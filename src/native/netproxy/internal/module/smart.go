package module

import (
	"context"
	"errors"
	"fmt"
	"slices"
	"strings"
	"time"

	"github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/catalog"
	"github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/chain"
	moduleconfig "github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/config"
	"github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/service"
	"github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/serviceapi"
	"github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/smartgroup"
)

// ErrSmartGroupNotFound 表示请求的智能分组或自建分组不存在。
var ErrSmartGroupNotFound = errors.New("分组不存在")

// SmartGroupView 是智能分组页面使用的单个分组快照。
type SmartGroupView struct {
	ID      string               `json:"id"`
	Name    string               `json:"name"`
	Kind    string               `json:"kind"`
	Region  string               `json:"region,omitzero"`
	Members []smartgroup.NodeRef `json:"members"`
	// Available 是成员中仍然存在的节点数；为 0 时运行时不会生成这个分组。
	Available int `json:"available"`
	// Edited 表示地区分组有手动加入或移除的节点，重新智能分组时会保留这些修改。
	Edited bool `json:"edited,omitzero"`
	// Selected 是自动选择当前使用的节点（运行时出站标签），Delay 是它最近一次测速的延迟。
	Selected string `json:"selected,omitzero"`
	Delay    int32  `json:"delay,omitzero"`
}

// SmartGroupsView 是智能分组页面的完整快照。
type SmartGroupsView struct {
	Groups      []SmartGroupView `json:"groups"`
	GeneratedAt string           `json:"generated_at,omitzero"`
	// Active 是节点页当前使用的智能分组 id；为空表示正在使用普通分组。
	Active  string `json:"active,omitzero"`
	Running bool   `json:"running"`
}

func smartRuntime(document smartgroup.Document) []catalog.SmartGroup {
	groups := make([]catalog.SmartGroup, 0, len(document.Groups))
	for _, group := range document.Groups {
		definition := catalog.SmartGroup{ID: group.ID, Tag: group.RuntimeTag()}
		for _, member := range group.Effective() {
			definition.Members = append(definition.Members, catalog.SmartMember{GroupID: member.GroupID, Node: member.Node})
		}
		groups = append(groups, definition)
	}
	return groups
}

// catalogNodes 返回全部节点分组的节点，作为智能分组的来源与成员可用性依据。
func catalogNodes(ctx context.Context, options Options) ([]smartgroup.Source, map[smartgroup.NodeRef]struct{}, error) {
	groups, err := catalog.Scan(ctx, catalog.ScanOptions{Root: options.CatalogRoot, WithNodes: true})
	if err != nil {
		return nil, nil, err
	}
	sources := make([]smartgroup.Source, 0, len(groups))
	present := make(map[smartgroup.NodeRef]struct{})
	for _, group := range groups {
		source := smartgroup.Source{GroupID: group.Group.ID}
		for _, node := range group.Nodes {
			source.Tags = append(source.Tags, node.Tag)
			present[smartgroup.NodeRef{GroupID: group.Group.ID, Node: node.Tag}] = struct{}{}
		}
		sources = append(sources, source)
	}
	return sources, present, nil
}

// ReadSmartGroups 返回全部智能分组与自建分组；服务运行时合并自动选择的结果。
func ReadSmartGroups(ctx context.Context, options Options) (SmartGroupsView, error) {
	document, err := smartgroup.Load(ctx, options.smartDir())
	if err != nil {
		return SmartGroupsView{}, err
	}
	return smartView(ctx, options, document)
}

func smartView(ctx context.Context, options Options, document smartgroup.Document) (SmartGroupsView, error) {
	_, present, err := catalogNodes(ctx, options)
	if err != nil {
		return SmartGroupsView{}, err
	}
	module, err := moduleconfig.LoadModule(options.ModuleConfig)
	if err != nil {
		return SmartGroupsView{}, err
	}
	view := SmartGroupsView{Groups: make([]SmartGroupView, 0, len(document.Groups)), GeneratedAt: document.GeneratedAt}
	if document.Find(module.SmartGroupID) >= 0 {
		view.Active = module.SmartGroupID
	}
	for _, group := range document.Groups {
		members := group.Effective()
		available := 0
		for _, member := range members {
			if _, ok := present[member]; ok {
				available++
			}
		}
		view.Groups = append(view.Groups, SmartGroupView{
			ID: group.ID, Name: group.Name, Kind: group.Kind, Region: group.Region, Members: members,
			Available: available, Edited: len(group.Added)+len(group.Removed) > 0,
		})
	}
	if !service.ProcessRunning(options.SingBoxPath) {
		return view, nil
	}
	view.Running = true
	client, err := serviceapi.New(options.ServiceAddress, options.ServiceSecret)
	if err != nil {
		return view, nil
	}
	defer client.Close()
	requestContext, cancel := context.WithTimeout(ctx, minTimeout(options.RequestTimeout, 3*time.Second))
	defer cancel()
	groups, err := client.Groups(requestContext)
	if err != nil {
		// 运行时信息只用于展示自动选择的结果，读取失败时仍返回持久分组。
		return view, nil
	}
	runtime := make(map[string]serviceapi.Group, len(groups))
	for _, group := range groups {
		runtime[group.Tag] = group
	}
	for index := range view.Groups {
		group, ok := runtime[smartgroup.TagPrefix+view.Groups[index].Name]
		if !ok {
			continue
		}
		view.Groups[index].Selected = group.Selected
		for _, item := range group.Items {
			if item.Tag == group.Selected && item.URLTestTime > 0 {
				view.Groups[index].Delay = item.URLTestDelay
			}
		}
	}
	return view, nil
}

// GenerateSmartGroups 手动触发智能分组：按地区重新整理全部节点，保留手动修改、改名与自建分组。
func GenerateSmartGroups(ctx context.Context, options Options, english bool) (SmartGroupsView, smartgroup.Summary, error) {
	sources, _, err := catalogNodes(ctx, options)
	if err != nil {
		return SmartGroupsView{}, smartgroup.Summary{}, err
	}
	var summary smartgroup.Summary
	view, err := updateSmart(ctx, options, "smart.generate", "智能分组", func(document *smartgroup.Document) error {
		*document, summary = smartgroup.Generate(*document, sources, english, time.Now())
		return nil
	})
	return view, summary, err
}

// SaveSmartGroup 创建自建分组（id 为空）或修改分组名称与成员，返回分组 id。
func SaveSmartGroup(ctx context.Context, options Options, id, name string, members []smartgroup.NodeRef) (SmartGroupsView, string, error) {
	name = strings.TrimSpace(name)
	view, err := updateSmart(ctx, options, "smart.save", "保存智能分组", func(document *smartgroup.Document) error {
		if id == "" {
			id = smartgroup.NewCustomID()
			group := smartgroup.Group{ID: id, Name: name, Kind: smartgroup.KindCustom}
			group.SetMembers(members)
			document.Groups = append(document.Groups, group)
			return nil
		}
		index := document.Find(id)
		if index < 0 {
			return ErrSmartGroupNotFound
		}
		document.Groups[index].Name = name
		document.Groups[index].SetMembers(members)
		return nil
	})
	return view, id, err
}

// DeleteSmartGroup 删除分组；节点页或链式前置正在使用它时改回普通分组与跟随节点页。
func DeleteSmartGroup(ctx context.Context, options Options, id string) (SmartGroupsView, error) {
	return updateSmart(ctx, options, "smart.delete", "删除智能分组", func(document *smartgroup.Document) error {
		index := document.Find(id)
		if index < 0 {
			return ErrSmartGroupNotFound
		}
		document.Groups = slices.Delete(document.Groups, index, index+1)
		return nil
	})
}

// updateSmart 串行修改分组；服务运行时重载核心使新的自动选择出站生效，重载失败恢复修改前的分组。
func updateSmart(ctx context.Context, options Options, code, message string, mutate func(*smartgroup.Document) error) (view SmartGroupsView, err error) {
	persisted := false
	defer func() { logOperation(options, "smart", code, message, persisted, err) }()
	if err := options.validate(); err != nil {
		return SmartGroupsView{}, err
	}
	lock, err := waitLifecycleLock(ctx, options.StateFile)
	if err != nil {
		return SmartGroupsView{}, err
	}
	defer lock.release()
	if err := recoverConfigApply(ctx, options); err != nil {
		return SmartGroupsView{}, err
	}
	before, after, err := smartgroup.Update(ctx, options.smartDir(), mutate)
	if err != nil {
		return SmartGroupsView{}, err
	}
	persisted = true
	if err := clearSmartReferences(ctx, options, after); err != nil {
		return SmartGroupsView{}, err
	}
	if service.ProcessRunning(options.SingBoxPath) && !smartgroup.SameRuntime(before, after) {
		if err := ReloadService(ctx, options); err != nil {
			persisted = false
			_, _, restoreErr := smartgroup.Update(ctx, options.smartDir(), func(document *smartgroup.Document) error {
				*document = before
				return nil
			})
			return SmartGroupsView{}, errors.Join(&service.Error{
				Code: "smart.apply_failed", Message: fmt.Sprintf("分组未能生效，已恢复修改前的分组: %v", err),
				Data: map[string]any{"persisted": false, "cause": err.Error()},
			}, restoreErr)
		}
	}
	return smartView(ctx, options, after)
}

// clearSmartReferences 把引用已删除分组的节点页选择与链式前置改回普通选择。
func clearSmartReferences(ctx context.Context, options Options, document smartgroup.Document) error {
	module, err := moduleconfig.LoadModule(options.ModuleConfig)
	if err != nil {
		return err
	}
	if module.SmartGroupID != "" && document.Find(module.SmartGroupID) < 0 {
		selection := module.Selection
		selection.SmartGroupID = ""
		if err := options.updateSelection(ctx, selection); err != nil {
			return err
		}
	}
	state, err := chain.Load(ctx, options.chainDir())
	if err != nil {
		return err
	}
	if state.Front == nil || state.Front.SmartID == "" || document.Find(state.Front.SmartID) >= 0 {
		return nil
	}
	_, _, _, err = chain.Update(ctx, options.chainDir(), func(state *chain.State) error {
		state.Front = nil
		return nil
	})
	return err
}

// SelectSmartGroup 让节点页使用智能分组的自动选择，并在服务运行时同步选择器。
func SelectSmartGroup(ctx context.Context, options Options, id string) (data map[string]string, err error) {
	persisted := false
	defer func() { logOperation(options, "node", "node.select", "节点选择", persisted, err) }()
	if err := options.validate(); err != nil {
		return nil, err
	}
	lock, err := waitLifecycleLock(ctx, options.StateFile)
	if err != nil {
		return nil, err
	}
	defer lock.release()
	if err := recoverConfigApply(ctx, options); err != nil {
		return nil, err
	}
	options, release, err := lockConfigFiles(ctx, options, options.ModuleConfig)
	if err != nil {
		return nil, err
	}
	defer release()
	module, err := moduleconfig.LoadModule(options.ModuleConfig)
	if err != nil {
		return nil, err
	}
	document, err := smartgroup.Load(ctx, options.smartDir())
	if err != nil {
		return nil, err
	}
	index := document.Find(id)
	if index < 0 {
		return nil, ErrSmartGroupNotFound
	}
	group := document.Groups[index]
	_, present, err := catalogNodes(ctx, options)
	if err != nil {
		return nil, err
	}
	if !slices.ContainsFunc(group.Effective(), func(ref smartgroup.NodeRef) bool { _, ok := present[ref]; return ok }) {
		return nil, &service.Error{Code: "smart.empty", Message: "这个分组目前没有可用节点，请编辑分组或重新智能分组"}
	}
	selection := module.Selection
	selection.SmartGroupID = id
	if selection != module.Selection {
		if err := options.updateSelection(ctx, selection); err != nil {
			return nil, err
		}
	}
	persisted = true
	data = map[string]string{"smart_group_id": id, "mode": selection.Mode(), "selected": group.RuntimeTag()}
	if !service.ProcessRunning(options.SingBoxPath) {
		return data, nil
	}
	_, runtimeTag, err := catalog.ResolveSelection(ctx, options.CatalogRoot, selection)
	if err == nil {
		active, inner := selection.RuntimeTargets(runtimeTag)
		err = syncRuntimeSelector(ctx, options, selection, active, inner)
	}
	if err != nil {
		return data, &service.Error{Code: "node.runtime_sync_failed", Message: fmt.Sprintf("选择已保存，但运行时切换失败: %v", err),
			Data: map[string]any{"persisted": true, "runtime_synced": false, "cause": err.Error()}}
	}
	return data, nil
}

// TestSmartGroups 让分组的自动选择重新测速；id 为空时测试全部分组。
func TestSmartGroups(ctx context.Context, options Options, id string) (SmartGroupsView, error) {
	document, err := smartgroup.Load(ctx, options.smartDir())
	if err != nil {
		return SmartGroupsView{}, err
	}
	if id != "" && document.Find(id) < 0 {
		return SmartGroupsView{}, ErrSmartGroupNotFound
	}
	if !service.ProcessRunning(options.SingBoxPath) {
		return SmartGroupsView{}, &service.Error{Code: "smart.service_stopped", Message: "服务未运行，无法测速"}
	}
	client, err := serviceapi.New(options.ServiceAddress, options.ServiceSecret)
	if err != nil {
		return SmartGroupsView{}, err
	}
	defer client.Close()
	testContext, cancel := context.WithTimeout(ctx, 20*time.Second)
	defer cancel()
	groups, err := client.Groups(testContext)
	if err != nil {
		return SmartGroupsView{}, err
	}
	members := make(map[string][]string)
	for _, group := range groups {
		if strings.HasPrefix(group.Tag, smartgroup.TagPrefix) {
			for _, item := range group.Items {
				members[group.Tag] = append(members[group.Tag], item.Tag)
			}
		}
	}
	before, err := urlTestTimes(testContext, client)
	if err != nil {
		return SmartGroupsView{}, err
	}
	var tested [][]string
	for _, group := range document.Groups {
		tag := group.RuntimeTag()
		if (id != "" && group.ID != id) || len(members[tag]) == 0 {
			continue
		}
		if err := client.URLTest(testContext, tag); err != nil {
			return SmartGroupsView{}, err
		}
		tested = append(tested, members[tag])
	}
	// 自动选择会测试全部成员；每个分组至少有一个成员出结果后，自动选择就已经更新。
	poll := time.NewTicker(400 * time.Millisecond)
	defer poll.Stop()
	deadline := time.NewTimer(10 * time.Second)
	defer deadline.Stop()
	for waiting := len(tested) > 0; waiting; {
		select {
		case <-testContext.Done():
			waiting = false
		case <-deadline.C:
			waiting = false
		case <-poll.C:
			current, err := urlTestTimes(testContext, client)
			if err != nil {
				continue
			}
			waiting = slices.ContainsFunc(tested, func(tags []string) bool {
				return !slices.ContainsFunc(tags, func(tag string) bool { return current[tag] > before[tag] })
			})
		}
	}
	return smartView(ctx, options, document)
}
