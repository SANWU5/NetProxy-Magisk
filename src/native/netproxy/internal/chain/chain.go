// Package chain 管理链式代理：后置代理列表、后置模式与按应用策略。
//
// 链路固定为「当前节点（前置）→ 后置代理 → 目标」。前置始终跟随用户在节点页的选择，
// 因此本包只保存后置相关的持久状态，并生成 sing-box 运行时片段。
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

// Policy 让一组应用经由指定后置代理出网。
type Policy struct {
	ID       string   `json:"id"`
	Name     string   `json:"name"`
	Enabled  bool     `json:"enabled"`
	Landing  string   `json:"landing"`
	Packages []string `json:"packages"`
}

// Settings 是 chain.json 的持久结构。
type Settings struct {
	Mode     string   `json:"mode"`
	Manual   string   `json:"manual"`
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
	if state.Mode != ModeOff && len(state.Landings.Outbounds) == 0 {
		return errors.New("开启链式代理前需要先导入至少一个后置代理")
	}
	ids := make(map[string]struct{}, len(state.Policies))
	owners := make(map[string]string)
	for _, policy := range state.Policies {
		if strings.TrimSpace(policy.ID) == "" {
			return errors.New("策略缺少 id")
		}
		if _, exists := ids[policy.ID]; exists {
			return fmt.Errorf("策略 id 重复: %s", policy.ID)
		}
		ids[policy.ID] = struct{}{}
		if err := ValidateName(policy.Name); err != nil {
			return fmt.Errorf("策略名称无效: %w", err)
		}
		if _, ok := tags[policy.Landing]; !ok {
			return fmt.Errorf("策略 %q 的后置代理 %q 不存在", policy.Name, policy.Landing)
		}
		seen := make(map[string]struct{}, len(policy.Packages))
		for _, name := range policy.Packages {
			if !packageNamePattern.MatchString(name) {
				return fmt.Errorf("策略 %q 包含无效包名 %q", policy.Name, name)
			}
			if _, exists := seen[name]; exists {
				return fmt.Errorf("策略 %q 重复包含应用 %s", policy.Name, name)
			}
			seen[name] = struct{}{}
			if !policy.Enabled {
				continue
			}
			// 同一应用只能命中一个后置，否则结果取决于规则顺序，用户无法从界面判断。
			if owner, exists := owners[name]; exists {
				return fmt.Errorf("应用 %s 同时出现在策略 %q 和 %q 中", name, owner, policy.Name)
			}
			owners[name] = policy.Name
		}
		if policy.Enabled && len(policy.Packages) == 0 {
			return fmt.Errorf("启用的策略 %q 至少需要选择一个应用", policy.Name)
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
	clone.Landings.Outbounds = slices.Clone(state.Landings.Outbounds)
	clone.Landings.Endpoints = slices.Clone(state.Landings.Endpoints)
	return clone
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
