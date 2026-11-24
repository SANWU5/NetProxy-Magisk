// Package module 提供模块业务操作的 Go 应用服务。
package module

import (
	"context"
	json "encoding/json/v2"
	"errors"
	"fmt"
	"net/url"
	"os"
	"path/filepath"
	"slices"
	"strings"
	"time"

	"github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/catalog"
	"github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/chain"
	moduleconfig "github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/config"
	"github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/inbound"
	"github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/paths"
	"github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/provider"
	"github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/service"
	"github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/serviceapi"
	"github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/smartgroup"
	"github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/subscription"
	"github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/telemetry"
	"github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/worker"
	"github.com/sagernet/sing-box/option"
)

// Options 描述模块目录、运行时目录和平台适配器路径。
type Options struct {
	ModuleDir          string
	ManagerVersion     string
	ManagerVersionCode string
	CatalogRoot        string
	ModuleConfig       string
	InboundConfig      string
	SingBoxPath        string
	SingBoxDir         string
	RuntimeDir         string
	StateFile          string
	ProgressDir        string
	LogDir             string
	ServiceAddress     string
	ServiceSecret      string
	WorkerPIDFile      string
	WorkerLogFile      string
	WiFiStateFile      string
	RequestTimeout     time.Duration
	// TransferContext 仅在服务生命周期结束时取消订阅下载，不取消持久事务。
	TransferContext    context.Context
	NetworkStateReader worker.NetworkStateReader
	Telemetry          *telemetry.Reporter
	configEditors      map[string]*moduleconfig.Editor
	networkEvaluation  *NetworkEvaluation
}

// NewOptions 根据模块根目录返回完整的默认路径。
func NewOptions(moduleDir string) Options {
	layout := paths.New(moduleDir)
	return Options{
		ModuleDir:      layout.Root(),
		CatalogRoot:    layout.Catalog(),
		ModuleConfig:   layout.ModuleConfig(),
		InboundConfig:  layout.InboundConfig(),
		SingBoxPath:    layout.SingBox(),
		SingBoxDir:     layout.SingBoxDir(),
		RuntimeDir:     layout.Runtime(),
		StateFile:      layout.ServiceState(),
		ProgressDir:    layout.ProgressDir(),
		LogDir:         layout.Logs(),
		ServiceAddress: "127.0.0.1:9090",
		ServiceSecret:  "singbox",
		WorkerPIDFile:  layout.WorkerPID(),
		WorkerLogFile:  layout.ServiceLog(),
		WiFiStateFile:  layout.WiFiState(),
		RequestTimeout: 8 * time.Second,
		Telemetry:      telemetry.New(layout),
	}
}

// PrepareResult 描述一次运行时准备结果。
type PrepareResult struct {
	catalog.RuntimeResult
	Providers string `json:"providers"`
	Outbounds string `json:"outbounds"`
	Inbound   string `json:"inbound"`
	Route     string `json:"route,omitempty"`
	// Log 是关闭「记录日志」时覆盖主配置日志设置的片段；记录日志时为空。
	Log     string             `json:"log,omitempty"`
	Backend string             `json:"backend"`
	Network *NetworkEvaluation `json:"-"`
	// IPv6DNSGuard 表示本次运行启用了 IPv6 DNS 保护；核心因此启动失败时退回不保护并重试。
	IPv6DNSGuard bool `json:"-"`
}

// AppPolicy 描述分应用代理的持久设置。
type AppPolicy struct {
	Enabled    bool   `json:"enabled"`
	Mode       string `json:"mode"`
	ProxyApps  string `json:"proxy_apps"`
	BypassApps string `json:"bypass_apps"`
}

// Prepare 生成 Catalog、出站和当前受管入站运行时配置。
func Prepare(ctx context.Context, options Options, allowEmpty bool) (PrepareResult, error) {
	if err := options.validate(); err != nil {
		return PrepareResult{}, err
	}
	for _, path := range []string{options.RuntimeDir, filepath.Dir(options.StateFile)} {
		if err := os.MkdirAll(path, 0o700); err != nil {
			return PrepareResult{}, err
		}
	}
	providers := filepath.Join(options.RuntimeDir, "providers.json")
	outbounds := filepath.Join(options.RuntimeDir, "outbounds.json")
	inboundPath := filepath.Join(options.RuntimeDir, "inbound.json")
	config, err := inbound.Load(options.InboundConfig)
	if err != nil {
		return PrepareResult{}, err
	}
	if err := validateManagedConfig(options, config); err != nil {
		return PrepareResult{}, err
	}
	if config.Backend == "tun" {
		groups, err := catalog.GroupIDs(ctx, options.CatalogRoot, "all")
		if err != nil {
			return PrepareResult{}, err
		}
		for _, id := range groups {
			document, err := catalog.GroupProvider(ctx, options.CatalogRoot, id)
			if err != nil {
				return PrepareResult{}, err
			}
			for _, outbound := range document.Outbounds {
				if dialer, ok := outbound.Options.(option.DialerOptionsWrapper); ok && dialer.TakeDialerOptions().RoutingMark != 0 {
					return PrepareResult{}, tunRoutingMarkConflict("catalog.routing_mark")
				}
			}
			for _, endpoint := range document.Endpoints {
				if dialer, ok := endpoint.Options.(option.DialerOptionsWrapper); ok && dialer.TakeDialerOptions().RoutingMark != 0 {
					return PrepareResult{}, tunRoutingMarkConflict("catalog.routing_mark")
				}
			}
		}
	}
	chainState, err := chain.Load(ctx, options.chainDir())
	if err != nil {
		return PrepareResult{}, err
	}
	chainRuntime, err := chain.BuildRuntime(ctx, options.chainDir(), chainState, domesticRuleSets(options)...)
	if err != nil {
		return PrepareResult{}, err
	}
	runtimeOptions := catalog.RuntimeOptions{
		Root: options.CatalogRoot, ModuleConfig: options.ModuleConfig,
		ProvidersOutput: providers, OutboundsOutput: outbounds,
		AllowEmpty: allowEmpty,
	}
	if chainRuntime != nil {
		runtimeOptions.Chain = &catalog.ChainOutbounds{
			FrontTag: chain.FrontTag, SelectorTag: chain.SelectorTag,
			Landings: chainRuntime.Landings, LandingTags: chainRuntime.LandingTags,
			Manual: chainRuntime.Manual, ProxyOption: chainRuntime.ProxyOption, ProxyChain: chainRuntime.ProxyChain,
		}
	}
	smartDocument, err := smartgroup.Load(ctx, options.smartDir())
	if err != nil {
		return PrepareResult{}, err
	}
	runtimeOptions.Smart = smartRuntime(smartDocument)
	runtime, err := catalog.BuildRuntime(ctx, runtimeOptions)
	if err != nil {
		return PrepareResult{}, err
	}
	// 策略路由必须排在主配置规则之前才能先于兜底规则命中，排序方式见 preparedConfigArgs。
	// 策略路由文件始终存在，没有策略时为空配置：核心原位重载按启动时的文件列表重新读取，
	// 文件被删除会让重载失败，新出现的文件也不会被读取。
	routePath := filepath.Join(options.RuntimeDir, "route.json")
	routeContent := []byte(emptyOverlay)
	if chainRuntime != nil && chainRuntime.Route != nil {
		routeContent = chainRuntime.Route
	}
	logPath, err := prepareLogOverride(options)
	if err != nil {
		return PrepareResult{}, err
	}
	network, err := configuredNetwork(ctx, options)
	if err != nil {
		return PrepareResult{}, err
	}
	if network.DesiredMode == "Direct" {
		if err := validateDirectRouting(options); err != nil {
			return PrepareResult{}, err
		}
	}
	config, err = config.WithDNSBypass(network.DesiredMode == "Direct")
	if err != nil {
		return PrepareResult{}, err
	}
	// IPv6 DNS 保护：用户不接管 IPv6 时，运行时仍接管 IPv6，但只劫持 DNS、其余 IPv6 直连，
	// 避免应用经网络下发的 IPv6 DNS 服务器查询而被污染。策略路由文件因此在这里才写出。
	ipv6Guard, err := ipv6DNSGuardApplies(options, config)
	if err != nil {
		return PrepareResult{}, err
	}
	if ipv6Guard {
		if config, err = config.WithIPv6DNSGuard(); err != nil {
			return PrepareResult{}, err
		}
		if routeContent, err = withIPv6DirectRule(routeContent); err != nil {
			return PrepareResult{}, err
		}
	}
	if err := provider.WriteAtomic(routePath, routeContent, 0o600); err != nil {
		return PrepareResult{}, err
	}
	// 启用的白名单策略组中的应用必须被接管，否则分应用代理跳过它们时策略组看不到这些流量。
	config.App, err = interceptPolicyApps(config.App, chainState, func(refs []inbound.PackageRef) (inbound.PackageUIDResolution, error) {
		return inbound.ResolvePackageUIDs(ctx, refs)
	})
	if err != nil {
		return PrepareResult{}, err
	}
	missingPackages, err := inbound.WriteAtomic(ctx, inboundPath, config)
	if err != nil {
		return PrepareResult{}, err
	}
	for _, ref := range missingPackages {
		logService(options, "WARN", "inbound.package", "skipped", "分应用代理跳过未安装应用: %s", ref.String())
	}
	return PrepareResult{RuntimeResult: runtime, Providers: providers, Outbounds: outbounds, Inbound: inboundPath, Route: routePath, Log: logPath, Backend: config.Backend, Network: &network, IPv6DNSGuard: ipv6Guard}, nil
}

// logOverride 关闭核心日志；启动失败这类致命错误由 sing-box 命令行直接写到标准错误，仍会记录。
const logOverride = "{\"log\":{\"disabled\":true}}\n"

// prepareLogOverride 按「记录日志」开关写入或删除日志覆盖片段，返回需要传给核心的片段路径。
func prepareLogOverride(options Options) (string, error) {
	module, err := moduleconfig.LoadModule(options.ModuleConfig)
	if err != nil {
		return "", err
	}
	// 日志覆盖文件同样始终存在（记录核心日志时为空配置），切换日志开关后原位重载才能生效。
	path := filepath.Join(options.RuntimeDir, "log.json")
	content := logOverride
	if module.Logging {
		content = emptyOverlay
	}
	return path, provider.WriteAtomic(path, []byte(content), 0o600)
}

// emptyOverlay 是不改变任何设置的覆盖配置，让传给核心的配置文件列表保持不变。
const emptyOverlay = "{}\n"

func (options Options) chainDir() string {
	return paths.New(options.ModuleDir).Chain()
}

func (options Options) smartDir() string {
	return paths.New(options.ModuleDir).Smart()
}

func (options Options) updateSelection(ctx context.Context, selection moduleconfig.Selection) error {
	if editor := options.configEditors[filepath.Clean(options.ModuleConfig)]; editor != nil {
		return editor.UpdateSelection(selection)
	}
	return moduleconfig.UpdateSelection(ctx, options.ModuleConfig, selection)
}

// Check 生成隔离运行时配置并执行 sing-box check。
func Check(ctx context.Context, options Options, allowEmpty bool) (PrepareResult, error) {
	prepared, err := Prepare(ctx, options, allowEmpty)
	if err != nil {
		return PrepareResult{}, err
	}
	if options.SingBoxPath == "" {
		return prepared, errors.New("sing-box 路径为空")
	}
	return prepared, checkPreparedConfiguration(ctx, options, prepared)
}

// SelectNode 更新持久选择，并在服务运行时通过 Service API 同步选择器。
func SelectNode(ctx context.Context, options Options, target, group string) (data map[string]string, err error) {
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
	selection := moduleconfig.Selection{ActiveGroupID: group}
	if target == "auto" {
		if strings.TrimSpace(group) == "" {
			selection.ActiveGroupID = module.ActiveGroupID
		}
	} else {
		selection.ActiveGroupID, selection.SelectedNodeTag, err = splitReference(target)
		if err != nil {
			return nil, err
		}
	}
	selection, runtimeTag, err := catalog.ResolveSelection(ctx, options.CatalogRoot, selection)
	if err != nil {
		return nil, err
	}
	if selection != module.Selection {
		if err := options.updateSelection(ctx, selection); err != nil {
			return nil, err
		}
	}
	persisted = true
	active, inner := selection.RuntimeTargets(runtimeTag)
	selected := active
	if inner != "" {
		selected = inner
	}
	data = map[string]string{"group_id": selection.ActiveGroupID, "mode": selection.Mode(), "selected": selected}
	if service.ProcessRunning(options.SingBoxPath) {
		if err := syncRuntimeSelector(ctx, options, selection, active, inner); err != nil {
			return data, &service.Error{Code: "node.runtime_sync_failed", Message: fmt.Sprintf("节点选择已保存，但运行时切换失败: %v", err),
				Data: map[string]any{"persisted": true, "runtime_synced": false, "group_id": selection.ActiveGroupID, "mode": selection.Mode(), "selected": selected, "cause": err.Error()}}
		}
	}
	return data, nil
}

func syncSelection(ctx context.Context, options Options) error {
	module, err := moduleconfig.LoadModule(options.ModuleConfig)
	if err != nil {
		return err
	}
	if strings.TrimSpace(module.ActiveGroupID) == "" {
		return nil
	}
	_, runtimeTag, err := catalog.ResolveSelection(ctx, options.CatalogRoot, module.Selection)
	if err != nil {
		return err
	}
	active, inner := module.Selection.RuntimeTargets(runtimeTag)
	return syncRuntimeSelector(ctx, options, module.Selection, active, inner)
}

// syncRuntimeSelector 把持久选择同步到运行实例：active/inner 是普通分组的选择器与节点，
// selection 中的智能分组与链式代理设置决定顶层 Proxy 与链式前置的选择。
func syncRuntimeSelector(ctx context.Context, options Options, selection moduleconfig.Selection, active, inner string) error {
	targets, err := loadSelectionTargets(ctx, options, selection, active)
	if err != nil {
		return err
	}
	client, err := serviceapi.New(options.ServiceAddress, options.ServiceSecret)
	if err != nil {
		return err
	}
	defer client.Close()
	requestContext, cancel := context.WithTimeout(ctx, minTimeout(options.RequestTimeout, 6*time.Second))
	defer cancel()
	for {
		if err := requestContext.Err(); err != nil {
			return err
		}
		attemptContext, cancelAttempt := context.WithTimeout(requestContext, time.Second)
		err := applyRuntimeSelection(attemptContext, client, active, inner, targets)
		cancelAttempt()
		if err == nil || !serviceapi.IsRetryable(err) {
			return err
		}
		timer := time.NewTimer(200 * time.Millisecond)
		select {
		case <-requestContext.Done():
			timer.Stop()
			return errors.Join(err, requestContext.Err())
		case <-timer.C:
		}
	}
}

// UpdateApp 在配置事务锁内修改最新应用策略，并应用到运行实例。
func UpdateApp(ctx context.Context, options Options, action, value string) (data AppPolicy, err error) {
	persisted := false
	defer func() { logOperation(options, "app", "app-policy.update", "分应用策略更新", persisted, err) }()
	if err := options.validate(); err != nil {
		return AppPolicy{}, err
	}
	options, release, err := lockConfigApply(ctx, options, options.InboundConfig, false)
	if err != nil {
		return AppPolicy{}, err
	}
	defer release()
	content, err := os.ReadFile(options.InboundConfig)
	if err != nil {
		return AppPolicy{}, err
	}
	config, err := inbound.Parse(content)
	if err != nil {
		return AppPolicy{}, err
	}
	policy := config.App
	switch action {
	case "mode":
		if value != "blacklist" && value != "whitelist" {
			return AppPolicy{}, errors.New("应用模式应为 blacklist 或 whitelist")
		}
		policy.Enabled = true
		policy.Mode = value
	case "add":
		ref, err := inbound.ParsePackageRef(value)
		if err != nil {
			return AppPolicy{}, err
		}
		if policy.Mode == "whitelist" {
			policy.ProxyApps = addPackageRef(policy.ProxyApps, ref.String())
		} else {
			policy.BypassApps = addPackageRef(policy.BypassApps, ref.String())
		}
		policy.Enabled = true
	case "remove":
		ref, err := inbound.ParsePackageRef(value)
		if err != nil {
			return AppPolicy{}, err
		}
		policy.ProxyApps = slices.DeleteFunc(policy.ProxyApps, func(item string) bool { return item == ref.String() })
		policy.BypassApps = slices.DeleteFunc(policy.BypassApps, func(item string) bool { return item == ref.String() })
	case "enable", "disable":
		policy.Enabled = action == "enable"
	default:
		return AppPolicy{}, fmt.Errorf("未知应用操作: %s", action)
	}
	content, err = json.Marshal(map[string]inbound.AppPolicy{"app": policy}, json.Deterministic(true))
	if err != nil {
		return AppPolicy{}, err
	}
	if _, err := applyConfigLocked(ctx, options, "inbound/app", options.InboundConfig, content, false, ""); err != nil {
		return AppPolicy{}, err
	}
	persisted = true
	config.App = policy
	return appPolicy(config), nil
}

func appPolicy(config inbound.Config) AppPolicy {
	return AppPolicy{
		Enabled:    config.App.Enabled,
		Mode:       config.App.Mode,
		ProxyApps:  strings.Join(config.App.ProxyApps, ","),
		BypassApps: strings.Join(config.App.BypassApps, ","),
	}
}

// NodeAppend 将节点加入本地分组并处理活动状态与运行时 reload。
func NodeAppend(ctx context.Context, options Options, groupID, input string, allowInsecure bool) (mutation catalog.MutationResult, err error) {
	return nodeAppend(ctx, options, groupID, "", "append", allowInsecure, func(mutationOptions catalog.MutationOptions) (catalog.MutationResult, error) {
		mutationOptions.Input = input
		return catalog.AppendNode(ctx, mutationOptions)
	})
}

// NodeAppendContent 将原始内容加入本地分组，不读取客户端指定的文件路径。
// newGroupName 不为空时新建一个同名本地分组并加入其中，groupID 被忽略；解析失败时不留下空分组。
func NodeAppendContent(ctx context.Context, options Options, groupID, newGroupName string, content []byte, allowInsecure bool) (catalog.MutationResult, error) {
	return nodeAppend(ctx, options, groupID, newGroupName, "append", allowInsecure, func(mutationOptions catalog.MutationOptions) (catalog.MutationResult, error) {
		return catalog.AppendNodeContent(ctx, mutationOptions, content)
	})
}

// createLocalGroup 新建一个空的本地分组。名称不能与已有分组重复，否则按名称引用分组会产生歧义。
func createLocalGroup(ctx context.Context, options Options, name string) (string, error) {
	if _, err := catalog.ResolveGroup(ctx, options.CatalogRoot, name); err == nil || !strings.Contains(err.Error(), "分组不存在") {
		if err == nil {
			return "", fmt.Errorf("已有同名分组: %s", name)
		}
		return "", err
	}
	id, err := catalog.NewSubscriptionGroupID(ctx, options.CatalogRoot)
	if err != nil {
		return "", err
	}
	if err := catalog.InitializeGroup(ctx, catalog.GroupOptions{Root: options.CatalogRoot, GroupID: id, Name: name, Type: "local"}); err != nil {
		return "", err
	}
	return id, nil
}

// NodeImport 将本地文件中的节点追加到 default 本地配置组。
func NodeImport(ctx context.Context, options Options, input string, allowInsecure bool) (mutation catalog.MutationResult, err error) {
	return nodeAppend(ctx, options, "default", "", "import", allowInsecure, func(mutationOptions catalog.MutationOptions) (catalog.MutationResult, error) {
		mutationOptions.Input = input
		return catalog.AppendNode(ctx, mutationOptions)
	})
}

// NodeImportContent 将原始节点内容追加到 default 组，不读取客户端指定的文件路径。
func NodeImportContent(ctx context.Context, options Options, content []byte, allowInsecure bool) (catalog.MutationResult, error) {
	return nodeAppend(ctx, options, "default", "", "import", allowInsecure, func(mutationOptions catalog.MutationOptions) (catalog.MutationResult, error) {
		return catalog.AppendNodeContent(ctx, mutationOptions, content)
	})
}

func nodeAppend(ctx context.Context, options Options, groupID, newGroupName, action string, allowInsecure bool, appendNodes func(catalog.MutationOptions) (catalog.MutationResult, error)) (mutation catalog.MutationResult, err error) {
	message := "节点添加"
	if action == "import" {
		message = "节点导入"
	}
	defer func() { logOperation(options, "node", "node."+action, message, mutation.Revision > 0, err) }()
	options, saved, release, err := lockCatalogChange(ctx, options)
	if err != nil {
		return catalog.MutationResult{}, err
	}
	defer release()
	if err := ensureDefaultGroup(ctx, options); err != nil {
		return catalog.MutationResult{}, err
	}
	created := false
	if newGroupName = strings.TrimSpace(newGroupName); newGroupName != "" {
		if groupID, err = createLocalGroup(ctx, options, newGroupName); err != nil {
			return catalog.MutationResult{}, err
		}
		created = true
	} else {
		if groupID == "" {
			groupID = "default"
		}
		groupID, err = catalog.ResolveGroup(ctx, options.CatalogRoot, groupID)
		if err != nil {
			return catalog.MutationResult{}, err
		}
		// URL 订阅每次更新都会整组替换，手动加入的节点会丢失，因此只允许加入本地分组。
		if typ, err := catalog.GroupType(ctx, options.CatalogRoot, groupID); err != nil {
			return catalog.MutationResult{}, err
		} else if typ != "local" {
			return catalog.MutationResult{}, errors.New("只能把节点加入本地分组；URL 订阅更新时会覆盖手动加入的节点")
		}
	}
	result, err := appendNodes(catalog.MutationOptions{
		GroupDir:      filepath.Join(options.CatalogRoot, groupID),
		GroupID:       groupID,
		Type:          "local",
		AllowInsecure: allowInsecure,
	})
	if err != nil {
		if created {
			_ = catalog.DeleteGroup(ctx, options.CatalogRoot, groupID)
		}
		return catalog.MutationResult{}, err
	}
	_, attempted, err := syncCatalogChange(ctx, options, saved, groupID, result.StructureChanged, service.ProcessRunning(options.SingBoxPath))
	return result, catalogChangeError("node", attempted, err)
}

// NodeEdit 原子替换指定分组的节点。
func NodeEdit(ctx context.Context, options Options, reference, input string, allowInsecure bool) (mutation catalog.MutationResult, err error) {
	return nodeEdit(ctx, options, reference, allowInsecure, func(mutationOptions catalog.MutationOptions) (catalog.MutationResult, error) {
		mutationOptions.Input = input
		return catalog.EditNode(ctx, mutationOptions)
	})
}

// NodeEditContent 使用原始内容替换指定节点，不解释设备文件路径。
func NodeEditContent(ctx context.Context, options Options, reference string, content []byte, allowInsecure bool) (catalog.MutationResult, error) {
	return nodeEdit(ctx, options, reference, allowInsecure, func(mutationOptions catalog.MutationOptions) (catalog.MutationResult, error) {
		return catalog.EditNodeContent(ctx, mutationOptions, content)
	})
}

func nodeEdit(ctx context.Context, options Options, reference string, allowInsecure bool, editNode func(catalog.MutationOptions) (catalog.MutationResult, error)) (mutation catalog.MutationResult, err error) {
	defer func() { logOperation(options, "node", "node.edit", "节点编辑", mutation.Revision > 0, err) }()
	options, saved, release, err := lockCatalogChange(ctx, options)
	if err != nil {
		return catalog.MutationResult{}, err
	}
	defer release()
	groupID, tag, err := splitReference(reference)
	if err != nil {
		return catalog.MutationResult{}, err
	}
	groupID, err = catalog.ResolveGroup(ctx, options.CatalogRoot, groupID)
	if err != nil {
		return catalog.MutationResult{}, err
	}
	result, err := editNode(catalog.MutationOptions{GroupDir: filepath.Join(options.CatalogRoot, groupID), GroupID: groupID, Tag: tag, AllowInsecure: allowInsecure})
	if err != nil {
		return catalog.MutationResult{}, err
	}
	_, attempted, err := syncCatalogChange(ctx, options, saved, groupID, result.StructureChanged, service.ProcessRunning(options.SingBoxPath))
	return result, catalogChangeError("node", attempted, err)
}

// NodeRemove 删除指定节点，并在手动节点消失时回退 Auto。
func NodeRemove(ctx context.Context, options Options, reference string) (mutation catalog.MutationResult, err error) {
	defer func() { logOperation(options, "node", "node.remove", "节点删除", mutation.Revision > 0, err) }()
	options, saved, release, err := lockCatalogChange(ctx, options)
	if err != nil {
		return catalog.MutationResult{}, err
	}
	defer release()
	groupID, tag, err := splitReference(reference)
	if err != nil {
		return catalog.MutationResult{}, err
	}
	groupID, err = catalog.ResolveGroup(ctx, options.CatalogRoot, groupID)
	if err != nil {
		return catalog.MutationResult{}, err
	}
	result, err := catalog.RemoveNode(ctx, catalog.MutationOptions{GroupDir: filepath.Join(options.CatalogRoot, groupID), GroupID: groupID, Tag: tag})
	if err != nil {
		return catalog.MutationResult{}, err
	}
	_, attempted, err := syncCatalogChange(ctx, options, saved, groupID, result.StructureChanged, service.ProcessRunning(options.SingBoxPath))
	return result, catalogChangeError("node", attempted, err)
}

// RemoveSubscription 删除 URL 订阅或本地分组（default 只清空节点），并处理活动分组替代。
func RemoveSubscription(ctx context.Context, options Options, query, replacement string) (err error) {
	deleted := false
	defer func() { logOperation(options, "subscription", "subscription.remove", "订阅删除", deleted, err) }()
	options, saved, release, err := lockCatalogChange(ctx, options)
	if err != nil {
		return err
	}
	defer release()
	groupID, err := catalog.ResolveGroup(ctx, options.CatalogRoot, query)
	if err != nil {
		return err
	}
	typ, err := catalog.GroupType(ctx, options.CatalogRoot, groupID)
	if err != nil || (typ != "subscription" && typ != "local") {
		return errors.New("目标不是订阅或本地分组")
	}
	if replacement != "" {
		replacement, err = catalog.ResolveGroup(ctx, options.CatalogRoot, replacement)
		if err != nil {
			return err
		}
		if replacement == groupID {
			return errors.New("替代分组不能是待删除订阅")
		}
		if _, _, err := catalog.ResolveSelection(ctx, options.CatalogRoot, moduleconfig.Selection{ActiveGroupID: replacement}); err != nil {
			return err
		}
	}
	// default 本地分组是添加节点的默认去处，不能删除，只清空其中的节点；其余分组整个删除。
	if groupID == "default" {
		if _, err := catalog.ClearGroup(ctx, catalog.MutationOptions{
			GroupDir: filepath.Join(options.CatalogRoot, groupID), GroupID: groupID, Type: "local",
		}); err != nil {
			return err
		}
	} else if err := catalog.DeleteGroup(ctx, options.CatalogRoot, groupID); err != nil {
		return err
	}
	deleted = true
	_, attempted, err := syncCatalogChange(ctx, options, saved, replacement, true, service.ProcessRunning(options.SingBoxPath))
	return catalogChangeError("subscription", attempted, err)
}

func lockCatalogChange(ctx context.Context, options Options) (Options, moduleconfig.Selection, func(), error) {
	if err := options.validate(); err != nil {
		return options, moduleconfig.Selection{}, nil, err
	}
	lock, err := waitLifecycleLock(ctx, options.StateFile)
	if err != nil {
		return options, moduleconfig.Selection{}, nil, err
	}
	if err := recoverConfigApply(ctx, options); err != nil {
		lock.release()
		return options, moduleconfig.Selection{}, nil, err
	}
	options, release, err := lockConfigFiles(ctx, options, options.ModuleConfig, options.InboundConfig, paths.SingBoxConfig(options.SingBoxDir))
	if err != nil {
		lock.release()
		return options, moduleconfig.Selection{}, nil, err
	}
	module, err := moduleconfig.LoadModule(options.ModuleConfig)
	if err != nil {
		release()
		lock.release()
		return options, moduleconfig.Selection{}, nil, err
	}
	return options, module.Selection, func() { release(); lock.release() }, nil
}

// SyncCatalog 将 Worker 的持久化副作用串行化到服务生命周期，不在下载阶段持锁。
func SyncCatalog(ctx context.Context, options Options, groupID string, structureChanged bool) (string, bool, error) {
	localContext, cancel := context.WithTimeout(context.WithoutCancel(ctx), 5*time.Second)
	defer cancel()
	options, saved, release, err := lockCatalogChange(localContext, options)
	if err != nil {
		return "", false, err
	}
	defer release()
	return applyCatalogChange(ctx, localContext, options, saved, groupID, structureChanged, service.ProcessRunning(options.SingBoxPath))
}

func syncCatalogChange(ctx context.Context, options Options, saved moduleconfig.Selection, preferredGroup string, structureChanged, running bool) (string, bool, error) {
	// Catalog 已提交，取消只能停止运行时请求，不能留下指向已删除节点的持久选择。
	localContext, cancel := context.WithTimeout(context.WithoutCancel(ctx), 5*time.Second)
	defer cancel()
	return applyCatalogChange(ctx, localContext, options, saved, preferredGroup, structureChanged, running)
}

func applyCatalogChange(ctx, localContext context.Context, options Options, saved moduleconfig.Selection, preferredGroup string, structureChanged, running bool) (string, bool, error) {
	selection, runtimeTag, err := catalog.NormalizeSelection(localContext, options.CatalogRoot, saved, preferredGroup)
	if err != nil {
		return "", false, err
	}
	if selection != saved {
		if err := options.updateSelection(localContext, selection); err != nil {
			return "", false, err
		}
	}
	if !running {
		return subscription.RuntimeSyncNotRunning, false, nil
	}
	if runtimeTag == "" {
		return subscription.RuntimeSyncNotRunning, true, StopService(ctx, options)
	}
	if structureChanged || selection.ActiveGroupID != saved.ActiveGroupID {
		err = ReloadService(ctx, options)
	} else {
		active, inner := selection.RuntimeTargets(runtimeTag)
		err = syncRuntimeSelector(ctx, options, selection, active, inner)
	}
	return subscription.RuntimeSyncApplied, true, err
}

func catalogChangeError(component string, attempted bool, err error) error {
	if err == nil {
		return nil
	}
	code, message := "persisted_effect_failed", "数据已保存，但本地选择整理失败"
	if attempted {
		code, message = "runtime_sync_failed", "数据已保存，但运行时同步失败"
	}
	return &service.Error{Code: component + "." + code, Message: message, Data: map[string]any{"persisted": true, "runtime_synced": false, "cause": err.Error()}}
}

func ensureDefaultGroup(ctx context.Context, options Options) error {
	if err := os.MkdirAll(options.CatalogRoot, 0o700); err != nil {
		return err
	}
	return catalog.EnsureGroup(ctx, catalog.GroupOptions{Root: options.CatalogRoot, GroupID: "default", Name: "本地配置", Type: "local"})
}

func splitReference(reference string) (string, string, error) {
	group, tag, found := strings.Cut(reference, "/")
	if !found || group == "" || tag == "" {
		return "", "", errors.New("节点引用格式应为 <group-id>/<tag>")
	}
	return group, tag, nil
}

func (options Options) validate() error {
	for name, value := range map[string]string{"模块配置": options.ModuleConfig, "Catalog": options.CatalogRoot, "入站配置": options.InboundConfig} {
		if strings.TrimSpace(value) == "" {
			return fmt.Errorf("%s路径不能为空", name)
		}
	}
	return nil
}

func minTimeout(value, fallback time.Duration) time.Duration {
	if value <= 0 || value > fallback {
		return fallback
	}
	return value
}

func addPackageRef(current []string, value string) []string {
	if slices.Contains(current, value) {
		return current
	}
	return append(current, value)
}

// SubscriptionOptions 描述订阅业务的公共路径和 Service 适配器。
type SubscriptionOptions struct {
	Options
	Name           string
	URL            string
	UserAgent      string
	HWID           string
	Headers        map[string]string
	AutoUpdate     bool
	UpdateInterval int64
	IntervalSource string
	UpdateViaProxy string
	Include        string
	Exclude        string
	AllowInsecure  bool
	Timeout        int64
}

// AddSubscription 创建订阅并立即执行一次验证更新。
func AddSubscription(ctx context.Context, options SubscriptionOptions) (result subscription.Result, err error) {
	defer func() {
		logOperation(options.Options, "subscription", "subscription.add", "订阅添加", result.Persisted, err)
	}()
	if options.URL == "" {
		return subscription.Result{}, errors.New("订阅 URL 不能为空")
	}
	if err := subscription.ValidateSettings(options.UpdateInterval, options.UpdateViaProxy, options.Timeout); err != nil {
		return subscription.Result{}, err
	}
	if err := ensureDefaultGroup(ctx, options.Options); err != nil {
		return subscription.Result{}, err
	}
	groupID, err := catalog.NewSubscriptionGroupID(ctx, options.CatalogRoot)
	if err != nil {
		return subscription.Result{}, err
	}
	if err := catalog.InitializeGroup(ctx, catalog.GroupOptions{Root: options.CatalogRoot, GroupID: groupID, Name: options.Name, Type: "subscription", URL: options.URL, UserAgent: options.UserAgent, HWID: options.HWID, CustomHeaders: options.Headers, AutoUpdate: options.AutoUpdate, UpdateInterval: options.UpdateInterval, IntervalSource: options.IntervalSource, UpdateViaProxy: options.UpdateViaProxy, Include: options.Include, Exclude: options.Exclude, AllowInsecure: options.AllowInsecure, Timeout: options.Timeout}); err != nil {
		return subscription.Result{}, err
	}
	workerOptions := workerOptions(options.Options)
	// 分组初始化已经提交；首次下载失败时也必须向客户端报告设置已持久化。
	workerOptions.PersistedBeforeUpdate = true
	updated, err := worker.UpdateGroup(ctx, workerOptions, groupID, time.Now(), nil)
	if err != nil {
		if options.Name == "" {
			if fallback := hostName(options.URL); fallback != "" {
				_ = catalog.SetGroupName(ctx, options.CatalogRoot, groupID, fallback, time.Now())
			}
		}
		return updated, err
	}
	return updated, nil
}

// UpdateSubscription 执行指定订阅更新并处理更新后的运行时副作用。
func UpdateSubscription(ctx context.Context, options Options, query string) (result subscription.Result, err error) {
	defer func() {
		logOperation(options, "subscription", "subscription.update", "订阅更新", result.Persisted, err)
	}()
	groupID, err := catalog.ResolveGroup(ctx, options.CatalogRoot, query)
	if err != nil {
		return subscription.Result{}, err
	}
	return worker.UpdateGroup(ctx, workerOptions(options), groupID, time.Now(), nil)
}

// EditSubscription 保存订阅编辑，并将需要变更的运行时状态交给 Worker 应用。
func EditSubscription(ctx context.Context, options Options, query string, edit subscription.EditOptions) (result subscription.EditResult, err error) {
	defer func() {
		logOperation(options, "subscription", "subscription.edit", "订阅编辑", result.Persisted, err)
	}()
	groupID, err := catalog.ResolveGroup(ctx, options.CatalogRoot, query)
	if err != nil {
		return subscription.EditResult{}, err
	}
	edit.Root = options.CatalogRoot
	edit.GroupID = groupID
	edit.ProgressDir = options.ProgressDir
	edit.DeferUpdate = true
	if edit.Now.IsZero() {
		edit.Now = time.Now()
	}
	edited, err := subscription.Edit(ctx, edit)
	if err != nil {
		return edited, err
	}
	if !edited.RequiresUpdate && !edited.NameChanged {
		if !service.ProcessRunning(options.SingBoxPath) {
			if err := subscription.RecordRuntimeSyncNotRunning(ctx, options.CatalogRoot, groupID, edit.Now); err != nil {
				return edited, err
			}
			edited.RuntimeSynced = false
			edited.RuntimeSyncState = subscription.RuntimeSyncNotRunning
		}
		return edited, nil
	}

	var updated subscription.Result
	workerOpts := workerOptions(options)
	workerOpts.ProxyURL = edit.ProxyURL
	workerOpts.FallbackDirect = edit.FallbackDirect
	workerOpts.PersistedBeforeUpdate = true
	if edited.RequiresUpdate {
		updated, err = worker.UpdateGroup(ctx, workerOpts, groupID, edit.Now, nil)
	} else {
		updated, err = worker.SyncEditedGroup(ctx, workerOpts, groupID, edit.Now, nil)
	}
	if err != nil {
		// 编辑设置已经在调用 Worker 前提交；更新失败只保留旧 Provider，不能把设置伪装成未保存。
		if updated.Persisted {
			edited.NodeCount = updated.NodeCount
			if updated.Revision != 0 {
				edited.Revision = updated.Revision
			}
			edited.StructureChanged = updated.StructureChanged
			edited.NotModified = updated.NotModified
			edited.RuntimeSynced = updated.RuntimeSynced
			edited.RuntimeSyncState = updated.RuntimeSyncState
			edited.RuntimeSyncPending = updated.RuntimeSyncPending
		}
		edited.Persisted = true
		return edited, err
	}
	edited.NodeCount = updated.NodeCount
	if updated.Revision != 0 {
		edited.Revision = updated.Revision
	}
	edited.StructureChanged = updated.StructureChanged
	edited.NotModified = updated.NotModified
	edited.Persisted = updated.Persisted
	edited.RuntimeSynced = updated.RuntimeSynced
	edited.RuntimeSyncState = updated.RuntimeSyncState
	edited.RuntimeSyncPending = updated.RuntimeSyncPending
	return edited, err
}

// UpdateAllSubscriptions 按 Catalog 顺序更新全部订阅。
func UpdateAllSubscriptions(ctx context.Context, options Options) (result worker.Summary, err error) {
	defer func() {
		logOperation(options, "subscription", "subscription.update-all", "全部订阅更新", false, err)
	}()
	ids, err := catalog.GroupIDs(ctx, options.CatalogRoot, "subscription")
	if err != nil {
		return worker.Summary{}, err
	}
	summary := worker.Summary{Updated: []string{}, Failed: []string{}}
	var firstUpdateErr error
	for _, id := range ids {
		if _, updateErr := worker.UpdateGroup(ctx, workerOptions(options), id, time.Now(), nil); updateErr != nil {
			summary.Failed = append(summary.Failed, id)
			if firstUpdateErr == nil {
				firstUpdateErr = updateErr
			}
		} else {
			summary.Updated = append(summary.Updated, id)
		}
	}
	return summary, firstUpdateErr
}

func workerOptions(options Options) worker.Options {
	workerOptions := worker.Options{
		Root:                options.CatalogRoot,
		ProgressDir:         options.ProgressDir,
		TransferContext:     options.TransferContext,
		PIDFile:             options.WorkerPIDFile,
		LogFile:             options.WorkerLogFile,
		ModuleConfig:        options.ModuleConfig,
		APIConfig:           paths.New(options.ModuleDir).APIConfig(),
		SingBoxPath:         options.SingBoxPath,
		ServiceAddress:      options.ServiceAddress,
		ServiceSecret:       options.ServiceSecret,
		NetworkWatchEnabled: true,
		SyncCatalog: func(ctx context.Context, groupID string, structureChanged bool) (string, bool, error) {
			return SyncCatalog(ctx, options, groupID, structureChanged)
		},
		Now: time.Now,
		NetworkEvaluate: func(ctx context.Context, networkType, ssid string, networkChanged bool) error {
			_, err := EvaluateNetwork(ctx, options, networkType, ssid, networkChanged)
			return err
		},
	}
	if options.Telemetry != nil {
		workerOptions.Telemetry = options.Telemetry
		workerOptions.CoreRunning = func() bool {
			state, err := ReadServiceState(options.StateFile)
			return err == nil && state.State == "ready" && state.PID > 0 && service.FindProcess(options.SingBoxPath, int(state.PID)) == int(state.PID)
		}
	}
	return workerOptions
}

// RecordActivity 仅记录公共客户端的活跃；停服入口不能为统计重新启动 Worker。
func RecordActivity(ctx context.Context, options Options, startWorker bool) {
	if options.Telemetry == nil || ctx.Err() != nil {
		return
	}
	if added, err := options.Telemetry.RecordActive(time.Now()); added && err == nil {
		options.Telemetry.Notify()
		if startWorker {
			_ = EnsureWorker(ctx, options)
		}
	}
}

func hostName(rawURL string) string {
	parsed, err := url.Parse(rawURL)
	if err != nil {
		return ""
	}
	return parsed.Hostname()
}

// LoadAppPolicy 读取分应用代理设置。
func LoadAppPolicy(configPath string) (AppPolicy, error) {
	config, err := inbound.Load(configPath)
	if err != nil {
		return AppPolicy{}, err
	}
	return appPolicy(config), nil
}
