package module

import (
	"context"
	"errors"
	"fmt"
	"slices"
	"strings"
	"time"

	"github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/chain"
	"github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/service"
	"github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/serviceapi"
	"github.com/sagernet/sing-box/option"
)

// ChainView 是链式代理页面使用的完整快照。
type ChainView struct {
	Mode     string                 `json:"mode"`
	Manual   string                 `json:"manual"`
	Landings []chain.LandingSummary `json:"landings"`
	Policies []chain.Policy         `json:"policies"`
	Running  bool                   `json:"running"`
	// Front 是前置实际使用的节点（运行时解析），Landing 是「链式代理」选择器当前的后置。
	Front   string           `json:"front"`
	Landing string           `json:"landing"`
	Delays  map[string]int32 `json:"delays"`
}

// chainTargets 描述选择同步时链式代理需要额外设置的选择器。
type chainTargets struct {
	enabled bool
	landing string
	proxy   string
}

func loadChainTargets(ctx context.Context, options Options, active string) (chainTargets, error) {
	state, err := chain.Load(ctx, options.chainDir())
	if err != nil {
		return chainTargets{}, err
	}
	targets := chainTargets{proxy: active}
	if len(state.Landings.Outbounds) == 0 {
		return targets, nil
	}
	targets.enabled = true
	if state.Manual != "" {
		targets.landing = chain.LandingTag(state.Manual)
	}
	if chain.UsesManual(state.Mode) {
		targets.proxy = chain.SelectorTag
	}
	return targets, nil
}

// applyRuntimeSelection 依次设置分组内节点、链式前置、链式代理与顶层 Proxy。
// 前置必须与节点页的选择保持同一个分组目标：手动后置接管 Proxy 后，节点选择只改变前置。
func applyRuntimeSelection(ctx context.Context, client *serviceapi.Client, active, inner string, targets chainTargets) error {
	if inner != "" {
		if err := client.Select(ctx, active, inner); err != nil {
			return err
		}
	}
	if targets.enabled {
		if err := client.Select(ctx, chain.FrontTag, active); err != nil {
			return err
		}
		if targets.landing != "" {
			if err := client.Select(ctx, chain.SelectorTag, targets.landing); err != nil {
				return err
			}
		}
	}
	return client.Select(ctx, "Proxy", targets.proxy)
}

// ReadChain 返回链式代理设置；服务运行时合并前置、后置的实际选择与测速结果。
func ReadChain(ctx context.Context, options Options) (ChainView, error) {
	state, err := chain.Load(ctx, options.chainDir())
	if err != nil {
		return ChainView{}, err
	}
	view := chainView(state)
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
	mergeChainRuntime(&view, groups)
	return view, nil
}

func chainView(state chain.State) ChainView {
	return ChainView{
		Mode: state.Mode, Manual: state.Manual,
		Landings: state.Summaries(), Policies: slices.Clone(state.Policies),
		Delays: map[string]int32{},
	}
}

func mergeChainRuntime(view *ChainView, groups []serviceapi.Group) {
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
	// 前置选择的是分组（Auto/Select），沿选择链找到实际节点。
	current := selected[chain.FrontTag]
	for depth := 0; depth < 4 && current != ""; depth++ {
		next, isGroup := selected[current]
		if !isGroup || next == "" {
			break
		}
		current = next
	}
	view.Front = current
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
	return false, before.Manual != after.Manual && len(after.Landings.Outbounds) > 0
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

// TestChainLandings 经由当前前置测试后置代理延迟；tag 为空时测试全部后置。
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
	target := chain.SelectorTag
	if tag != "" {
		target = chain.LandingTag(tag)
	}
	testContext, cancel := context.WithTimeout(ctx, 15*time.Second)
	defer cancel()
	before := landingTestTimes(testContext, client)
	if err := client.URLTest(testContext, target); err != nil {
		return ChainView{}, err
	}
	expected := make([]string, 0, len(state.Landings.Outbounds))
	for _, outbound := range state.Landings.Outbounds {
		if tag == "" || outbound.Tag == tag {
			expected = append(expected, chain.LandingTag(outbound.Tag))
		}
	}
	// 测速结果异步写入；每个目标的测试时间更新或超时后返回最新快照。
	poll := time.NewTicker(300 * time.Millisecond)
	defer poll.Stop()
	limit := time.NewTimer(10 * time.Second)
	defer limit.Stop()
wait:
	for {
		select {
		case <-testContext.Done():
			break wait
		case <-limit.C:
			break wait
		case <-poll.C:
		}
		current := landingTestTimes(testContext, client)
		done := true
		for _, landing := range expected {
			if current[landing] <= before[landing] {
				done = false
				break
			}
		}
		if done {
			break
		}
	}
	view := chainView(state)
	view.Running = true
	groups, err := client.Groups(ctx)
	if err != nil {
		return view, err
	}
	mergeChainRuntime(&view, groups)
	return view, nil
}

func landingTestTimes(ctx context.Context, client *serviceapi.Client) map[string]int64 {
	result := make(map[string]int64)
	outbounds, err := client.Outbounds(ctx)
	if err != nil {
		return result
	}
	for _, item := range outbounds {
		if strings.HasPrefix(item.Tag, chain.LandingTagPrefix) {
			result[item.Tag] = item.URLTestTime
		}
	}
	return result
}
