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
	"github.com/sagernet/sing-box/option"
)

// ChainView 是链式代理页面使用的完整快照。
type ChainView struct {
	Mode   string `json:"mode"`
	Manual string `json:"manual"`
	// FrontSetting 是指定的前置；为空时前置跟随节点页的选择。
	FrontSetting *chain.Front `json:"front_setting,omitzero"`
	// FrontUnavailable 表示指定的前置已不存在或没有可用节点，运行时暂时跟随节点页的选择。
	FrontUnavailable bool                   `json:"front_unavailable,omitzero"`
	Landings         []chain.LandingSummary `json:"landings"`
	Policies         []chain.Policy         `json:"policies"`
	Running          bool                   `json:"running"`
	// Front 是前置实际使用的节点（运行时解析），Landing 是「链式代理」选择器当前的后置。
	Front   string `json:"front"`
	Landing string `json:"landing"`
	// Delays 是后置经由前置的测速结果，-1 表示最近一次测试失败。
	Delays map[string]int32 `json:"delays"`
	// FrontDelay 是本次链路测速中前置节点自身的延迟，-1 表示失败；未测试时省略。
	FrontDelay *int32 `json:"front_delay,omitzero"`
}

// selectionTargets 描述一次选择同步需要设置的全部选择器。
type selectionTargets struct {
	// main 是节点页的主选择：智能分组的自动选择，或普通分组的选择器；fallback 是普通分组的选择器。
	main     string
	fallback string
	// chain 表示已导入后置代理，运行时存在链式前置与链式代理两个选择器。
	chain   bool
	front   string
	landing string
	proxy   string
}

func loadSelectionTargets(ctx context.Context, options Options, selection moduleconfig.Selection, active string) (selectionTargets, error) {
	targets := selectionTargets{main: active, fallback: active}
	if selection.SmartGroupID != "" {
		document, err := smartgroup.Load(ctx, options.smartDir())
		if err != nil {
			return selectionTargets{}, err
		}
		if index := document.Find(selection.SmartGroupID); index >= 0 {
			targets.main = document.Groups[index].RuntimeTag()
		}
	}
	targets.front, targets.proxy = targets.main, targets.main
	state, err := chain.Load(ctx, options.chainDir())
	if err != nil {
		return selectionTargets{}, err
	}
	if len(state.Landings.Outbounds) == 0 {
		return targets, nil
	}
	targets.chain = true
	if state.Front != nil {
		// 指定的前置已被删除时退回跟随节点页的选择，链式代理页会提示前置不可用。
		if front, err := resolveFrontTarget(ctx, options, *state.Front); err == nil {
			targets.front = front
		}
	}
	if state.Manual != "" {
		targets.landing = chain.LandingTag(state.Manual)
	}
	if chain.UsesManual(state.Mode) {
		targets.proxy = chain.SelectorTag
	}
	return targets, nil
}

// applyRuntimeSelection 依次设置分组内节点、链式前置、链式代理与顶层 Proxy。
// 智能分组或指定前置在运行时不存在（没有可用节点）时，依次回退到节点页的主选择与普通分组。
func applyRuntimeSelection(ctx context.Context, client *serviceapi.Client, active, inner string, targets selectionTargets) error {
	if inner != "" {
		if err := client.Select(ctx, active, inner); err != nil {
			return err
		}
	}
	if targets.chain {
		if err := selectFirst(ctx, client, chain.FrontTag, targets.front, targets.main, targets.fallback); err != nil {
			return err
		}
		if targets.landing != "" {
			if err := client.Select(ctx, chain.SelectorTag, targets.landing); err != nil {
				return err
			}
		}
	}
	if targets.proxy == chain.SelectorTag {
		return client.Select(ctx, "Proxy", chain.SelectorTag)
	}
	return selectFirst(ctx, client, "Proxy", targets.proxy, targets.fallback)
}

// selectFirst 依次尝试候选项，直到选择器接受其中一个；可重试的错误直接返回，由调用方重试整次同步。
func selectFirst(ctx context.Context, client *serviceapi.Client, group string, candidates ...string) error {
	var err error
	for _, candidate := range slices.Compact(candidates) {
		if err = client.Select(ctx, group, candidate); err == nil || serviceapi.IsRetryable(err) {
			return err
		}
	}
	return err
}

// resolveFrontTarget 返回指定前置在「链式前置」选择器中的选项：智能分组或节点分组的自动选择，或某个节点。
func resolveFrontTarget(ctx context.Context, options Options, front chain.Front) (string, error) {
	if front.SmartID != "" {
		document, err := smartgroup.Load(ctx, options.smartDir())
		if err != nil {
			return "", err
		}
		index := document.Find(front.SmartID)
		if index < 0 {
			return "", fmt.Errorf("分组 %q 不存在", front.SmartID)
		}
		return document.Groups[index].RuntimeTag(), nil
	}
	selection, runtimeTag, err := catalog.ResolveSelection(ctx, options.CatalogRoot,
		moduleconfig.Selection{ActiveGroupID: front.GroupID, SelectedNodeTag: front.Node})
	if err != nil {
		return "", err
	}
	group, node := selection.RuntimeTargets(runtimeTag)
	if node != "" {
		return node, nil
	}
	return group, nil
}

// CheckChainFront 校验指定前置引用的分组或节点当前存在。
func CheckChainFront(ctx context.Context, options Options, front chain.Front) error {
	_, err := resolveFrontTarget(ctx, options, front)
	return err
}

// ReadChain 返回链式代理设置；服务运行时合并前置、后置的实际选择与测速结果。
func ReadChain(ctx context.Context, options Options) (ChainView, error) {
	state, err := chain.Load(ctx, options.chainDir())
	if err != nil {
		return ChainView{}, err
	}
	view := chainView(state)
	frontTarget := ""
	if state.Front != nil {
		if frontTarget, err = resolveFrontTarget(ctx, options, *state.Front); err != nil {
			view.FrontUnavailable = true
		}
	}
	if !service.ProcessRunning(options.SingBoxPath) || len(state.Landings.Outbounds) == 0 {
		return view, nil
	}
	view.Running = true
	client, err := serviceapi.New(options.ServiceAddress, options.ServiceSecret)
	if err != nil {
		return view, err
	}
	defer client.Close()
	requestContext, cancel := context.WithTimeout(ctx, minTimeout(options.RequestTimeout, 3*time.Second))
	defer cancel()
	groups, err := client.Groups(requestContext)
	if err != nil {
		return view, err
	}
	// 选择同步在指定前置不存在于运行时（没有可用节点）时会回退，这时实际选择与指定的目标不同。
	if selected := mergeChainRuntime(&view, groups); frontTarget != "" && selected != frontTarget {
		view.FrontUnavailable = true
	}
	return view, nil
}

func chainView(state chain.State) ChainView {
	view := ChainView{
		Mode: state.Mode, Manual: state.Manual,
		Landings: state.Summaries(), Policies: slices.Clone(state.Policies),
		Delays: map[string]int32{},
	}
	if state.Front != nil {
		front := *state.Front
		view.FrontSetting = &front
	}
	return view
}

// mergeChainRuntime 合并后置测速记录与前置实际节点，返回「链式前置」选择器的直接选择。
func mergeChainRuntime(view *ChainView, groups []serviceapi.Group) string {
	selected := make(map[string]string, len(groups))
	for _, group := range groups {
		selected[group.Tag] = group.Selected
		if group.Tag != chain.SelectorTag {
			continue
		}
		view.Landing = strings.TrimPrefix(group.Selected, chain.LandingTagPrefix)
		for _, item := range group.Items {
			if item.URLTestTime > 0 {
				view.Delays[strings.TrimPrefix(item.Tag, chain.LandingTagPrefix)] = item.URLTestDelay
			}
		}
	}
	// 前置可能选择的是分组（Auto/Select/智能分组），沿选择链找到实际节点。
	direct := selected[chain.FrontTag]
	current := direct
	for depth := 0; depth < 4 && current != ""; depth++ {
		next, isGroup := selected[current]
		if !isGroup || next == "" {
			break
		}
		current = next
	}
	view.Front = current
	return direct
}

// UpdateChain 串行修改链式代理设置。服务运行时立即生效：只换手动后置时通过 Service API 切换，
// 其他变化重载核心；重载失败恢复修改前的文件，不留下与运行实例不一致的设置。
func UpdateChain(ctx context.Context, options Options, mutate func(*chain.State) error) (view ChainView, err error) {
	persisted := false
	defer func() { logOperation(options, "chain", "chain.update", "链式代理设置", persisted, err) }()
	if err := options.validate(); err != nil {
		return ChainView{}, err
	}
	lock, err := waitLifecycleLock(ctx, options.StateFile)
	if err != nil {
		return ChainView{}, err
	}
	defer lock.release()
	if err := recoverConfigApply(ctx, options); err != nil {
		return ChainView{}, err
	}
	before, after, snapshot, err := chain.Update(ctx, options.chainDir(), mutate)
	if err != nil {
		return ChainView{}, err
	}
	persisted = true
	view = chainView(after)
	if !service.ProcessRunning(options.SingBoxPath) {
		return view, nil
	}
	reload, selectOnly := chainRuntimeChange(before, after)
	switch {
	case reload:
		if err := ReloadService(ctx, options); err != nil {
			persisted = false
			restoreErr := snapshot.Restore()
			return ChainView{}, errors.Join(&service.Error{
				Code: "chain.apply_failed", Message: fmt.Sprintf("链式代理设置未能生效，已恢复修改前的设置: %v", err),
				Data: map[string]any{"persisted": false, "cause": err.Error()},
			}, restoreErr)
		}
	case selectOnly:
		if err := syncSelection(ctx, options); err != nil {
			return view, &service.Error{
				Code: "chain.runtime_sync_failed", Message: fmt.Sprintf("链式代理设置已保存，但运行时切换失败: %v", err),
				Data: map[string]any{"persisted": true, "runtime_synced": false, "cause": err.Error()},
			}
		}
	}
	return view, nil
}

// chainRuntimeChange 判断修改是否需要重载核心，或只需切换选择器。
func chainRuntimeChange(before, after chain.State) (reload, selectOnly bool) {
	if before.Mode != after.Mode || !sameLandings(before.Landings.Outbounds, after.Landings.Outbounds) {
		return true, false
	}
	if chain.UsesPolicies(after.Mode) && !samePolicies(before.Policies, after.Policies) {
		return true, false
	}
	// 「链式前置」包含全部分组与节点，换前置只需要切换选择器。
	changed := before.Manual != after.Manual || !chain.SameFront(before.Front, after.Front)
	return false, changed && len(after.Landings.Outbounds) > 0
}

func sameLandings(before, after []option.Outbound) bool {
	// Clone 只复制切片，未修改的后置共享同一个 Options 指针；导入、删除或改名都会改变比较结果。
	return slices.EqualFunc(before, after, func(left, right option.Outbound) bool {
		return left.Tag == right.Tag && left.Type == right.Type && left.Options == right.Options
	})
}

func samePolicies(before, after []chain.Policy) bool {
	return slices.EqualFunc(before, after, func(left, right chain.Policy) bool {
		return left.ID == right.ID && left.Name == right.Name && left.Enabled == right.Enabled &&
			left.Landing == right.Landing && slices.Equal(left.Packages, right.Packages)
	})
}

// TestChainLandings 经由当前前置测试后置代理，同时单独测试前置节点；tag 为空时测试全部后置。
// 内核在测试失败时删除测速记录，因此以「记录更新」判定成功，以「记录被删除或超时仍未更新」判定失败（-1）。
func TestChainLandings(ctx context.Context, options Options, tag string) (ChainView, error) {
	state, err := chain.Load(ctx, options.chainDir())
	if err != nil {
		return ChainView{}, err
	}
	if len(state.Landings.Outbounds) == 0 {
		return ChainView{}, errors.New("还没有后置代理")
	}
	if tag != "" && state.FindLanding(tag) < 0 {
		return ChainView{}, fmt.Errorf("后置代理 %q 不存在", tag)
	}
	if !service.ProcessRunning(options.SingBoxPath) {
		return ChainView{}, &service.Error{Code: "chain.service_stopped", Message: "服务未运行，无法经由前置测试后置代理"}
	}
	client, err := serviceapi.New(options.ServiceAddress, options.ServiceSecret)
	if err != nil {
		return ChainView{}, err
	}
	defer client.Close()
	testContext, cancel := context.WithTimeout(ctx, 20*time.Second)
	defer cancel()
	groups, err := client.Groups(testContext)
	if err != nil {
		return ChainView{}, err
	}
	view := chainView(state)
	view.Running = true
	mergeChainRuntime(&view, groups)
	targets := make([]string, 0, len(state.Landings.Outbounds)+1)
	for _, outbound := range state.Landings.Outbounds {
		if tag == "" || outbound.Tag == tag {
			targets = append(targets, chain.LandingTag(outbound.Tag))
		}
	}
	// 前置节点单独测一次：前置可用而后置失败时，问题出在前置到后置这一段。
	front := view.Front
	if front == "" || slices.ContainsFunc(groups, func(group serviceapi.Group) bool { return group.Tag == front }) {
		front = ""
	} else {
		targets = append(targets, front)
	}
	before, err := urlTestTimes(testContext, client)
	if err != nil {
		return ChainView{}, err
	}
	for _, target := range targets {
		if err := client.URLTest(testContext, target); err != nil {
			return ChainView{}, err
		}
	}
	results := waitURLTests(testContext, client, targets, before, 12*time.Second)
	if latest, err := client.Groups(testContext); err == nil {
		mergeChainRuntime(&view, latest)
	}
	for target, delay := range results {
		if target == front {
			view.FrontDelay = &delay
			continue
		}
		view.Delays[strings.TrimPrefix(target, chain.LandingTagPrefix)] = delay
	}
	return view, nil
}

func urlTestTimes(ctx context.Context, client *serviceapi.Client) (map[string]int64, error) {
	outbounds, err := client.Outbounds(ctx)
	if err != nil {
		return nil, err
	}
	result := make(map[string]int64, len(outbounds))
	for _, item := range outbounds {
		result[item.Tag] = item.URLTestTime
	}
	return result, nil
}

// waitURLTests 轮询测速记录直到每个目标都有结果：记录更新为成功，原有记录被删除为失败，超时仍未更新也按失败处理。
func waitURLTests(ctx context.Context, client *serviceapi.Client, targets []string, before map[string]int64, limit time.Duration) map[string]int32 {
	results := make(map[string]int32, len(targets))
	poll := time.NewTicker(300 * time.Millisecond)
	defer poll.Stop()
	deadline := time.NewTimer(limit)
	defer deadline.Stop()
	for len(results) < len(targets) {
		select {
		case <-ctx.Done():
		case <-deadline.C:
		case <-poll.C:
			outbounds, err := client.Outbounds(ctx)
			if err != nil {
				continue
			}
			current := make(map[string]serviceapi.GroupItem, len(outbounds))
			for _, item := range outbounds {
				current[item.Tag] = item
			}
			for _, target := range targets {
				if _, done := results[target]; done {
					continue
				}
				item, present := current[target]
				switch {
				case present && item.URLTestTime > before[target]:
					results[target] = item.URLTestDelay
				case before[target] > 0 && (!present || item.URLTestTime == 0):
					results[target] = -1
				}
			}
			continue
		}
		break
	}
	for _, target := range targets {
		if _, done := results[target]; !done {
			results[target] = -1
		}
	}
	return results
}
