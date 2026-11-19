interface CommandSpec {
  overview: string
  actions: readonly string[]
  help: string
}

export const COMMANDS = {
  api: {
    overview: 'api <命令>      模块 HTTP 管理接口',
    actions: ['init', 'start', 'status', 'access'],
    help: `api - 模块 HTTP 管理接口

  api init      初始化独立随机凭据，不覆盖已有配置
  api start     启动或恢复后台管理接口
  api status    查看实际接口就绪状态
  api access    读取地址与凭据，仅供 Root 客户端连接

接口默认仅监听 127.0.0.1:9091，关闭 sing-box 不关闭管理接口。
凭据不可分享或粘贴到公共聊天中。
`,
  },
  service: {
    overview: 'service <命令>  服务控制',
    actions: ['status', 'start', 'stop', 'restart', 'reload', 'check', 'toggle'],
    help: `service - 服务控制

  service status      查看服务状态、运行时间和流量
  service start       启动 sing-box 服务
  service stop        停止 sing-box 服务
  service restart     重启 sing-box 核心
  service reload      重载配置
  service check       检查服务配置
  service toggle      切换服务状态

状态: stopped / preparing / starting / ready / stopping / failed
configured_backend 是保存的入站；active_backend 仅在 ready 且 PID/API 毫秒启动身份一致时非空，否则为 null。
`,
  },
  catalog: {
    overview: 'catalog <命令>  分组查看',
    actions: ['list', 'show'],
    help: `catalog - 节点分组

  catalog list             列出所有节点分组
  catalog show <分组>      查看分组详情
`,
  },
  node: {
    overview: 'node <命令>     节点管理',
    actions: ['list', 'snapshot', 'current', 'show', 'get', 'export', 'delay', 'add', 'import', 'edit', 'remove', 'use'],
    help: `node - 节点管理

  node list [分组]                 列出节点
  node snapshot [分组]             查看运行时节点快照
  node current                     查看当前选择
  node show <分组>                 查看分组节点
  node get <分组>/<tag>            获取节点配置摘要
  node export <分组>/<tag>         导出节点链接
  node delay [目标] [分组]         测量节点延迟
  node delay auto <分组>           测量分组内所有节点
  node add <链接> [分组]           添加单节点链接
  node import <文件>               导入节点文件
  node edit <分组>/<tag> <来源>    编辑节点
  node remove <分组>/<tag>         删除节点
  node use auto [分组]             自动选择最快节点
  node use <分组>/<tag>            手动选择节点
`,
  },
  chain: {
    overview: 'chain <命令>    链式代理',
    actions: ['status', 'mode', 'landing', 'front', 'policy'],
    help: `chain - 链式代理（前置节点 → 后置代理 → 目标）

  chain status                         查看模式、前置、后置与链路延迟
  chain mode <模式> [后置]             切换后置模式，省略后置时保留当前手动后置
  chain landing list                   列出后置代理与最近测速
  chain landing import <文件> [--type socks|http]
                                       导入后置代理，不带协议头的行默认 socks
  chain landing use <后置>             选择手动模式使用的后置
  chain landing rename <后置> <新名称> 重命名后置，同步手动模式与策略引用
  chain landing remove <后置>          删除后置，仍被使用时拒绝
  chain landing test [后置]            经由前置测速，省略时测试全部后置
  chain front follow                   前置跟随节点页的选择
  chain front <分组>                   前置使用分组的自动选择
  chain front <分组>/<tag>             前置固定为指定节点
  chain front smart <智能分组ID>       前置使用智能分组
  chain policy list                    查看策略组（顺序即匹配优先级）
  chain policy enable|disable <ID>     启用或停用策略组
  chain policy order <ID>...           按给出的顺序重排全部策略组

模式: off（关闭）/ manual（手动）/ policy（按应用策略）/ manual_policy（手动 + 策略）
延迟 -1 表示最近一次测速失败；服务运行时修改立即生效。
`,
  },
  smart: {
    overview: 'smart <命令>    智能分组',
    actions: ['list', 'generate', 'use', 'select', 'test'],
    help: `smart - 智能分组与自建分组

  smart list                           列出分组、实际节点、手动选择与延迟
  smart generate [--english]           按地区重新智能分组，保留手动修改与自建分组
  smart use <分组ID>                   节点页改用该分组，沿用分组内选择
  smart select <分组ID> <分组>/<tag>   在分组内手动选择节点
  smart select <分组ID> auto           分组改回自动选择
  smart test [分组ID]                  重新测速，省略时测试全部分组

selected 是分组实际使用的节点，manual 为空表示使用自动选择。
使用 node use 选择普通分组时退出智能分组。
`,
  },
  sub: {
    overview: 'sub <命令>      订阅管理',
    actions: ['list', 'show', 'add', 'edit', 'update', 'update-all', 'activate', 'remove', 'history', 'cancel'],
    help: `sub - 订阅管理

  sub list                         列出订阅
  sub show <名称>                  查看订阅详情
  sub add [名称] <URL>             添加订阅
  sub edit <名称>                  编辑订阅
  sub update <名称>                更新单个订阅
  sub update-all                   更新所有订阅
  sub activate <名称>              激活订阅分组
  sub remove <名称>                删除订阅
  sub history <名称>               查看更新历史
  sub cancel <名称>                取消更新任务
`,
  },
  mode: {
    overview: 'mode <模式>     出站模式',
    actions: [],
    help: `mode - 出站模式

  mode            查看默认模式、实际模式与可选模式
  mode <模式名称> 保存主配置默认模式，运行时应用网络策略

可选模式来自主配置的路由与 DNS 规则，名称与内核一致。
默认配置：Rule（规则）、Proxy（代理）、Direct（直连）、RuleAllowAds（规则，允许广告）。
示例：mode Proxy、mode RuleAllowAds。
`,
  },
  network: {
    overview: 'network <命令>  网络策略评估',
    actions: ['evaluate', 'wifi-list'],
    help: `network - 网络策略

  network evaluate --type <类型> [--ssid <名称>]
                         评估当前网络并应用 Wi-Fi 策略
  network wifi-list       查看已保存的 Wi-Fi 名称
  类型: wifi / not_wifi
`,
  },
  app: {
    overview: 'app <命令>      分应用代理',
    actions: ['list', 'mode', 'add', 'remove', 'enable', 'disable'],
    help: `app - 分应用代理

  app list                    查看应用代理配置
  app mode blacklist          使用黑名单模式
  app mode whitelist          使用白名单模式
  app add <用户ID>:<包名>      添加指定用户的应用
  app remove <用户ID>:<包名>   移除指定用户的应用
  app enable                  启用分应用代理
  app disable                 禁用分应用代理

共用名单保存在 inbound.json 的 app 对象，运行时修改自动应用；只筛选本机应用。
`,
  },
  ebpf: {
    overview: 'ebpf <命令>     eBPF 能力诊断',
    actions: ['status'],
    help: `ebpf - eBPF 能力诊断

  ebpf status                         查看已配置能力
  ebpf status configured              查看当前配置
  ebpf status all                     检查全部支持能力
  ebpf status local                   检查本机数据路径
  ebpf status shared                  检查共享网络数据路径
  ebpf status all --raw               输出原始诊断信息

只诊断 eBPF 能力，不代表实际 active_backend；TUN 通过配置检查、实际启动与日志确认。
`,
  },
  config: {
    overview: 'config <命令>   配置管理',
    actions: ['list', 'read', 'check', 'validate', 'apply'],
    help: `config - 配置管理

  config list                         列出配置文件
  config read <目标>                  读取配置
  config check                        检查全部配置
  config validate <目标> <内容文件>   校验配置
  config apply <目标> <内容文件>      应用配置

  完整配置：module（module.json）、singbox/config.json、inbound
  模块分区：module/wifi、module/auto_start
  模块分区保留顶层字段，例如 {"auto_start":true}、{"wifi":{...}}；不能用 {} 删除。
  模块分区 revision 独立，保存时保留 selection 和其他字段；auto_start 只影响下次开机。
  入站分区：inbound/backend、inbound/root_policy、inbound/app、inbound/ebpf、inbound/tun
  入站分区保留顶层字段，例如 {"backend":"tun"}；不能用 {} 删除。
  核心分区：singbox/dns、singbox/inbounds、singbox/route 等
  核心分区保留顶层字段，例如 {"dns":{...}}；{} 删除该分区。
  只读运行时：runtime/inbound.json、runtime/providers.json、runtime/outbounds.json
  apply/validate 在目标前加 --revision <读取值>，避免覆盖并发修改。
  切换强杀或清理未确认时中止并保留 journal，需要设备重启后再恢复。
`,
  },
  logs: {
    overview: 'logs <命令>     日志管理',
    actions: ['show', 'clear', 'export'],
    help: `logs - 日志管理

  logs show service             查看服务日志
  logs show core                查看 sing-box 日志
  logs clear <类型>             清空日志
  logs export                   导出运行时配置和脱敏日志
`,
  },
} as const satisfies Record<string, CommandSpec>

export type CommandName = keyof typeof COMMANDS
export const COMMAND_NAMES = Object.keys(COMMANDS) as CommandName[]
export const ROOT_COMPLETIONS = [...COMMAND_NAMES, 'help', 'clear', 'exit']
export const HELP_TOPICS = [...COMMAND_NAMES, 'shell']
const CONFIG_TARGETS = [
  'module', 'module/wifi', 'module/auto_start',
  'inbound', 'inbound/backend', 'inbound/root_policy', 'inbound/app', 'inbound/ebpf', 'inbound/tun', 'singbox/config.json',
  'singbox/log', 'singbox/dns', 'singbox/inbounds', 'singbox/outbounds', 'singbox/route',
  'singbox/experimental', 'singbox/http_clients', 'singbox/services',
]

export const VALUE_COMPLETIONS: Record<string, string[]> = {
  'app mode': ['blacklist', 'whitelist'],
  'chain mode': ['off', 'manual', 'policy', 'manual_policy'],
  'chain landing': ['list', 'import', 'use', 'rename', 'remove', 'test'],
  'chain policy': ['list', 'enable', 'disable', 'order'],
  'smart generate': ['--english'],
  'ebpf status': ['configured', 'all', 'local', 'shared', '--raw'],
  'logs show': ['service', 'core'],
  'logs clear': ['service', 'core'],
  'network evaluate': ['--type', '--ssid'],
  'network evaluate --type': ['wifi', 'not_wifi'],
  'config read': [...CONFIG_TARGETS, 'runtime/inbound.json', 'runtime/providers.json', 'runtime/outbounds.json'],
  'config apply': CONFIG_TARGETS,
  'config validate': CONFIG_TARGETS,
}
