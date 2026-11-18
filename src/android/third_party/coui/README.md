# COUI 组件包（内置源码快照）

来源：AiingJie 的 `coui-ui-kit` 1.0.0（GitHub：sanwu5，GPL-3.0-only），仿 ColorOS 17（COUI）设计的
Jetpack Compose 组件，只依赖 Compose Foundation。

- `src/main/java/dev/aiingjie/coui/`：组件源码，与 1.0.0 发布包一致，未修改包名。
- `DESIGN.md`：颜色、字体、尺寸、圆角与动效数值，改动组件时同步更新。
- `NOTICE.md`：第三方声明（figma-squircle，MIT）。

NetProxy 管理器的全部界面（悬浮底栏、大标题页、卡片分组、弹出层）都基于这里的组件。
构建配置跟随管理器使用的 AGP、Kotlin 与 Compose BOM，不单独锁定旧版本。
