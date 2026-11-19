<p align="center">
  <img src="docs/public/N.svg" alt="NetProxy Logo" width="120" />
</p>

<h1 align="center">NetProxy</h1>

<p align="center">
  <strong>System-wide sing-box transparent proxy module for Android</strong><br>
  eBPF / TUN · Nodes & subscriptions · Per-app proxy · Network sharing
</p>

<p align="center">
  <a href="https://github.com/SANWU5/NetProxy-Magisk/releases/tag/nightly">
    <img src="https://img.shields.io/github/v/release/SANWU5/NetProxy-Magisk?include_prereleases&style=flat-square&label=Release&color=blue" alt="Latest Release" />
  </a>
  <a href="https://github.com/Fanju6/NetProxy-Magisk">
    <img src="https://img.shields.io/badge/Fork_of-Fanju6%2FNetProxy--Magisk-lightgrey?style=flat-square" alt="Fork of Fanju6/NetProxy-Magisk" />
  </a>
  <img src="https://img.shields.io/badge/Core-sing--box-blueviolet?style=flat-square" alt="sing-box Core" />
</p>

<p align="center">
  <a href="https://github.com/SANWU5/NetProxy-Magisk/releases/tag/nightly">Download this fork's builds</a> ·
  <a href="docs/guide/chain-proxy.md">Chain proxy</a> ·
  <a href="docs/guide/smart-groups.md">Smart groups</a> ·
  <a href="https://www.netproxy.store/">Official docs</a> ·
  <a href="https://github.com/SANWU5/NetProxy-Magisk/issues">Report issues</a>
</p>

<p align="center">
  <a href="README.md">中文</a> | English
</p>

> [!IMPORTANT]
> **This is an unofficial fork maintained by AiingJie and is not affiliated with the original author.** The original project is [Fanju6/NetProxy-Magisk](https://github.com/Fanju6/NetProxy-Magisk) by Fanju6.
>
> - **Based on**: official 8.6.2, plus chenran0408's 8.6.3–8.6.5, plus this fork's changes since 2026-11. See the [changelog](docs/changelog.md) (Chinese).
> - **Main changes**: global chain proxy (front → landing, per-app policies, link speed test), region-based smart groups, a COUI (ColorOS-style) manager, `chain` and `smart` commands in `netproxyctl` and the WebUI, built-in rule sets that follow their branches and update daily, and builds without telemetry.
> - **Separate signing key**: it cannot be installed over the official release or the other way round; uninstall the other manager first. This fork never updates from official channels.
> - **Support**: please use [this repository's issues](https://github.com/SANWU5/NetProxy-Magisk/issues). Do not report this fork's problems to the original author or the official group.
> - **License**: GPL-3.0, same as the original project; the original copyright notice is in [NOTICE](NOTICE).

---

## Overview

NetProxy is a system-wide transparent proxy module for rooted Android devices, supporting **Magisk, KernelSU, and APatch**. It uses sing-box to intercept traffic through eBPF or TUN + auto_redirect.

Manage everyday tasks with the bundled Android Manager, terminal-style module WebUI, CLI, or sing-box Dashboard. Nodes and subscriptions remain available for browsing and editing when the service is stopped.

## Screenshot

<div align="center">
  <img src="docs/public/Screenshot.jpg" width="60%" alt="NetProxy Android Manager screenshot" />
</div>

## Features

- **Transparent proxy**: Intercept TCP, UDP, and DNS through eBPF or TUN, with hotspot and USB tethering support.
- **Nodes and subscriptions**: Import node links, node text, Clash YAML, and sing-box JSON, with support for subscription filtering, custom request headers, usage information, and automatic updates.
- **Node selection and latency tests**: Choose nodes manually or use automatic selection; run latency tests even when the service is stopped.
- **Per-app proxy**: Configure blacklists and whitelists separately for each Android user, including cloned apps.
- **Network policies**: Rule, Proxy, Direct, and Rule (allow ads) modes, with automatic switching based on Wi-Fi names and the actual network used.
- **Configuration and diagnostics**: Edit sing-box configuration and local rules, view runtime configuration and logs, and export redacted diagnostic bundles.

## Installation and Getting Started

Requires an `arm64-v8a` Android device with Magisk, KernelSU, or APatch. Traffic interception uses root privileges.

eBPF is selected by default. Choose TUN under **Settings → Inbound settings**. See [Installation and Upgrades](https://www.netproxy.store/guide/installation) for device requirements and installation options.

1. Download `NetProxy_<version>_<build>.zip` from this fork's [nightly builds](https://github.com/SANWU5/NetProxy-Magisk/releases/tag/nightly). It includes the module and Android Manager.
2. Flash it in your root manager, choose how to retain data and whether to install the Android Manager, then wait for installation to finish.
3. Open the Android Manager to connect to the module automatically. Import nodes on the **Nodes** page or add a subscription on the **Subscriptions** page.
4. On the **Nodes** page, choose a group and automatic or manual selection, then return to **Dashboard** to start the service.

The service starts manually by default. Enable **Start on boot** in **Settings** for automatic startup. See [Quick Start](https://www.netproxy.store/guide/quick-start) for initial setup.

## Documentation

| Topic | Guide |
|---|---|
| Installation and initial setup | [Installation and Upgrades](https://www.netproxy.store/guide/installation) · [Quick Start](https://www.netproxy.store/guide/quick-start) |
| Nodes, subscriptions, and latency tests | [Nodes and Subscriptions](https://www.netproxy.store/guide/nodes-subscriptions) |
| Inbounds, per-app proxy, and network sharing | [Inbound Configuration](https://www.netproxy.store/config/inbound) · [eBPF](https://www.netproxy.store/config/ebpf) · [TUN](https://www.netproxy.store/config/tun) |
| Automatic network switching | [Wi-Fi Policies](https://www.netproxy.store/guide/wifi-policy) |
| Routing, DNS, and proxy groups | [Configuration Reference](https://www.netproxy.store/config/singbox) · [Proxy Groups](https://www.netproxy.store/config/policy-groups) |
| Terminal, dashboards, and third-party clients | [CLI](https://www.netproxy.store/guide/cli) · [Control Panels and APIs](https://www.netproxy.store/guide/control-panel) |
| Troubleshooting and issue reports | [FAQ and Diagnostics](https://www.netproxy.store/guide/faq) |

[Changelog](https://www.netproxy.store/changelog) · [Privacy Policy](https://www.netproxy.store/privacy)

## Naipi Companions

<p align="center">
  <a href="https://www.netproxy.store/mascot/dragon"><img src="docs/public/mascots/dragon/base.svg" width="144" alt="Mint-green pixel-art Naipi Dragon" /></a>
  <a href="https://www.netproxy.store/mascot/niang"><img src="docs/public/mascots/niang/stickers/16-leave-it.png" width="144" alt="Naipi Niang with long hair and a blue bow: Leave it to me" /></a>
</p>

**Naipi Dragon (奶屁龙)**, the trailblazer of the Pathway Workshop. Loves digging tunnels and taking naps, and occasionally digs an exit straight into the cookie cupboard.

**Naipi Niang (奶屁娘)**, the owner of the Pathway Workshop. Particular and a little proud, she asks "Where are the logs?" while already handing you a warm drink.

[Workshop Stories](https://www.netproxy.store/mascot/) · [Stickers](https://www.netproxy.store/mascot/stickers) · [Supporters](https://www.netproxy.store/mascot/supporters)

## Source Layout

```text
src/module/          Module installation and runtime files
src/native/netproxy/ Native Go component
src/webui/           Terminal-style module WebUI
src/android/         Android Manager
docs/                User documentation
tests/               Contract and regression tests
```

See the [Contributing Guide](CONTRIBUTING.md) for development and verification, [AGENTS.md](AGENTS.md) for architecture and cross-component contracts, and the [Android README](src/android/README.md) for manager builds.

## Acknowledgments

| Project | Role |
|------|------|
| [reF1nd/sing-box](https://github.com/reF1nd/sing-box) | Current proxy core |
| [SagerNet/sing-box](https://github.com/SagerNet/sing-box) | Upstream sing-box project |
| [Proxylink](https://github.com/Fanju6/Proxylink) | Original project behind NetProxy's internal node conversion support |
| [AsteriskNG](https://github.com/Asterisk4Magisk/AsteriskNG) | Android eBPF implementation reference |
| [v2rayNG](https://github.com/2dust/v2rayNG) | Node parsing reference |

Thanks also to [CHIZI-0618/sing-box](https://github.com/CHIZI-0618/sing-box), [Xray-core](https://github.com/XTLS/Xray-core), [AndroidTProxyShell](https://github.com/CHIZI-0618/AndroidTProxyShell), [IPSET_LKM](https://github.com/TanakaLun/IPSET_LKM), and [KsuWebUIStandalone](https://github.com/KOWX712/KsuWebUIStandalone) for powering or inspiring earlier versions.

## Community and Contributing

- [Report an issue with this fork](https://github.com/SANWU5/NetProxy-Magisk/issues)
- [Pull requests for this fork](https://github.com/SANWU5/NetProxy-Magisk/pulls)
- Original project: [Fanju6/NetProxy-Magisk](https://github.com/Fanju6/NetProxy-Magisk) (use it for the official release)

## License

[GPL-3.0 License](LICENSE)

## Star

[![Star History Chart](https://star-history.dera.page/svg?repos=Fanju6/NetProxy-Magisk&type=date&legend=top-left)](https://star-history.dera.page/#Fanju6/NetProxy-Magisk&type=date&legend=top-left)
