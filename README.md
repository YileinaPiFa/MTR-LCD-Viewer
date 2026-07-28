# MTR-LCD-Viewer

<p align="center">

<img src="https://pan.ylnpf.cn/f/PMTq/84439a0bf49a602d728ab9ef6bb84ebf.png" width="800">

</p>

<p align="center">

基于 Java 的 MTR LCD 脚本离线渲染与预览工具

</p>

<p align="center">

<a href="https://github.com/YileinaPiFa/MTR-LCD-Viewer">
<img src="https://img.shields.io/github/stars/YileinaPiFa/MTR-LCD-Viewer?style=flat-square">
</a>

<img src="https://img.shields.io/github/license/YileinaPiFa/MTR-LCD-Viewer?style=flat-square">
<img src="https://img.shields.io/badge/Java-17+-orange?style=flat-square&logo=openjdk">
<img src="https://img.shields.io/badge/Platform-Java%20GUI-blue?style=flat-square&logo=openjdk">

</p>

---

## 简介

MTR-LCD-Viewer 是一个用于预览和调试 MTR LCD 脚本资源的独立桌面工具。

无需启动 Minecraft，即可直接加载 MTR 资源包、解析 LCD 脚本并查看最终渲染效果。

项目通过模拟必要运行环境，使基于脚本驱动的 LCD 显示内容能够脱离游戏环境运行。

---

## 项目地址

GitHub：

https://github.com/YileinaPiFa/MTR-LCD-Viewer

---

## 功能特性

- 加载 MTR LCD 资源包（ZIP）
- 自动发现可渲染 LCD 内容
- Java2D 高质量渲染
- 支持 ANTE 脚本接口模拟
- 支持资源包内图片、字体资源读取
- 支持脚本日志输出
- LCD 画面缩放与预览
- 支持渲染结果导出
- 内置测试环境
- 支持独立调试 LCD 脚本

---
## 使用环境

| 项目 | 要求 |
| --- | --- |
| Java | Java 17 或更高版本 |
| 内存 | 需＞加载的资源包大小 |
| 架构 | x64/x86 amd/arm |

支持所有具有 Java GUI 运行环境的平台。

不支持无头（Headless）运行环境。

---

## 使用方法

### 1. 准备资源包

准备包含 LCD 脚本与资源文件的 MTR 资源包：

```
example.zip
```

---

### 2. 启动程序

直接运行：

```bat
run.bat
```

或者使用 Java 启动：

```bash
java -jar MTR-LCD-Viewer.jar
```

---

### 3. 加载资源包

打开程序后：

```
文件 → 打开资源包
```

选择对应 `.zip` 文件即可。

---

## 项目结构

```
MTR-LCD-Viewer
│
├─ src
│  └─ lcdviewer
│      ├─ ante        # ANTE API 模拟层
│      ├─ mock        # MTR 数据模拟
│      ├─ pack        # 资源包解析
│      └─ ui          # 用户界面
│
├─ lib                # 第三方依赖
│
├─ build.bat          # 编译脚本
├─ run.bat            # 启动脚本
└─ Full79Test.java    # 完整测试入口
```

---

## 技术实现

### ANTE 兼容层

项目实现了一套轻量运行环境，包括：

- Resources API
- Timing API
- Graphics 包装
- ResourceLocation 解析
- JavaScript 兼容处理

用于保证 LCD 脚本可以在独立环境中运行。

---

### JavaScript 支持

项目使用 Nashorn 执行 LCD 脚本，并提供兼容处理：

- ES6 语法适配
- JavaScript / Java 类型转换
- Graphics2D 调用支持

解决部分脚本在独立环境下无法执行的问题。

---

## 自动测试

项目包含测试工具：

### 冒烟测试

运行：

```bat
run_smoke.bat
```

用于快速确认资源包是否可以正常加载。

---

### 全量 LCD 测试

运行：

```bash
java Full79Test
```

用于执行完整 LCD 渲染检查。

---

## 开发

克隆项目：

```bash
git clone https://github.com/YileinaPiFa/MTR-LCD-Viewer.git
cd MTR-LCD-Viewer
```

编译：

```bat
build.bat
```

---

## 注意事项

- 本项目不是 Minecraft 客户端。
- 不包含 Minecraft 本体文件。
- 仅用于 LCD 脚本调试、预览和开发辅助。
- 部分复杂动画效果可能与游戏内表现存在差异。

---

## 致谢

感谢以下项目提供的参考：

- MTR（Minecraft Transit Railway）
- ANTE Script API
- Nashorn JavaScript Engine

---

## License

本项目采用 MIT License 开源。

完整协议内容请查看：

```
LICENSE
```
