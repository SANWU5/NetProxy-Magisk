// Package chain 管理链式代理：后置代理列表、后置模式、前置选择与按应用策略。
//
// 链路固定为「前置节点 → 后置代理 → 目标」。前置默认跟随用户在节点页的选择，也可以固定为
// 某个分组的自动选择或某个节点；本包保存这些持久状态，并生成 sing-box 运行时片段。
package chain

import (
	"context"
	"encoding/json/jsontext"
	json "encoding/json/v2"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"regexp"
	"slices"
	"strings"
	"unicode"
	"unicode/utf8"

	"github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/processlock"
	"github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/provider"
	"github.com/sagernet/sing-box/option"
)

// 运行时标签保持稳定：选择同步、cache_file 与客户端展示都依赖这些名称。
const (
	FrontTag         = "链式前置"
	SelectorTag      = "链式代理"
	LandingTagPrefix = "落地/"
)

// 后置模式。手动模式让顶层 Proxy 经由手动选择的后置出网；策略模式只让策略中的应用经由各自的后置出网。
const (
	ModeOff          = "off"
	ModeManual       = "manual"
	ModePolicy       = "policy"
	ModeManualPolicy = "manual_policy"
)

const (
	settingsFile = "chain.json"
	landingFile  = "landing.json"
	lockFile     = "chain.lock"
	maxNameBytes = 64
)

var packageNamePattern = regexp.MustCompile(`^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$`)

// 策略组的应用匹配方式：白名单只匹配勾选的应用，黑名单匹配勾选应用以外的全部应用。
const (
	MatchWhitelist = "whitelist"
	MatchBlacklist = "blacklist"
)

// 策略组的出口：经由后置代理、只经过前置节点，或直连。
const (
	ExitLanding = "landing"
	ExitFront   = "front"
	ExitDirect  = "direct"
)

// Policy 是一个策略组：匹配到的应用流量经由组的出口出网。多个组按列表顺序匹配，先命中的组生效。
type Policy struct {
	ID      string `json:"id"`
	Name    string `json:"name"`
	Enabled bool   `json:"enabled"`
	// Match 为空表示旧版策略，按白名单处理。
	Match string `json:"match,omitzero"`
	// Exit 为空表示旧版策略，经由 Landing 出网。
	Exit string `json:"exit,omitzero"`
	// Landing 只在出口为后置代理时使用。
	Landing string `json:"landing"`
	// DomesticDirect 让中国大陆网站直连而不经过组的出口；旧版策略没有这个字段，保持全部经由出口。
	DomesticDirect bool     `json:"domestic_direct,omitzero"`
	Packages       []string `json:"packages"`
}

// MatchMode 返回匹配方式；旧版策略没有这个字段，按白名单处理。
func (p Policy) MatchMode() string {
	if p.Match == "" {
		return MatchWhitelist
	}
	return p.Match
}

// ExitKind 返回出口；旧版策略没有这个字段，经由后置代理。
func (p Policy) ExitKind() string {
	if p.Exit == "" {
		return ExitLanding
	}
	return p.Exit
}

// Normalized 返回填好默认匹配方式与出口的副本，供客户端展示。
func (p Policy) Normalized() Policy {
	p.Match, p.Exit = p.MatchMode(), p.ExitKind()
	p.Packages = slices.Clone(p.Packages)
	return p
}

// exitTag 返回组出口在运行时出站图中的标签。
func (p Policy) exitTag() string {
	switch p.ExitKind() {
	case ExitFront:
		return FrontTag
	case ExitDirect:
		return "direct"
	default:
		return LandingTag(p.Landing)
	}
}

// Front 固定链式前置：SmartID 指定智能分组或自建分组的自动选择；否则 GroupID 指定节点分组，
// Node 为空时使用该分组的自动选择（延迟最低的节点），不为空时固定为这个节点。
type Front struct {
	GroupID string `json:"group_id,omitzero"`
	Node    string `json:"node,omitzero"`
	SmartID string `json:"smart_id,omitzero"`
}

// Settings 是 chain.json 的持久结构。
type Settings struct {
	Mode   string `json:"mode"`
	Manual string `json:"manual"`
	// Front 为空时前置跟随节点页的选择。
	Front    *Front   `json:"front,omitzero"`
	Policies []Policy `json:"policies"`
}

// State 合并后置模式设置与后置代理列表。
type State struct {
	Settings
	Landings provider.Document
}

// DefaultSettings 返回从未配置过链式代理时的设置。
func DefaultSettings() Settings {
	return Settings{Mode: ModeOff, Policies: []Policy{}}
}

// LandingTag 返回后置代理在运行时出站图中的标签。
func LandingTag(tag string) string {
	return LandingTagPrefix + tag
}

// UsesManual 判断模式是否让顶层 Proxy 经由手动后置出网。
func UsesManual(mode string) bool {
	return mode == ModeManual || mode == ModeManualPolicy
}

// UsesPolicies 判断模式是否生成按应用策略路由。
func UsesPolicies(mode string) bool {
	return mode == ModePolicy || mode == ModeManualPolicy
}

func settingsPath(dir string) string { return filepath.Join(dir, settingsFile) }
func landingPath(dir string) string  { return filepath.Join(dir, landingFile) }

// LandingPath 返回后置代理 Provider 文档路径。
func LandingPath(dir string) string { return landingPath(dir) }

// Load 读取链式代理状态；两个文件都不存在时表示从未配置。
func Load(ctx context.Context, dir string) (State, error) {
	state := State{Settings: DefaultSettings()}
	content, err := os.ReadFile(settingsPath(dir))
	switch {
	case err == nil:
		state.Settings, err = parseSettings(content)
		if err != nil {
			return State{}, err
		}
	case !errors.Is(err, os.ErrNotExist):
		return State{}, err
	}
	if _, err := os.Stat(landingPath(dir)); err == nil {
		state.Landings, err = provider.LoadAllowEmpty(ctx, landingPath(dir))
		if err != nil {
			return State{}, fmt.Errorf("读取后置代理列表失败: %w", err)
		}
	} else if !errors.Is(err, os.ErrNotExist) {
		return State{}, err
	}
	if err := state.Validate(); err != nil {
		return State{}, err
	}
	return state, nil
}

func parseSettings(content []byte) (Settings, error) {
	// null 会把字段静默置零，链式设置不接受这种隐式重置。
	decoder := jsontext.NewDecoder(strings.NewReader(string(content)))
	for {
		token, err := decoder.ReadToken()
		if err != nil {
			break
		}
		if token.Kind() == 'n' {
			return Settings{}, errors.New("链式代理配置字段不能为 null")
		}
	}
	settings := DefaultSettings()
	if err := json.Unmarshal(content, &settings, json.RejectUnknownMembers(true)); err != nil {
		return Settings{}, fmt.Errorf("链式代理配置无效: %w", err)
	}
	if settings.Policies == nil {
		settings.Policies = []Policy{}
	}
	return settings, nil
}

// Validate 校验设置、后置列表以及两者之间的引用。
func (state State) Validate() error {
	switch state.Mode {
	case ModeOff, ModeManual, ModePolicy, ModeManualPolicy:
	default:
		return fmt.Errorf("后置模式只能是 off、manual、policy 或 manual_policy，当前为 %q", state.Mode)
	}
	if len(state.Landings.Endpoints) > 0 {
		return errors.New("后置代理不支持 WireGuard/Tailscale 等 Endpoint 类型")
	}
	tags := make(map[string]struct{}, len(state.Landings.Outbounds))
	for _, outbound := range state.Landings.Outbounds {
		if _, ok := outbound.Options.(option.DialerOptionsWrapper); !ok {
			return fmt.Errorf("后置代理 %q 的类型 %s 不支持经由前置节点连接", outbound.Tag, outbound.Type)
		}
		tags[outbound.Tag] = struct{}{}
	}
	if len(state.Landings.Outbounds) > 0 {
		if err := provider.Validate(state.Landings); err != nil {
			return fmt.Errorf("后置代理列表无效: %w", err)
		}
	}
	if state.Manual != "" {
		if _, ok := tags[state.Manual]; !ok {
			return fmt.Errorf("手动后置 %q 不存在", state.Manual)
		}
	}
	if UsesManual(state.Mode) && state.Manual == "" {
		return errors.New("手动模式需要先选择一个后置代理")
	}
	if front := state.Front; front != nil {
		switch {
		case front.SmartID != "":
			if front.GroupID != "" || front.Node != "" {
				return errors.New("指定的前置只能是智能分组或节点分组之一")
			}
		case strings.TrimSpace(front.GroupID) == "":
			return errors.New("指定的前置缺少分组")
		case !utf8.ValidString(front.Node) || strings.ContainsFunc(front.Node, unicode.IsControl):
			return errors.New("指定的前置节点名称无效")
		}
	}
	if state.Mode != ModeOff && len(state.Landings.Outbounds) == 0 {
		return errors.New("开启链式代理前需要先导入至少一个后置代理")
	}
	ids := make(map[string]struct{}, len(state.Policies))
	for _, policy := range state.Policies {
		if strings.TrimSpace(policy.ID) == "" {
			return errors.New("策略组缺少 id")
		}
		if _, exists := ids[policy.ID]; exists {
			return fmt.Errorf("策略组 id 重复: %s", policy.ID)
		}
		ids[policy.ID] = struct{}{}
		if err := ValidateName(policy.Name); err != nil {
			return fmt.Errorf("策略组名称无效: %w", err)
		}
		switch policy.MatchMode() {
		case MatchWhitelist, MatchBlacklist:
		default:
			return fmt.Errorf("策略组 %q 的匹配方式只能是 whitelist 或 blacklist", policy.Name)
		}
		switch policy.ExitKind() {
		case ExitLanding:
			if _, ok := tags[policy.Landing]; !ok {
				return fmt.Errorf("策略组 %q 的后置代理 %q 不存在", policy.Name, policy.Landing)
			}
		case ExitFront, ExitDirect:
			if policy.Landing != "" {
				return fmt.Errorf("策略组 %q 不经由后置代理时不能指定后置", policy.Name)
			}
		default:
			return fmt.Errorf("策略组 %q 的出口只能是 landing、front 或 direct", policy.Name)
		}
		// 不同组可以包含同一应用：按列表顺序匹配，先命中的组生效，客户端据此显示应用实际所在的组。
		seen := make(map[string]struct{}, len(policy.Packages))
		for _, name := range policy.Packages {
			if !packageNamePattern.MatchString(name) {
				return fmt.Errorf("策略组 %q 包含无效包名 %q", policy.Name, name)
			}
			if _, exists := seen[name]; exists {
				return fmt.Errorf("策略组 %q 重复包含应用 %s", policy.Name, name)
			}
			seen[name] = struct{}{}
		}
		// 黑名单组不勾选应用表示匹配全部应用；白名单组没有应用就什么也匹配不到。
		if policy.Enabled && policy.MatchMode() == MatchWhitelist && len(policy.Packages) == 0 {
			return fmt.Errorf("启用的白名单策略组 %q 至少需要选择一个应用", policy.Name)
		}
	}
	return nil
}

// ValidateName 校验后置代理和策略的显示名称。
func ValidateName(name string) error {
	trimmed := strings.TrimSpace(name)
	if trimmed == "" {
		return errors.New("名称不能为空")
	}
	if trimmed != name {
		return errors.New("名称首尾不能有空白")
	}
	if len(name) > maxNameBytes || !utf8.ValidString(name) || strings.ContainsFunc(name, unicode.IsControl) {
		return fmt.Errorf("名称必须是不超过 %d 字节、不含控制字符的 UTF-8 文本", maxNameBytes)
	}
	return nil
}

// Clone 返回可以独立修改的副本。
func (state State) Clone() State {
	clone := state
	clone.Policies = make([]Policy, len(state.Policies))
	for index, policy := range state.Policies {
		policy.Packages = slices.Clone(policy.Packages)
		clone.Policies[index] = policy
	}
	if state.Front != nil {
		front := *state.Front
		clone.Front = &front
	}
	clone.Landings.Outbounds = slices.Clone(state.Landings.Outbounds)
	clone.Landings.Endpoints = slices.Clone(state.Landings.Endpoints)
	return clone
}

// SameFront 判断两个前置设置是否相同；都为空表示都跟随节点页。
func SameFront(left, right *Front) bool {
	if left == nil || right == nil {
		return left == right
	}
	return *left == *right
}

// FindPolicy 返回指定 id 的策略下标。
func (state State) FindPolicy(id string) int {
	return slices.IndexFunc(state.Policies, func(policy Policy) bool { return policy.ID == id })
}

// FindLanding 返回指定标签的后置代理下标。
func (state State) FindLanding(tag string) int {
	return slices.IndexFunc(state.Landings.Outbounds, func(outbound option.Outbound) bool { return outbound.Tag == tag })
}

// InvalidError 表示输入或引用关系不合法，客户端应提示用户修改而不是重试。
type InvalidError struct{ Err error }

func (e *InvalidError) Error() string { return e.Err.Error() }
func (e *InvalidError) Unwrap() error { return e.Err }

// Snapshot 保存写入前的原始文件，供运行时应用失败时恢复。
type Snapshot struct {
	dir     string
	present map[string][]byte
}

func takeSnapshot(dir string) (Snapshot, error) {
	snapshot := Snapshot{dir: dir, present: make(map[string][]byte, 2)}
	for _, name := range []string{settingsFile, landingFile} {
		content, err := os.ReadFile(filepath.Join(dir, name))
		if err == nil {
			snapshot.present[name] = content
		} else if !errors.Is(err, os.ErrNotExist) {
			return Snapshot{}, err
		}
	}
	return snapshot, nil
}

// Restore 把文件恢复到快照时的内容；快照时不存在的文件会被删除。
func (snapshot Snapshot) Restore() error {
	var result error
	for _, name := range []string{settingsFile, landingFile} {
		path := filepath.Join(snapshot.dir, name)
		if content, ok := snapshot.present[name]; ok {
			result = errors.Join(result, provider.WriteAtomic(path, content, 0o600))
		} else if err := os.Remove(path); err != nil && !errors.Is(err, os.ErrNotExist) {
			result = errors.Join(result, err)
		}
	}
	return result
}

// Update 在链式代理文件锁内读取最新状态、修改、校验并原子写入。
// 返回修改前后的状态与写入前快照，调用方据此决定是否重载以及失败时恢复。
func Update(ctx context.Context, dir string, mutate func(*State) error) (before, after State, snapshot Snapshot, err error) {
	lock, err := processlock.Acquire(ctx, filepath.Join(dir, lockFile))
	if err != nil {
		return State{}, State{}, Snapshot{}, err
	}
	defer func() { err = errors.Join(err, lock.Release()) }()
	before, err = Load(ctx, dir)
	if err != nil {
		return State{}, State{}, Snapshot{}, err
	}
	after = before.Clone()
	if err := mutate(&after); err != nil {
		return State{}, State{}, Snapshot{}, &InvalidError{Err: err}
	}
	if err := after.Validate(); err != nil {
		return State{}, State{}, Snapshot{}, &InvalidError{Err: err}
	}
	snapshot, err = takeSnapshot(dir)
	if err != nil {
		return State{}, State{}, Snapshot{}, err
	}
	if err := save(ctx, dir, after); err != nil {
		return State{}, State{}, Snapshot{}, errors.Join(err, snapshot.Restore())
	}
	return before, after, snapshot, nil
}

func save(ctx context.Context, dir string, state State) error {
	if err := os.MkdirAll(dir, 0o700); err != nil {
		return err
	}
	settings := state.Settings
	if settings.Policies == nil {
		settings.Policies = []Policy{}
	}
	content, err := json.Marshal(settings, json.Deterministic(true), jsontext.WithIndent("  "))
	if err != nil {
		return err
	}
	if err := provider.SaveAtomicAllowEmpty(ctx, landingPath(dir), state.Landings); err != nil {
		return err
	}
	return provider.WriteAtomic(settingsPath(dir), append(content, '\n'), 0o600)
}

// ReorderPolicies 按 ids 的顺序重排策略组；ids 必须恰好包含现有的全部组。组的顺序决定匹配优先级。
func (state *State) ReorderPolicies(ids []string) error {
	if len(ids) != len(state.Policies) {
		return &InvalidError{Err: errors.New("策略组顺序必须包含全部策略组")}
	}
	ordered := make([]Policy, 0, len(ids))
	used := make(map[string]bool, len(ids))
	for _, id := range ids {
		index := state.FindPolicy(id)
		if index < 0 || used[id] {
			return &InvalidError{Err: fmt.Errorf("策略组顺序无效: %s", id)}
		}
		used[id] = true
		ordered = append(ordered, state.Policies[index])
	}
	state.Policies = ordered
	return nil
}
