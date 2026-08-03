# Termux AI Chat - 定制版

[![下载 APK](https://img.shields.io/github/v/release/lingqingsuiyu-cmyk/termux-aichat?label=下载&color=blue)](https://github.com/lingqingsuiyu-cmyk/termux-aichat/releases/latest)

本项目是 [Termux](https://termux.com) 的定制 fork，在保留完整终端功能的基础上，内置了 AI 聊天模块。

---

## 新增功能

- **AI 对话** — 支持 OpenAI 兼容 API，流式输出，保留最近 20 条上下文
- **多 Profile 配置** — 可为不同角色/服务分别配置名称、角色名、API 地址、Key 和模型
- **SSH 远程执行** — 内置 SSH 客户端（JSch），可直接在聊天界面执行远程命令
- **本地 Bash 执行** — 支持在对话中执行本地 shell 命令
- **代码块渲染** — 消息中的代码块高亮显示，带一键执行和复制按钮
- **悬浮球** — 右下角可拖动悬浮按钮，随时唤起 AI 聊天

---

## 下载安装

前往 [Releases](https://github.com/lingqingsuiyu-cmyk/termux-aichat/releases/latest) 下载最新 APK。

- `termux-aichat-vX.X.X-arm64.apk` — 适用于骁龙 / 联发科 64 位 Android 手机（推荐）

**Android 7.0 及以上可用。**

---

## 快速上手

1. 安装 APK，打开 Termux
2. 点击右下角悬浮球，或从菜单进入 **AI 聊天**
3. 进入设置，填写：
   - API 地址（如 `https://api.openai.com/v1`）
   - API Key
   - 模型名称（如 `gpt-4o`、`claude-sonnet-4`）
4. 保存后即可开始对话

---

## 关于原项目

本项目基于 [termux/termux-app](https://github.com/termux/termux-app) 开发，遵循 GPLv3 协议。  
终端核心功能、插件体系等均来自原项目，感谢 Termux 团队的贡献。

原项目支持的插件（需从同一来源安装）：
- [Termux:API](https://github.com/termux/termux-api)
- [Termux:Boot](https://github.com/termux/termux-boot)
- [Termux:Float](https://github.com/termux/termux-float)
- [Termux:Styling](https://github.com/termux/termux-styling)
- [Termux:Tasker](https://github.com/termux/termux-tasker)
- [Termux:Widget](https://github.com/termux/termux-widget)

---

## 许可证

GPLv3，详见 [LICENSE](LICENSE.md)。
