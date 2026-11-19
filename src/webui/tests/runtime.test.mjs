import { test } from 'node:test'
import assert from 'node:assert/strict'
import { mkdtempSync, mkdirSync, writeFileSync, rmSync, readFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { checkBuild } from '../scripts/check-build.mjs'
import { decodeCtlResult } from '../src/contract.ts'
import { createPoller } from '../src/polling.ts'
import { complete, replaceCompletion } from '../src/autocomplete.ts'
import { parseCommandLine } from '../src/command.ts'
import { formatCtlOutput } from '../src/format.ts'
import { mockCtl } from '../src/mock.ts'
import { COMMANDS } from '../src/commands.ts'

test('HTTP API 帮助、补全与预览不伪造可用服务或真实凭据', () => {
  assert.deepEqual(complete('api st').candidates, ['start', 'status'])
  assert.match(COMMANDS.api.help, /127\.0\.0\.1:9091/)
  assert.equal(decodeCtlResult(mockCtl(['api', 'access'])).ok, false)
  assert.equal(decodeCtlResult(mockCtl(['api', 'start'])).ok, false)
  assert.equal(decodeCtlResult(mockCtl(['api', 'status'])).data.state, 'unavailable')
})

test('构建检查拒绝缺失或空的页面资源及运行时角色帧', t => {
  const root = mkdtempSync(join(tmpdir(), 'netproxy-webui-'))
  t.after(() => rmSync(root, { recursive: true, force: true }))
  writeFileSync(join(root, 'index.html'), '<script src="./main.js"></script><link href="https://mui.kernelsu.org/internal/insets.css">')
  assert.throws(() => checkBuild(root), /ENOENT/)
  writeFileSync(join(root, 'main.js'), 'const frame="./mascots/dragon/sleep-a.svg"')
  assert.throws(() => checkBuild(root), /ENOENT/)
  mkdirSync(join(root, 'mascots/dragon'), { recursive: true })
  writeFileSync(join(root, 'mascots/dragon/sleep-a.svg'), '')
  assert.throws(() => checkBuild(root), /资源无效/)
  writeFileSync(join(root, 'mascots/dragon/sleep-a.svg'), '<svg/>')
  assert.doesNotThrow(() => checkBuild(root))
})

test('补全使用当前 Catalog，候选替换保留空格、引号与反斜杠', () => {
  const groups = ['default', 'Kitty Network', 'Kitty "Lab"\\Node']
  assert.deepEqual(complete('catalog show ', groups).candidates, groups)
  assert.deepEqual(complete('sub show ', groups, ['sub-id']).candidates, ['sub-id'])
  assert.deepEqual(complete('catalog show "Kitty ', groups).candidates, groups.slice(1))
  assert.deepEqual(complete('service\tst').candidates, ['status', 'start', 'stop'])
  assert.deepEqual(complete('! echo hi', groups).candidates, [])
  for (const source of ['catalog show ', 'catalog show "Ki', 'catalog show\t']) {
    for (const group of groups) {
      assert.deepEqual(parseCommandLine(replaceCompletion(source, group)), ['catalog', 'show', group])
    }
  }
  assert.equal(complete('node use auto def', groups).completed, 'node use auto default ')
})

test('格式化只改变 JSON 排版，文本、错误和危险字符保持原样', () => {
  const raw = '{"schema":1,"message":"<script>alert(1)</script>","data":{"name":"本地配置"}}'
  assert.deepEqual(JSON.parse(formatCtlOutput(raw)), JSON.parse(raw))
  assert.equal(formatCtlOutput('shell output\n'), 'shell output\n')
  assert.equal(formatCtlOutput('not { json'), 'not { json')
  const longLines = `${'long command 中文 '.repeat(40)}\n\t${'x'.repeat(400)}\n\nend\n`
  assert.equal(formatCtlOutput(longLines), longLines)
})

test('终端只在命令结果内横向滚动，标题和状态不随输出移动', () => {
  const style = readFileSync(new URL('../src/style.css', import.meta.url), 'utf8')
  assert.match(style, /\.out pre \{[^}]*white-space: pre;/)
  assert.doesNotMatch(style, /\.out(?: pre)? \{[^}]*(?:pre-wrap|break-all|anywhere)/)
  assert.match(style, /\.out \{[^}]*overflow-x: hidden;[^}]*overflow-y: auto;[^}]*overscroll-behavior: contain;/)
  assert.match(style, /\.entry-output \{[^}]*overflow-x: auto;[^}]*overscroll-behavior-x: contain;/)
  assert.match(style, /\.entry-command \{[^}]*min-width: 0;[^}]*overflow: hidden;[^}]*text-overflow: ellipsis;/)
  assert.doesNotMatch(style, /\.entry(?:-heading)? \{[^}]*width: max-content;/)
  assert.match(style, /\.transcript \{[^}]*min-width: 0;/)

  const page = readFileSync(new URL('../index.html', import.meta.url), 'utf8')
  assert.match(page, /<div class="entry-output" role="region" aria-label="命令输出" tabindex="0"><\/div>/)
  assert.match(page, /<\/section>\s*<div id="mascot"/)
  assert.match(page, /<\/div>\s*<form id="command-form"/)

  const main = readFileSync(new URL('../src/main.ts', import.meta.url), 'utf8')
  assert.match(main, /textContent: '❯ ' \+ command, title: command/)
  assert.match(main, /append\(resultOutput, kind, out\)/)
  assert.match(main, /append\(resultOutput, 'e', err\)/)
  assert.match(main, /append\(resultOutput, 'e', resultText\)/)
})

test('节点选择运行时失败保留持久化状态与原始错误', () => {
  const response = { schema: 1, ok: false, code: 'node.runtime_sync_failed', message: '节点选择已保存，但运行时切换失败', data: { persisted: true, runtime_synced: false, group_id: 'default', mode: 'manual', selected: '本地配置/NODE' } }
  const output = { out: JSON.stringify(response), err: '', code: 1 }
  assert.deepEqual(decodeCtlResult(output), response)
  assert.deepEqual(JSON.parse(formatCtlOutput(output.out)), response)
})

test('模式补全仅使用配置列表，保留自定义名称和空格', () => {
  const modes = ['Rule', 'Proxy', 'Direct', 'RuleAllowAds', 'Office Network']
  assert.deepEqual(complete('mode ', [], [], modes).candidates, modes)
  assert.deepEqual(complete('mode ').candidates, [])
  assert.equal(complete('mode Off', [], [], modes).completed, 'mode "Office Network" ')
  assert.equal(complete('mode Pro', [], [], modes).completed, 'mode Proxy ')
  assert.equal(complete('mode RuleA', [], [], modes).completed, 'mode RuleAllowAds ')
  const run = (...args) => decodeCtlResult(mockCtl(args))
  for (const mode of ['proxy', 'Global', 'AllowAds']) assert.equal(run('mode', mode).ok, false)
  for (const mode of ['Proxy', 'RuleAllowAds']) assert.equal(run('mode', mode).data.mode, mode)
  assert.deepEqual(run('mode').data.available, ['Proxy', 'RuleAllowAds', 'Rule', 'Direct'])
  assert.deepEqual(run('mode').data.available, run('service', 'status').data.available_outbound_modes)
  assert.match(COMMANDS.mode.help, /Proxy（代理）.*RuleAllowAds/)
  run('mode', 'Rule')
})

test('入站帮助与补全只使用公共配置目标，诊断仍保留 ebpf status', () => {
  const targets = ['inbound', 'inbound/backend', 'inbound/root_policy', 'inbound/app', 'inbound/ebpf', 'inbound/tun']
  for (const action of ['read', 'apply', 'validate']) {
    assert.deepEqual(complete(`config ${action} in`).candidates, targets)
    assert.ok(!complete(`config ${action} `).candidates.includes('ebpf'))
    assert.deepEqual(complete(`config ${action} ebpf`).candidates, [])
  }
  assert.deepEqual(complete('config read runtime/in').candidates, ['runtime/inbound.json'])
  assert.deepEqual(complete('config apply runtime/').candidates, [])
  assert.deepEqual(complete('ebpf ').candidates, ['status'])
  assert.deepEqual(complete('tun ').candidates, [])
  assert.match(COMMANDS.config.help, /不能用 \{\} 删除/)
  assert.match(COMMANDS.service.help, /active_backend.*ready.*PID\/API/)
})

test('模块 JSON 帮助、补全和 mock 使用完整目标与独立分区', () => {
  const run = (...args) => decodeCtlResult(mockCtl(args))
  const defaults = JSON.parse(readFileSync(new URL('../../module/config/module.json', import.meta.url)))
  const targets = ['module', 'module/wifi', 'module/auto_start']
  for (const action of ['read', 'apply', 'validate']) {
    assert.deepEqual(complete(`config ${action} module`).candidates, targets)
    assert.deepEqual(complete(`config ${action} module/selection`).candidates, [])
  }
  assert.match(COMMANDS.config.help, /module\.json/)
  assert.match(COMMANDS.config.help, /module\/wifi、module\/auto_start/)
  assert.match(COMMANDS.config.help, /revision 独立.*selection/)
  const full = run('config', 'read', 'module').data
  assert.deepEqual(JSON.parse(full.content), defaults)
  const revisions = [full.revision]
  for (const section of ['wifi', 'auto_start']) {
    const fragment = run('config', 'read', `module/${section}`).data
    assert.deepEqual(JSON.parse(fragment.content), { [section]: defaults[section] })
    revisions.push(fragment.revision)
  }
  assert.equal(new Set(revisions).size, revisions.length)
  assert.equal(run('config', 'read', 'module/selection').ok, false)
})

test('mock 同步单文件模板、分区与真实后端状态边界', () => {
  const run = (...args) => decodeCtlResult(mockCtl(args))
  const defaults = JSON.parse(readFileSync(new URL('../../module/config/inbound/inbound.json', import.meta.url)))
  run('service', 'stop')
  assert.equal(run('service', 'status').data.configured_backend, 'ebpf')
  assert.equal(run('service', 'status').data.active_backend, null)
  assert.deepEqual(JSON.parse(run('config', 'read', 'inbound').data.content), defaults)
  assert.deepEqual(run('config', 'list').data.filter(item => item.category === 'inbound').map(item => item.id), [
    'inbound', 'inbound/backend', 'inbound/root_policy', 'inbound/app', 'inbound/ebpf', 'inbound/tun',
  ])
  for (const section of ['backend', 'root_policy', 'app', 'ebpf', 'tun']) {
    const result = run('config', 'read', `inbound/${section}`)
    assert.deepEqual(JSON.parse(result.data.content), { [section]: defaults[section] })
    assert.ok(result.data.revision)
  }
  assert.deepEqual(run('app', 'list').data, { enabled: false, mode: 'blacklist', proxy_apps: '', bypass_apps: '' })
  assert.equal(run('config', 'read', 'ebpf').ok, false)
  assert.equal(run('config', 'read', 'runtime/ebpf.json').ok, false)
  assert.equal(run('tun', 'status').ok, false)
  assert.equal(run('ebpf', 'status').ok, true)
  assert.equal(run('config', 'read', 'runtime/inbound.json').ok, false)
  run('service', 'start')
  assert.equal(run('service', 'status').data.active_backend, 'ebpf')
  assert.equal(run('service', 'status').data.pid, 4242)
  const runtime = JSON.parse(run('config', 'read', 'runtime/inbound.json').data.content)
  assert.equal(runtime.inbounds.length, 1)
  assert.equal(runtime.inbounds[0].tag, 'netproxy-in')
  assert.deepEqual(runtime.inbounds[0].shared, { enabled: false })
  assert.deepEqual(run('config', 'list').data.filter(item => item.category === 'runtime').map(item => [item.id, item.editable]), [
    ['runtime/inbound.json', false], ['runtime/providers.json', false], ['runtime/outbounds.json', false],
  ])
  run('service', 'stop')
  assert.equal(run('service', 'status').data.active_backend, null)
  assert.equal(run('config', 'read', 'runtime/inbound.json').ok, true)
})

test('eBPF mock 区分可读预检与显式原始报告', () => {
  const defaults = JSON.parse(readFileSync(new URL('../../module/config/inbound/inbound.json', import.meta.url))).ebpf
  const readable = decodeCtlResult(mockCtl(['ebpf', 'status'])).data
  assert.equal(readable.raw, false)
  assert.match(readable.content, /能力预检通过/)
  assert.match(readable.content, /尚未验证实际挂载/)
  assert.equal(readable.report.result, 'preflight_passed')
  assert.equal(readable.report.preflight, true)
  assert.equal(readable.report.exact_object_load, true)
  assert.equal(readable.report.local_data_plane, defaults.local.data_plane)
  assert.equal(Object.hasOwn(readable.report, 'shared_data_plane'), false)
  assert.equal(Object.hasOwn(readable.report, 'active_programs'), false)
  for (const args of [['--raw'], ['shared', '--raw'], ['--raw', 'all']]) {
    const raw = decodeCtlResult(mockCtl(['ebpf', 'status', ...args])).data
    assert.equal(raw.raw, true)
    assert.deepEqual(JSON.parse(raw.content), raw.report)
    assert.ok(['configured', 'shared', 'all'].includes(raw.mode))
    assert.equal(raw.report.local_data_plane, raw.report.mode === 'shared' ? undefined : defaults.local.data_plane)
    assert.equal(raw.report.shared_data_plane, raw.report.mode === 'local' ? undefined : defaults.shared.data_plane)
  }
})

test('JSON 与进程退出状态必须同时成功，结构化失败保留', () => {
  const success = { schema: 1, ok: true, code: 'service.status', message: '服务状态', data: { state: 'ready' } }
  assert.deepEqual(decodeCtlResult({ out: JSON.stringify(success), err: '', code: 0 }), success)
  assert.equal(decodeCtlResult({ out: JSON.stringify(success), err: 'terminated', code: 1 }).ok, false)
  const failure = { ...success, ok: false, code: 'subscription.runtime_sync_failed', message: '运行时同步失败' }
  assert.deepEqual(decodeCtlResult({ out: JSON.stringify(failure), err: 'extra', code: 1 }), failure)
  assert.equal(decodeCtlResult({ out: '{"schema":2}', err: '', code: 0 }).code, 'transport.invalid_json')
  assert.equal(decodeCtlResult({ out: '', err: 'denied', code: 1 }).message, 'denied')
})

test('慢请求不重叠，隐藏时暂停，过期响应不可覆盖当前状态', async t => {
  t.mock.timers.enable({ apis: ['setTimeout'] })
  const pending = []
  const results = []
  const poller = createPoller(() => new Promise(resolve => pending.push(resolve)), value => results.push(value))
  const settle = async () => { for (let i = 0; i < 5; i++) await Promise.resolve() }
  poller.setActive(true)
  t.mock.timers.tick(30_000)
  assert.equal(pending.length, 1)
  poller.setActive(false)
  poller.setActive(true)
  assert.equal(pending.length, 1)
  pending.shift()('expired')
  await settle()
  assert.deepEqual(results, [])
  assert.equal(pending.length, 1)
  pending.shift()('ready')
  await settle()
  assert.deepEqual(results, ['ready'])
  t.mock.timers.tick(4999)
  assert.equal(pending.length, 0)
  t.mock.timers.tick(1)
  assert.equal(pending.length, 1)
  poller.refresh()
  poller.refresh()
  pending.shift()('before-command')
  await settle()
  assert.deepEqual(results, ['ready'])
  assert.equal(pending.length, 1)
  poller.setActive(false)
  pending.shift()('hidden')
  await settle()
  t.mock.timers.tick(30_000)
  assert.equal(pending.length, 0)
})

test('链式代理与智能分组的帮助、补全使用 netproxyctl 子命令', () => {
  const groups = ['default', 'demo-sub']
  assert.deepEqual(complete('ch').candidates, ['chain'])
  assert.deepEqual(complete('chain ').candidates, ['status', 'mode', 'landing', 'front', 'policy'])
  assert.deepEqual(complete('chain mode ').candidates, ['off', 'manual', 'policy', 'manual_policy'])
  assert.deepEqual(complete('chain landing r').candidates, ['rename', 'remove'])
  assert.deepEqual(complete('chain front ', groups).candidates, ['follow', 'smart', ...groups])
  assert.deepEqual(complete('chain landing import a.txt ').candidates, ['--type'])
  assert.deepEqual(complete('chain landing import a.txt --type ').candidates, ['socks', 'http'])
  assert.deepEqual(complete('chain policy ').candidates, ['list', 'enable', 'disable', 'order'])
  assert.deepEqual(complete('smart ').candidates, ['list', 'generate', 'use', 'select', 'test'])
  assert.deepEqual(complete('smart select smart-hk ', groups).candidates, ['auto', ...groups])
  assert.deepEqual(complete('smart generate ').candidates, ['--english'])
  assert.match(COMMANDS.chain.help, /off（关闭）.*manual_policy/)
  assert.match(COMMANDS.smart.help, /smart select <分组ID> auto/)
})

test('链式代理 mock 与模块接口字段、错误码一致', () => {
  const run = (...args) => decodeCtlResult(mockCtl(args))
  run('service', 'stop')
  const initial = run('chain', 'status')
  assert.equal(initial.code, 'chain.current')
  assert.deepEqual(Object.keys(initial.data), ['mode', 'manual', 'landings', 'policies', 'running', 'front', 'landing', 'delays'])
  assert.equal(initial.data.landings[0].has_password, true)
  assert.equal(run('chain', 'mode', 'manual').code, 'chain.invalid')
  assert.equal(run('chain', 'mode', 'bogus').code, 'chain.invalid')
  assert.equal(run('chain', 'mode', 'manual', '示例后置').data.mode, 'manual')
  assert.equal(run('service', 'status').data.chain_mode, 'manual')
  assert.equal(run('service', 'status').data.chain_landing, '示例后置')
  assert.equal(run('chain', 'landing', 'test').code, 'chain.service_stopped')
  const imported = run('chain', 'landing', 'import', 'landing.txt', '--type', 'http')
  assert.equal(imported.code, 'chain.landings_imported')
  const added = imported.data.import.added[0]
  assert.equal(imported.data.chain.landings.at(-1).type, 'http')
  assert.equal(Object.hasOwn(imported.data.chain.landings.at(-1), 'username'), false)
  assert.equal(run('chain', 'landing', 'remove', '示例后置').code, 'chain.invalid')
  assert.equal(run('chain', 'landing', 'rename', added, '新后置').data.landings.at(-1).tag, '新后置')
  assert.equal(run('chain', 'landing', 'use', '新后置').data.manual, '新后置')
  run('service', 'start')
  const tested = run('chain', 'landing', 'test')
  assert.equal(tested.code, 'chain.delay')
  assert.equal(tested.data.running, true)
  assert.equal(typeof tested.data.front_delay, 'number')
  assert.ok(tested.data.delays['新后置'] > 0)
  assert.equal(run('chain', 'front', 'default/demo-node').data.front_setting.node, 'demo-node')
  assert.equal(run('chain', 'front', 'missing').code, 'chain.invalid')
  assert.equal(Object.hasOwn(run('chain', 'front', 'follow').data, 'front_setting'), false)
  assert.deepEqual(run('chain', 'policy', 'list').data.policies[0].packages, ['com.example.app'])
  assert.equal(run('chain', 'policy', 'enable', 'policy-demo').data.policies[0].enabled, true)
  assert.equal(run('chain', 'policy', 'order', 'policy-demo').code, 'chain.policies_ordered')
  assert.equal(run('chain', 'policy', 'order', 'missing').code, 'chain.invalid')
  run('chain', 'mode', 'off')
  assert.equal(run('chain', 'landing', 'remove', '新后置').code, 'chain.landing_removed')
  run('service', 'stop')
})

test('智能分组 mock 覆盖生成、选择、组内节点与测速', () => {
  const run = (...args) => decodeCtlResult(mockCtl(args))
  run('service', 'stop')
  assert.deepEqual(run('smart', 'list').data, { groups: [], running: false })
  assert.equal(run('smart', 'use', 'smart-other').code, 'smart.not_found')
  const generated = run('smart', 'generate')
  assert.equal(generated.code, 'smart.generated')
  assert.deepEqual(generated.data.summary, { regions: 1, nodes: 1, unclassified: 1, informational: 0 })
  assert.equal(generated.data.smart.groups[0].id, 'smart-other')
  assert.equal(run('smart', 'test').code, 'smart.service_stopped')
  const used = run('smart', 'use', 'smart-other')
  assert.deepEqual(used.data, { smart_group_id: 'smart-other', mode: 'smart', selected: 'Smart/🌐 其他' })
  assert.equal(run('smart', 'list').data.active, 'smart-other')
  const status = run('service', 'status').data
  assert.equal(status.selector_mode, 'smart')
  assert.equal(status.smart_group_id, 'smart-other')
  assert.equal(run('node', 'current').data.smart_group_name, '🌐 其他')
  assert.equal(run('smart', 'select', 'smart-other', 'default/missing').code, 'smart.invalid')
  assert.equal(run('smart', 'select', 'smart-other', 'default/demo-node').code, 'node.selected')
  assert.deepEqual(run('smart', 'list').data.groups[0].manual, { group_id: 'default', node: 'demo-node' })
  run('smart', 'select', 'smart-other', 'auto')
  assert.equal(Object.hasOwn(run('smart', 'list').data.groups[0], 'manual'), false)
  run('service', 'start')
  const tested = run('smart', 'test', 'smart-other')
  assert.equal(tested.code, 'smart.delay')
  assert.equal(tested.data.groups[0].selected, '本地配置/demo-node')
  assert.ok(tested.data.groups[0].delay > 0)
  assert.equal(run('chain', 'front', 'smart', 'smart-other').data.front_setting.smart_id, 'smart-other')
  run('chain', 'front', 'follow')
  run('node', 'use', 'auto', 'default')
  assert.equal(Object.hasOwn(run('service', 'status').data, 'smart_group_id'), false)
  run('service', 'stop')
})
