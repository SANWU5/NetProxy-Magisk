# COUI 组件包（内置源码快照）

来源：AiingJie 的 `coui-ui-kit` 1.0.0（GitHub：sanwu5，GPL-3.0-only），仿 ColorOS 17（COUI）设计的
Jetpack Compose 组件，只依赖 Compose Foundation。

- `src/main/java/dev/aiingjie/coui/`：组件源码，基于 1.0.0 发布包，未修改包名。NetProxy 在此基础上有改动（弹出层、按压、列表分割线、操作行与开关触感、对话框按钮、底栏显隐动画，新增空状态与展开行），数值记在 `DESIGN.md`。
- `DESIGN.md`：颜色、字体、尺寸、圆角与动效数值，改动组件时同步更新。
- `NOTICE.md`：第三方声明（figma-squircle，MIT）。

NetProxy 管理器的全部界面（悬浮底栏、大标题页、卡片分组、弹出层）都基于这里的组件。
构建配置跟随管理器使用的 AGP、Kotlin 与 Compose BOM，不单独锁定旧版本。
