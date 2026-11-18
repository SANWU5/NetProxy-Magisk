import { CONTRACT_SCHEMA, type CtlResult, type ExecResult, type InboundBackend, type ServiceBackendStatus } from './contract.ts'

const GROUP_DEFAULTS = {
  auto_update: false,
  update_interval: 0,
  update_via_proxy: 'auto',
  usage: null,
  profile_title: '',
  profile_web_page_url: '',
  last_attempt_at: '',
  last_success_at: '',
  next_update_at: '',
  last_error: '',
  updated_at: '',
  progress: null,
}

const GROUPS = [
  { ...GROUP_DEFAULTS, id: 'default', name: '本地配置', runtime_tag: '本地配置', type: 'local' as const, active: true, node_count: 1, revision: 1 },
  { ...GROUP_DEFAULTS, id: 'demo-sub', name: '示例订阅', runtime_tag: '示例订阅', type: 'subscription' as const, active: false, node_count: 2, revision: 3, auto_update: true, update_interval: 86400 },
]

const NODE = { tag: 'demo-node', protocol: 'socks', server: 'example.test', port: 1080 }
let serviceState = 'stopped'
const availableModes = ['Proxy', 'RuleAllowAds', 'Rule', 'Direct']
let outboundMode = 'Rule'
let runtimePrepared = false

// 链式代理与智能分组的字段名与 internal/module 的 ChainView、SmartGroupsView 保持一致。
const CHAIN_MODES = ['off', 'manual', 'policy', 'manual_policy']
const RUNTIME_NODE = '本地配置/demo-node'
type NodeRef = { group_id: string; node: string }
type ChainFront = { group_id?: string; node?: string; smart_id?: string }
type Landing = { tag: string; type: string; server: string; port: number; username?: string; has_password: boolean; tls: boolean }
const chain = {
  mode: 'off',
  manual: '',
  front: undefined as ChainFront | undefined,
  landings: [{ tag: '示例后置', type: 'socks', server: 'landing.example.test', port: 1080, username: 'demo', has_password: true, tls: false }] as Landing[],
  policies: [{ id: 'policy-demo', name: '示例策略', enabled: false, landing: '示例后置', packages: ['com.example.app'] }],
  delays: {} as Record<string, number>,
}
type SmartGroup = { id: string; name: string; kind: string; region?: string; members: NodeRef[]; manual?: NodeRef }
const smart = { groups: [] as SmartGroup[], generatedAt: '', active: '', delays: {} as Record<string, number> }

const MODULE_CONFIG = {
  auto_start: false,
  logging: false,
  selection: { group_id: 'default', node_tag: '' },
  wifi: { enabled: false, mode: 'blacklist', blacklist: [] as string[], whitelist: [] as string[], proxy_on_non_wifi: true },
}

const INBOUND_CONFIG = {
  backend: 'ebpf' as InboundBackend,
  root_policy: 'include',
  app: { enabled: false, mode: 'blacklist', proxy_apps: [] as string[], bypass_apps: [] as string[] },
  ebpf: {
    type: 'ebpf', tag: 'netproxy-in', network: ['tcp', 'udp'], udp_timeout: '5m', tc_priority: 1,
    local: {
      enabled: true, data_plane: 'cgroup', dns_mode: 'respect_policy', ipv6: true,
      bypass_private_address: true, bypass_rule_set: ['geoip/cn'],
    },
    shared: {
      enabled: false, data_plane: 'socket_assign', dns_mode: 'hijack', interface: ['wlan2'],
      ipv6: true, bypass_private_address: true, bypass_rule_set: ['geoip/cn'],
    },
  },
  tun: {
    type: 'tun', tag: 'netproxy-in', interface_name: 'netproxy',
    address: ['172.19.0.1/30', 'fdfe:dcba:9876::1/126'],
    auto_route: true, auto_redirect: true, dns_mode: 'hijack',
  },
}

const STATIC_CONFIG: Record<string, unknown> = {
  log: { level: 'info' },
  dns: { final: 'dns-proxy' },
  inbounds: [],
  route: { final: 'Proxy' },
  experimental: {},
  http_clients: [],
  services: [],
}

const MODULE_DOCUMENTS = [
  { id: 'module', filename: 'module.json', category: 'module', editable: true },
  ...['wifi', 'auto_start', 'logging'].map(section => ({
    id: `module/${section}`, filename: section, category: 'module', editable: true, section,
  })),
]

const CONFIG_DOCUMENTS = [
  { id: 'inbound', filename: 'inbound.json', category: 'inbound', editable: true },
  ...['backend', 'root_policy', 'app', 'ebpf', 'tun'].map(section => ({
    id: `inbound/${section}`, filename: section, category: 'inbound', editable: true, section,
  })),
  { id: 'singbox/config.json', filename: 'config.json', category: 'config', editable: true },
  ...[...Object.keys(STATIC_CONFIG), 'outbounds'].map(section => ({
    id: `singbox/${section}`, filename: section, category: 'config', editable: true, section,
  })),
  ...['inbound.json', 'providers.json', 'outbounds.json'].map(filename => ({
    id: `runtime/${filename}`, filename, category: 'runtime', editable: false,
  })),
]

function response<T>(code: string, message: string, data?: T): CtlResult<T> {
  return { schema: CONTRACT_SCHEMA, ok: true, code, message, ...(data === undefined ? {} : { data }) }
}

function failure(code: string, message: string): CtlResult<Record<string, never>> {
  return { schema: CONTRACT_SCHEMA, ok: false, code, message, data: {} }
}

function serviceStatus() {
  const backendStatus: ServiceBackendStatus = {
    configured_backend: INBOUND_CONFIG.backend,
    active_backend: serviceState === 'ready' ? INBOUND_CONFIG.backend : null,
  }
  return {
    state: serviceState,
    pid: serviceState === 'ready' ? 4242 : null,
    started_at: serviceState === 'ready' ? 1_700_000_000 : 0,
    ready_at: serviceState === 'ready' ? 1_700_000_005 : 0,
    uptime_seconds: serviceState === 'ready' ? 120 : 0,
    error: '',
    outbound_mode: outboundMode,
    configured_outbound_mode: outboundMode,
    available_outbound_modes: availableModes,
    ...backendStatus,
    selector_mode: activeSmart() ? 'smart' : 'urltest',
    active_group_id: 'default',
    active_group_name: '本地配置',
    active_group_runtime_tag: '本地配置',
    active_group_node_count: 1,
    selected_node_ref: '',
    runtime_selected: serviceState === 'ready' ? smartSelected(activeSmart()) : '',
    ...smartSelection(),
    chain_mode: chain.mode,
    chain_landing: chain.manual,
    memory_bytes: 0,
    process_cpu_ticks: 0,
    system_cpu_ticks: 0,
    cpu_count: 1,
    connections_in: 0,
    connections_out: 0,
    upload_total: 0,
    download_total: 0,
    worker_state: 'stopped',
    worker_pid: null,
  }
}

function snapshot() {
  return { group: GROUPS[0], nodes: [NODE] }
}

function selection() {
  return {
    active_group_id: 'default',
    active_group_name: '本地配置',
    active_group_runtime_tag: '本地配置',
    active_group_node_count: 1,
    selector_mode: activeSmart() ? 'smart' : 'urltest',
    selected_node_ref: '',
    selected: activeSmart() ? `Smart/${activeSmart()!.name}` : 'Auto/本地配置',
    runtime_selected: serviceState === 'ready' ? smartSelected(activeSmart()) : '',
    ...smartSelection(),
  }
}

function activeSmart(): SmartGroup | undefined {
  return smart.groups.find(group => group.id === smart.active)
}

function smartSelection() {
  const group = activeSmart()
  return group ? { smart_group_id: group.id, smart_group_name: group.name } : {}
}

// 运行时实际节点形如「分组运行时标签/节点」；mock 只有一个本地节点。
function smartSelected(group?: SmartGroup): string {
  return group?.manual ? `本地配置/${group.manual.node}` : RUNTIME_NODE
}

function chainView() {
  const running = serviceState === 'ready' && chain.landings.length > 0
  return {
    mode: chain.mode,
    manual: chain.manual,
    ...(chain.front ? { front_setting: chain.front } : {}),
    landings: chain.landings,
    policies: chain.policies,
    running,
    front: running ? RUNTIME_NODE : '',
    landing: running ? chain.manual || chain.landings[0].tag : '',
    delays: chain.delays,
  }
}

function smartView() {
  const running = serviceState === 'ready'
  return {
    groups: smart.groups.map(group => ({
      id: group.id, name: group.name, kind: group.kind, ...(group.region ? { region: group.region } : {}),
      members: group.members, available: group.members.length, ...(group.manual ? { manual: group.manual } : {}),
      ...(running ? { selected: smartSelected(group) } : {}),
      ...(running && smart.delays[group.id] ? { delay: smart.delays[group.id] } : {}),
    })),
    ...(smart.generatedAt ? { generated_at: smart.generatedAt } : {}),
    ...(activeSmart() ? { active: smart.active } : {}),
    running,
  }
}

function normalizeArgs(args: string[]): string[] {
  const result: string[] = []
  for (let index = 0; index < args.length; index += 1) {
    const argument = args[index]
    if (argument === '--json') continue
    if (argument === '--timeout') {
      index += 1
      continue
    }
    if (argument.startsWith('--timeout=')) continue
    result.push(argument)
  }
  return result
}

function executeSmart(args: string[]): CtlResult<unknown> {
  const [action = 'list', id, target] = args
  if (action === 'list' && args.length <= 1) return response('smart.list', '智能分组', smartView())
  if (action === 'generate') {
    const english = args.includes('--english')
    if (!smart.groups.some(group => group.id === 'smart-other')) {
      smart.groups.unshift({ id: 'smart-other', name: english ? '🌐 Others' : '🌐 其他', kind: 'region', region: 'other', members: [{ group_id: 'default', node: NODE.tag }] })
    }
    smart.generatedAt = '2026-01-01T00:00:00Z'
    return response('smart.generated', '智能分组已完成', { smart: smartView(), summary: { regions: 1, nodes: 1, unclassified: 1, informational: 0 } })
  }
  const group = smart.groups.find(item => item.id === id)
  if (action === 'test') {
    if (id && !group) return failure('smart.not_found', '分组不存在')
    if (serviceState !== 'ready') return failure('smart.service_stopped', '服务未运行，无法测速')
    for (const item of smart.groups) if (!id || item.id === id) smart.delays[item.id] = 95
    return response('smart.delay', '分组测速完成', smartView())
  }
  if ((action === 'use' || action === 'select') && id && !group) return failure('smart.not_found', '分组不存在')
  if (group && action === 'select' && target) {
    if (target === 'auto') delete group.manual
    else {
      const [group_id, ...rest] = target.split('/')
      const node = rest.join('/')
      if (!group_id || !node) return failure('node.ref_invalid', '节点引用格式应为 <group-id>/<tag>')
      if (!group.members.some(member => member.group_id === group_id && member.node === node)) {
        return failure('smart.invalid', `节点 "${node}" 不在分组 "${group.name}" 中`)
      }
      group.manual = { group_id, node }
    }
  }
  if (group && (action === 'use' && args.length === 2 || action === 'select' && target)) {
    smart.active = group.id
    return response('node.selected', '节点选择已更新', { smart_group_id: group.id, mode: 'smart', selected: `Smart/${group.name}` })
  }
  return failure('usage.invalid', '用法: netproxyctl smart list|generate|use|select|test')
}

function chainInvalid(message: string) {
  return failure('chain.invalid', message)
}

function executeLanding(args: string[]): CtlResult<unknown> {
  const [action = 'list', tag, name] = args
  if (action === 'list') return response('chain.current', '链式代理设置', chainView())
  if (action === 'import' && tag) {
    const typeIndex = args.indexOf('--type')
    const type = typeIndex > 0 ? args[typeIndex + 1] : 'socks'
    if (type !== 'socks' && type !== 'http') return failure('usage.invalid', `默认协议只能是 socks 或 http，当前为 "${type ?? ''}"`)
    const added = `导入后置 ${chain.landings.length + 1}`
    chain.landings.push({ tag: added, type, server: 'imported.example.test', port: 1080, has_password: false, tls: false })
    return response('chain.landings_imported', '后置代理已导入', { chain: chainView(), import: { added: [added], skipped: [] } })
  }
  if (action === 'test') {
    if (!chain.landings.length) return failure('command.failed', '还没有后置代理')
    if (tag && !chain.landings.some(landing => landing.tag === tag)) return failure('command.failed', `后置代理 "${tag}" 不存在`)
    if (serviceState !== 'ready') return failure('chain.service_stopped', '服务未运行，无法经由前置测试后置代理')
    chain.landings.forEach((landing, index) => { if (!tag || landing.tag === tag) chain.delays[landing.tag] = 180 + index * 40 })
    return response('chain.delay', '后置代理测速完成', { ...chainView(), front_delay: 60 })
  }
  const landing = chain.landings.find(item => item.tag === tag)
  if (!landing) return chainInvalid(action === 'use' ? `手动后置 "${tag ?? ''}" 不存在` : `后置代理 "${tag ?? ''}" 不存在`)
  if (action === 'use') {
    chain.manual = landing.tag
    return response('chain.saved', '链式代理设置已保存', chainView())
  }
  if (action === 'rename' && name !== undefined) {
    if (!name.trim()) return chainInvalid('名称不能为空')
    if (name.trim() !== name) return chainInvalid('名称首尾不能有空白')
    if (name !== landing.tag && chain.landings.some(item => item.tag === name)) return chainInvalid(`已存在名为 "${name}" 的后置代理`)
    for (const policy of chain.policies) if (policy.landing === landing.tag) policy.landing = name
    if (chain.manual === landing.tag) chain.manual = name
    landing.tag = name
    return response('chain.landing_renamed', '后置代理已重命名', chainView())
  }
  if (action === 'remove') {
    if (chain.manual === landing.tag && (chain.mode === 'manual' || chain.mode === 'manual_policy')) {
      return chainInvalid(`后置代理 "${landing.tag}" 正在被手动模式使用，请先切换手动后置或模式`)
    }
    const policy = chain.policies.find(item => item.landing === landing.tag)
    if (policy) return chainInvalid(`后置代理 "${landing.tag}" 正在被策略 "${policy.name}" 使用，请先修改该策略`)
    if (chain.manual === landing.tag) chain.manual = ''
    chain.landings.splice(chain.landings.indexOf(landing), 1)
    delete chain.delays[landing.tag]
    if (!chain.landings.length) chain.mode = 'off'
    return response('chain.landing_removed', '后置代理已删除', chainView())
  }
  return failure('usage.invalid', '用法: netproxyctl chain landing list|import|use|rename|remove|test')
}

function executeChain(args: string[]): CtlResult<unknown> {
  const [action = 'status', target, value] = args
  if ((action === 'status' && args.length <= 1) || (action === 'policy' && target === 'list')) {
    return response('chain.current', '链式代理设置', chainView())
  }
  if (action === 'mode' && target) {
    if (!CHAIN_MODES.includes(target)) return chainInvalid(`后置模式只能是 off、manual、policy 或 manual_policy，当前为 "${target}"`)
    const manual = value ?? chain.manual
    if (manual && !chain.landings.some(landing => landing.tag === manual)) return chainInvalid(`手动后置 "${manual}" 不存在`)
    if ((target === 'manual' || target === 'manual_policy') && !manual) return chainInvalid('手动模式需要先选择一个后置代理')
    if (target !== 'off' && !chain.landings.length) return chainInvalid('开启链式代理前需要先导入至少一个后置代理')
    chain.mode = target
    chain.manual = manual
    return response('chain.saved', '链式代理设置已保存', chainView())
  }
  if (action === 'front' && target) {
    if (target === 'follow') chain.front = undefined
    else if (target === 'smart') {
      if (!smart.groups.some(group => group.id === value)) return chainInvalid(`指定的前置不可用: 分组 "${value ?? ''}" 不存在`)
      chain.front = { smart_id: value }
    } else {
      const [group_id, ...rest] = target.split('/')
      const node = rest.join('/')
      if (!GROUPS.some(group => group.id === group_id) || (node && node !== NODE.tag)) return chainInvalid(`指定的前置不可用: ${target}`)
      chain.front = node ? { group_id, node } : { group_id }
    }
    return response('chain.front_saved', '前置已保存', chainView())
  }
  if (action === 'landing') return executeLanding(args.slice(1))
  return failure('usage.invalid', '用法: netproxyctl chain status|mode|landing|front|policy')
}

function execute(args: string[]): CtlResult<unknown> {
  const clean = normalizeArgs(args)
  const command = clean[0]
  const action = clean[1]

  if (command === 'api') {
    if (action === 'access') return failure('api.preview_unavailable', '开发预览不提供真实连接凭据')
    if (action === 'init') return response('api.initialized', '管理接口凭据已初始化', { listen: '127.0.0.1:9091' })
    if (action === 'start') return failure('api.preview_unavailable', '开发预览不会启动 HTTP 管理服务')
    if (action === 'status') return response('api.status', '模块管理接口状态', { state: 'unavailable', listen: '127.0.0.1:9091' })
    return failure('usage.invalid', '用法: api init|start|status|access')
  }

  if (command === 'service') {
    if (action === 'start' || action === 'restart') {
      serviceState = 'ready'
      runtimePrepared = true
    }
    if (action === 'stop') serviceState = 'stopped'
    if (action === 'status') return response('service.status', '服务状态', serviceStatus())
    return response(`service.${action || 'status'}`, '服务操作完成', { action, status: serviceStatus() })
  }

  if (command === 'catalog') {
    if (action === 'list') return response('catalog.groups', 'Catalog 分组快照', GROUPS)
    if (action === 'show') return response('catalog.show', 'Catalog 分组快照', snapshot())
  }

  if (command === 'sub') {
    if (action === 'list') return response('subscription.list', '订阅列表', GROUPS.filter(group => group.type === 'subscription'))
    return response(`subscription.${action || 'list'}`, '订阅操作完成', {})
  }

  if (command === 'node') {
    if (action === 'list') return response('node.list', '节点列表', [snapshot()])
    if (action === 'show') return response('catalog.show', 'Catalog 分组快照', snapshot())
    if (action === 'snapshot') return response('node.snapshot', '节点快照', { groups: [snapshot()], selection: selection() })
    if (action === 'current') return response('node.current', '当前节点选择', selection())
    // 选择普通分组或节点时节点页退出智能分组，与 SelectNode 一致。
    if (action === 'use') smart.active = ''
    return response(`node.${action || 'list'}`, '节点操作完成', {})
  }

  if (command === 'chain') return executeChain(clean.slice(1))
  if (command === 'smart') return executeSmart(clean.slice(1))

  if (command === 'mode') {
    if (!action) return response('mode.current', '当前出站模式', { mode: outboundMode, available: availableModes })
    if (!availableModes.includes(action)) return failure('mode.invalid', '配置中不存在出站模式: ' + action)
    outboundMode = action
    return response('mode.changed', '出站模式已切换', { mode: outboundMode })
  }

  if (command === 'app' && action === 'list') {
    return response('app.list', '分应用代理配置', {
      enabled: INBOUND_CONFIG.app.enabled, mode: INBOUND_CONFIG.app.mode,
      proxy_apps: INBOUND_CONFIG.app.proxy_apps.join(','), bypass_apps: INBOUND_CONFIG.app.bypass_apps.join(','),
    })
  }

  if (command === 'network' && action === 'wifi-list') {
    return response('network.wifi_list', '已读取保存的 Wi-Fi 名称', { ssids: ['Home Wi-Fi', 'Office'] })
  }

  if (command === 'network' && action === 'evaluate') {
    return response('network.evaluated', 'Wi-Fi 自动切换未启用', {
      enabled: false,
      network_type: 'not_wifi',
      target: 'proxying',
      desired_mode: outboundMode,
      runtime_mode: outboundMode,
      changed: false,
      reason: 'Wi-Fi 自动切换未启用',
    })
  }

  if (command === 'ebpf' && action === 'status') {
    const requestedMode = clean.slice(2).find(arg => arg !== '--raw') || 'configured'
    const raw = clean.includes('--raw')
    const reportMode = requestedMode === 'configured' ? 'local' : requestedMode
    const localDataPlane = reportMode === 'local' || reportMode === 'all' ? INBOUND_CONFIG.ebpf.local.data_plane : undefined
    const sharedDataPlane = reportMode === 'shared' || reportMode === 'all' ? INBOUND_CONFIG.ebpf.shared.data_plane : undefined
    const report = {
      platform: 'android',
      kernel_release: 'mock',
      architecture: 'arm64',
      mode: reportMode,
      ...(localDataPlane ? { local_data_plane: localDataPlane } : {}),
      ...(sharedDataPlane ? { shared_data_plane: sharedDataPlane } : {}),
      network: ['tcp', 'udp'],
      ipv6: true,
      findings: [],
      preflight: true,
      exact_object_load: true,
      summary: { pass: 1, warn: 0, fail: 0, unknown: 0, required_failures: 0, required_unknowns: 0, required_issues: 0 },
      result: 'preflight_passed',
    }
    return response('ebpf.status', 'eBPF 能力检查完成', {
      mode: requestedMode,
      raw,
      content: raw ? JSON.stringify(report, null, 2) : '结论: eBPF 能力预检通过\n尚未验证实际挂载与网络接管。',
      report,
    })
  }

  if (command === 'config' && action === 'list') {
    const documents = runtimePrepared ? CONFIG_DOCUMENTS : CONFIG_DOCUMENTS.filter(item => item.category !== 'runtime')
    return response('config.list', '配置列表', documents)
  }
  if (command === 'config' && action === 'read') {
    const document = [...MODULE_DOCUMENTS, ...CONFIG_DOCUMENTS].find(item => item.id === clean[2])
    if (!document) return failure('command.failed', '不支持的配置目标')
    if (document.category === 'runtime' && !runtimePrepared) return failure('command.failed', '尚未生成运行时配置')
    let content: unknown = {}
    if (document.id === 'module') content = MODULE_CONFIG
    else if (document.category === 'module') content = { [document.filename]: MODULE_CONFIG[document.filename as keyof typeof MODULE_CONFIG] }
    else if (document.id === 'inbound') content = INBOUND_CONFIG
    else if (document.category === 'inbound') content = { [document.filename]: INBOUND_CONFIG[document.filename as keyof typeof INBOUND_CONFIG] }
    else if (document.id === 'runtime/inbound.json') content = { inbounds: [{ ...INBOUND_CONFIG.ebpf, shared: { enabled: false } }] }
    else if (document.id === 'runtime/providers.json') content = { providers: [] }
    else if (document.id === 'runtime/outbounds.json') content = { outbounds: [] }
    else if (document.id === 'singbox/config.json') content = STATIC_CONFIG
    else if (Object.prototype.hasOwnProperty.call(STATIC_CONFIG, document.filename)) content = { [document.filename]: STATIC_CONFIG[document.filename] }
    return response('config.read', '配置内容', {
      target: document.id, content: JSON.stringify(content, null, 2), revision: `mock-${document.filename}-1`,
    })
  }

  if (command === 'logs' && action === 'show') {
    return response('logs.show', '日志内容', { kind: clean[2] || 'service', content: '开发预览 mock 日志\n' })
  }

  return failure('usage.invalid', '开发预览不支持此命令')
}

export function mockCtl(args: string[]): ExecResult {
  const result = execute(args)
  return { out: `${JSON.stringify(result)}\n`, err: '', code: result.ok ? 0 : 2 }
}
