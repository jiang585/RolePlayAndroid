<div align="center">

# 满座 FullHouse

**好戏开场，群里已满座。**

一个群聊里，坐满了由 AI 扮演的活生生角色。
写一段世界观、建几张角色卡、选一个身份进场——你发一句台词，他们接住整场戏。

[![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![MinSdk](https://img.shields.io/badge/minSdk-26-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Language](https://img.shields.io/badge/language-Java-blue?logo=openjdk)](#)
[![Arch](https://img.shields.io/badge/architecture-MVVM%20%2B%20Repository-8A2BE2)](#)
[![Release](https://img.shields.io/badge/release-v1.0-orange)](#)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-ff69b4.svg)](#-参与贡献)

</div>

---

## 🎭 这是什么

**满座 FullHouse** 是一款**本地优先**的多人 AI 角色扮演群聊 App：

在一个类微信群的界面里，多个 AI 角色同群演出。他们有各自的头像、性格、说话风格和口头禅，会互相接话、吵架、拌嘴、推进剧情——而你，既是群友，也是编剧。

适合：剧情写作者、跑团（TRPG）爱好者、同人创作者，以及想要一段沉浸式聊天陪伴的人。

> 🔒 **隐私优先**：所有剧本、角色、聊天记录仅存储在你的设备上；只有对话生成会调用你自行配置的大模型 API。无需注册、无账号、无云同步。

## ✨ 特性

- 💬 **群聊式演出** — 类微信聊天界面，我固定右侧、NPC 靠左，动作与旁白渲染为居中系统消息
- 🎬 **AI 编排发言** — 谁接话、说几句由 AI 决定；一键「AI 推进」可让 NPC 连续自演多轮，随时叫停
- 🌍 **一句话生成世界观** — 输入一句设定，AI 完善为时代 / 地点 / 势力 / 规则 / 主线的完整世界观
- 🃏 **角色卡系统** — 姓名、性格、背景、说话风格、口头禅、人物关系、隐藏设定……支持纯文本描述让 AI 补全，或结构化导入
- 🎟️ **身份自由切换** — 扮演主角、配角或旁观者，随时换身份，历史消息原样保留
- 🎨 **个性化装扮** — 群聊背景、气泡样式、每角色专属配色
- 📦 **导入导出** — 角色卡 / 世界观 / 聊天记录可独立导出导入，剧本可整体打包分享，方便二创
- 🔐 **密钥本机加密** — API Key 通过 Android Keystore 加密存储，支持多个 API 配置档案一键切换

## 🚀 快速开始

### 环境要求

- Android Studio（建议最新稳定版）
- Android 8.0（API 26）及以上设备或模拟器
- 一个 OpenAI 兼容的大模型 API（Key 由你自行配置）

### 构建运行

```bash
git clone https://github.com/jiang585/RolePlayAndroid.git
cd RolePlayAndroid
```

用 Android Studio 打开工程，直接 Run 即可。

### 配置 API

首次启动后进入 **设置 → 配置档案**：

1. 填入 API Base URL 与 API Key（兼容 OpenAI 接口格式，各类中转 / 国产模型均可）
2. Key 仅通过 Android Keystore 加密保存在本机
3. 支持保存多个配置档案，随时切换

## 📖 上手三步

1. **建剧本** — 新建剧本，一句话描述世界观（如「架空古代王朝，夺嫡之争」），让 AI 完善
2. **建角色** — 每个角色写一两句人设（或导入角色卡），AI 自动补全完整角色卡
3. **进场开演** — 选好你的身份，发出第一句台词，好戏开场 🎬

## 🗺️ Roadmap

- [x] MVP：剧本管理 / 角色卡 / 世界观 / 群聊引擎 / AI 编排 / 装扮 / 导入导出
- [ ] 世界观版本管理与回退
- [ ] 剧本包分享市场（本地文件互传）
- [ ] 多语言界面

## 🤝 参与贡献

欢迎 Issue 与 PR！提交前请：

1. Fork 本仓库并新建分支
2. 遵循现有代码风格（Java + MVVM + Repository 分层）
3. 确保 `./gradlew build` 通过

## 📄 开源协议

本项目基于 [MIT License](LICENSE) 开源。
