// Package smartgroup 管理智能分组与自建分组。
//
// 智能分组只在用户手动触发时计算：按节点名称识别地区，把全部订阅与本地配置中的节点整理成地区分组。
// 自建分组由用户自己挑选节点组成。每个分组在运行时生成一个自动选择（URLTest）出站，成员按
// 「来源分组 + 节点名」引用，节点被订阅移除后只会减少成员，不会让核心启动失败。
// 地区分组的手动加入、移除与改名单独保存，重新智能分组时保留。
package smartgroup

import (
	"cmp"
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
	"time"
	"unicode"
	"unicode/utf8"

	"github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/processlock"
	"github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/provider"
	"github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/region"
	"github.com/gofrs/uuid/v5"
)

// 分组类型。
const (
	KindRegion = "region"
	KindCustom = "custom"
)

// TagPrefix 是分组在运行时出站图中的标签前缀；选择同步与客户端展示依赖这个名称。
const TagPrefix = "Smart/"

const (
	regionIDPrefix = "smart-"
	customIDPrefix = "custom-"
	otherRegion    = "other"
	documentFile   = "groups.json"
	lockFile       = "groups.lock"
	maxNameBytes   = 64
)

var idPattern = regexp.MustCompile(`^[a-z0-9][a-z0-9-]{0,79}$`)

// 常用地区排在前面，其余按节点数量排序；中国大陆与无法识别的节点排在最后。
var regionPriority = []string{"hk", "tw", "jp", "kr", "sg", "us", "gb", "de", "fr", "nl", "ca", "au", "mo", "my", "th", "vn", "ph", "in", "tr", "ru"}

// NodeRef 按来源分组与节点名引用一个节点。
type NodeRef struct {
	GroupID string `json:"group_id"`
	Node    string `json:"node"`
}

// Group 是一个智能分组或自建分组。
type Group struct {
	ID     string `json:"id"`
	Name   string `json:"name"`
	Kind   string `json:"kind"`
	Region string `json:"region,omitzero"`
	// Members 对地区分组是最近一次智能分组的结果，对自建分组是全部成员。
	Members []NodeRef `json:"members"`
	// Added 与 Removed 是地区分组相对智能分组结果的手动修改。
	Added   []NodeRef `json:"added,omitzero"`
	Removed []NodeRef `json:"removed,omitzero"`
}

// Document 是 groups.json 的持久结构。
type Document struct {
	Groups      []Group `json:"groups"`
	GeneratedAt string  `json:"generated_at,omitzero"`
}

// Source 是参与智能分组的一个节点分组及其节点名称。
type Source struct {
	GroupID string
	Tags    []string
}

// Summary 描述一次智能分组的结果。
type Summary struct {
	Regions       int `json:"regions"`
	Nodes         int `json:"nodes"`
	Unclassified  int `json:"unclassified"`
	Informational int `json:"informational"`
}

// InvalidError 表示输入或引用关系不合法，客户端应提示用户修改而不是重试。
type InvalidError struct{ Err error }

func (e *InvalidError) Error() string { return e.Err.Error() }
func (e *InvalidError) Unwrap() error { return e.Err }

// RuntimeTag 返回分组在运行时出站图中的标签。
func (g Group) RuntimeTag() string { return TagPrefix + g.Name }

// Effective 返回分组当前的成员：地区分组在智能分组结果上应用手动修改。
func (g Group) Effective() []NodeRef {
	if g.Kind != KindRegion {
		return unique(g.Members)
	}
	removed := refSet(g.Removed)
	result := make([]NodeRef, 0, len(g.Members)+len(g.Added))
	for _, ref := range g.Members {
		if _, skip := removed[ref]; !skip {
			result = append(result, ref)
		}
	}
	return unique(append(result, g.Added...))
}

// SetMembers 把分组成员设为 members。地区分组只记录相对智能分组结果的差异，便于重新分组时保留。
func (g *Group) SetMembers(members []NodeRef) {
	members = unique(members)
	if g.Kind != KindRegion {
		g.Members, g.Added, g.Removed = members, nil, nil
		return
	}
	base, want := refSet(g.Members), refSet(members)
	g.Added, g.Removed = nil, nil
	for _, ref := range members {
		if _, ok := base[ref]; !ok {
			g.Added = append(g.Added, ref)
		}
	}
	for _, ref := range g.Members {
		if _, ok := want[ref]; !ok {
			g.Removed = append(g.Removed, ref)
		}
	}
}

// Find 返回指定 id 的分组下标，不存在时返回 -1。
func (doc Document) Find(id string) int {
	return slices.IndexFunc(doc.Groups, func(group Group) bool { return group.ID == id })
}

// Clone 返回可以独立修改的副本。
func (doc Document) Clone() Document {
	clone := Document{GeneratedAt: doc.GeneratedAt, Groups: make([]Group, len(doc.Groups))}
	for index, group := range doc.Groups {
		group.Members = slices.Clone(group.Members)
		group.Added = slices.Clone(group.Added)
		group.Removed = slices.Clone(group.Removed)
		clone.Groups[index] = group
	}
	return clone
}

// NewCustomID 返回新自建分组的 id。
func NewCustomID() string { return customIDPrefix + uuid.Must(uuid.NewV4()).String() }

// Generate 重新计算地区分组：每个识别出的地区一个分组，无法识别的节点归入「其他」，提示性节点不参与。
// 已有地区分组的名称与手动修改保留；本次没有节点、但手动加入过节点的地区分组也保留；自建分组不受影响。
func Generate(doc Document, sources []Source, english bool, now time.Time) (Document, Summary) {
	var summary Summary
	buckets := make(map[string][]NodeRef)
	for _, source := range sources {
		for _, tag := range source.Tags {
			if region.Informational(tag) {
				summary.Informational++
				continue
			}
			key := otherRegion
			if detected, ok := region.Detect(tag); ok {
				key = detected.Key
			} else {
				summary.Unclassified++
			}
			buckets[key] = append(buckets[key], NodeRef{GroupID: source.GroupID, Node: tag})
			summary.Nodes++
		}
	}
	previous := make(map[string]Group)
	names := make(map[string]struct{})
	var custom []Group
	for _, group := range doc.Groups {
		if group.Kind == KindRegion {
			previous[group.ID] = group
		} else {
			custom = append(custom, group)
			names[group.Name] = struct{}{}
		}
	}
	next := Document{Groups: []Group{}, GeneratedAt: now.UTC().Format(time.RFC3339)}
	for _, key := range sortedRegions(buckets) {
		id := regionIDPrefix + key
		group, existed := previous[id]
		delete(previous, id)
		if !existed {
			group = Group{ID: id, Kind: KindRegion, Region: key, Name: uniqueName(regionLabel(key, english), names)}
		}
		group.Members = unique(buckets[key])
		names[group.Name] = struct{}{}
		next.Groups = append(next.Groups, group)
		summary.Regions++
	}
	kept := make([]Group, 0, len(previous))
	for _, group := range previous {
		if len(group.Added) > 0 {
			group.Members = []NodeRef{}
			kept = append(kept, group)
		}
	}
	slices.SortFunc(kept, func(left, right Group) int { return cmp.Compare(left.ID, right.ID) })
	next.Groups = append(append(next.Groups, kept...), custom...)
	return next, summary
}

// Validate 校验分组 id、名称、类型与成员引用。
func (doc Document) Validate() error {
	ids := make(map[string]struct{}, len(doc.Groups))
	names := make(map[string]struct{}, len(doc.Groups))
	for _, group := range doc.Groups {
		if !idPattern.MatchString(group.ID) {
			return fmt.Errorf("分组 id 无效: %q", group.ID)
		}
		switch group.Kind {
		case KindRegion:
			if !strings.HasPrefix(group.ID, regionIDPrefix) || group.Region == "" {
				return fmt.Errorf("地区分组 %q 缺少地区信息", group.Name)
			}
		case KindCustom:
			if !strings.HasPrefix(group.ID, customIDPrefix) {
				return fmt.Errorf("自建分组 id 无效: %q", group.ID)
			}
			if len(group.Members) == 0 {
				return fmt.Errorf("自建分组 %q 至少需要一个节点", group.Name)
			}
		default:
			return fmt.Errorf("分组 %q 的类型无效: %q", group.Name, group.Kind)
		}
		if _, exists := ids[group.ID]; exists {
			return fmt.Errorf("分组 id 重复: %s", group.ID)
		}
		ids[group.ID] = struct{}{}
		if err := ValidateName(group.Name); err != nil {
			return fmt.Errorf("分组名称无效: %w", err)
		}
		if _, exists := names[group.Name]; exists {
			return fmt.Errorf("分组名称重复: %s", group.Name)
		}
		names[group.Name] = struct{}{}
		for _, refs := range [][]NodeRef{group.Members, group.Added, group.Removed} {
			for _, ref := range refs {
				if strings.TrimSpace(ref.GroupID) == "" || ref.Node == "" || !utf8.ValidString(ref.Node) {
					return fmt.Errorf("分组 %q 包含无效节点引用", group.Name)
				}
			}
		}
	}
	return nil
}

// ValidateName 校验分组名称。
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

// Load 读取分组；文件不存在表示从未分组。
func Load(ctx context.Context, dir string) (Document, error) {
	if err := ctx.Err(); err != nil {
		return Document{}, err
	}
	content, err := os.ReadFile(filepath.Join(dir, documentFile))
	if errors.Is(err, os.ErrNotExist) {
		return Document{Groups: []Group{}}, nil
	}
	if err != nil {
		return Document{}, err
	}
	var doc Document
	if err := json.Unmarshal(content, &doc, json.RejectUnknownMembers(true)); err != nil {
		return Document{}, fmt.Errorf("智能分组数据无效: %w", err)
	}
	if doc.Groups == nil {
		doc.Groups = []Group{}
	}
	for index := range doc.Groups {
		if doc.Groups[index].Members == nil {
			doc.Groups[index].Members = []NodeRef{}
		}
	}
	if err := doc.Validate(); err != nil {
		return Document{}, fmt.Errorf("智能分组数据无效: %w", err)
	}
	return doc, nil
}

// Update 在文件锁内读取最新分组、修改、校验并原子写入，返回修改前后的内容。
func Update(ctx context.Context, dir string, mutate func(*Document) error) (before, after Document, err error) {
	if err := os.MkdirAll(dir, 0o700); err != nil {
		return Document{}, Document{}, err
	}
	lock, err := processlock.Acquire(ctx, filepath.Join(dir, lockFile))
	if err != nil {
		return Document{}, Document{}, err
	}
	defer func() { err = errors.Join(err, lock.Release()) }()
	before, err = Load(ctx, dir)
	if err != nil {
		return Document{}, Document{}, err
	}
	after = before.Clone()
	if err := mutate(&after); err != nil {
		return Document{}, Document{}, &InvalidError{Err: err}
	}
	if err := after.Validate(); err != nil {
		return Document{}, Document{}, &InvalidError{Err: err}
	}
	content, err := json.Marshal(after, json.Deterministic(true), jsontext.WithIndent("  "))
	if err != nil {
		return Document{}, Document{}, err
	}
	if err := provider.WriteAtomic(filepath.Join(dir, documentFile), append(content, '\n'), 0o600); err != nil {
		return Document{}, Document{}, err
	}
	return before, after, nil
}

// SameRuntime 判断两份分组生成的运行时出站是否相同（名称与有效成员）。
func SameRuntime(left, right Document) bool {
	return slices.EqualFunc(left.Groups, right.Groups, func(a, b Group) bool {
		return a.ID == b.ID && a.Name == b.Name && slices.Equal(a.Effective(), b.Effective())
	})
}

func regionLabel(key string, english bool) string {
	if key == otherRegion {
		if english {
			return "🌐 Others"
		}
		return "🌐 其他"
	}
	return region.Lookup(key).Label(english)
}

func sortedRegions(buckets map[string][]NodeRef) []string {
	keys := make([]string, 0, len(buckets))
	for key := range buckets {
		keys = append(keys, key)
	}
	rank := func(key string) int {
		switch key {
		case otherRegion:
			return 3000
		case "cn":
			return 2000
		}
		if index := slices.Index(regionPriority, key); index >= 0 {
			return index
		}
		return 1000
	}
	slices.SortFunc(keys, func(left, right string) int {
		return cmp.Or(
			cmp.Compare(rank(left), rank(right)),
			cmp.Compare(len(buckets[right]), len(buckets[left])),
			cmp.Compare(left, right),
		)
	})
	return keys
}

func uniqueName(base string, taken map[string]struct{}) string {
	name := base
	for suffix := 2; ; suffix++ {
		if _, exists := taken[name]; !exists {
			return name
		}
		name = fmt.Sprintf("%s %d", base, suffix)
	}
}

func unique(refs []NodeRef) []NodeRef {
	seen := make(map[NodeRef]struct{}, len(refs))
	result := make([]NodeRef, 0, len(refs))
	for _, ref := range refs {
		if _, exists := seen[ref]; !exists {
			seen[ref] = struct{}{}
			result = append(result, ref)
		}
	}
	return result
}

func refSet(refs []NodeRef) map[NodeRef]struct{} {
	set := make(map[NodeRef]struct{}, len(refs))
	for _, ref := range refs {
		set[ref] = struct{}{}
	}
	return set
}
