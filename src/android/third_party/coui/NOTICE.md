# 第三方声明

## figma-squircle（MIT）

`coui/src/main/java/dev/aiingjie/coui/CouiShapes.kt` 中 `CouiSmoothShape` 的平滑圆角计算方法与 figma-squircle 相同。
<https://github.com/phamfoo/figma-squircle>

```
MIT License

Copyright (c) 2021 Tien Pham

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## AndroidX、Jetpack Compose、Kotlin（Apache License 2.0）

组件库依赖这些库，但源码包和 AAR 里都不包含它们，由使用者的构建从 Maven 仓库获取。演示安装包里打包了它们。
<https://android.googlesource.com/platform/frameworks/support/> · <https://github.com/JetBrains/kotlin>

## Gradle Wrapper（Apache License 2.0）

`gradlew`、`gradlew.bat` 和 `gradle/wrapper/gradle-wrapper.jar` 来自 Gradle。
<https://github.com/gradle/gradle>

## ColorOS / OPPO

ColorOS、COUI 是 OPPO 的名称。本组件包是独立实现的仿 ColorOS 风格组件，与 OPPO 无关，不含 OPPO 的代码、图片、字体或其他资源。
`ColorOsMaterial.kt` 在运行时通过反射调用 ColorOS 系统自带的材质接口（`com.oplus.view.material`），设备上没有这些接口时自动不用。
