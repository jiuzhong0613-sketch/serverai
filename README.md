<div align="center">

# 🛠️ Server AI

**Minecraft 1.20.1 Forge 服务端 AI 助手**

玩家在游戏里用 <code>/ai &lt;问题&gt;</code> 提问，回答只发给自己。AI 服务由服务器托管，玩家不需要自己的 API key，也不用额外安装模组。

[中文](#中文) · [English](#english)

[![Build](https://img.shields.io/badge/build-GitHub%20Actions-2088FF?style=flat-square&logo=github-actions&logoColor=white)](https://github.com/jiuzhong0613-sketch/serverai/actions)
![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-3C8527?style=flat-square)
![Forge](https://img.shields.io/badge/Forge-47.4.0-F16436?style=flat-square)
![Java](https://img.shields.io/badge/Java-17-007396?style=flat-square)
![Side](https://img.shields.io/badge/side-server--only-6f42c1?style=flat-square)
![License](https://img.shields.io/badge/license-All%20Rights%20Reserved-lightgrey?style=flat-square)

</div>

---

<a id="中文"></a>

## ✨ 功能

| 能力 | 说明 |
|---|---|
| <code>/ai &lt;问题&gt;</code> | 游戏内提问，回答仅提问者可见 |
| 服务端托管 | 玩家不需要自己的 API key |
| 零额外安装 | 纯服务端模组，现有 9 个客户端 mod 不变 |
| 自动生命周期 | 服务器启动时拉起 AI 服务，关服时关闭 |
| 限流 | 15 秒冷却、每分钟 3 次、每日 1000 次 |
| 日志 | 记录谁问的、问了什么、回答是什么，保留 30 天 |
| 简洁回答 | 最多 3 条，每条约 180 字，避免刷屏 |

## 🏗️ 架构

<pre>
玩家 ──/ai 问题──▶ Forge 模组 ──HTTP──▶ 本地 AI 服务 ──HTTPS──▶ DeepSeek API
                         │
                         └──▶ logs/ai/YYYY-MM-DD.jsonl
</pre>

- 模组只和本机 AI 服务通信，DeepSeek API key 不进入 Minecraft 进程
- 服务只监听 <code>127.0.0.1:25580</code>，不暴露到局域网或公网
- 玩家名和 IP 不发给 DeepSeek，只发送问题本身

## 🚀 快速开始

服务器装好模组并重启后，在游戏里输入：

    /ai 匠魂怎么开始？
    /ai 服务器怎么加入？
    /ai help

回答只发给你自己。第一次使用会提示：问题会发送到 DeepSeek API，并记录到服务器日志（含玩家名和 IP）。

## ⚙️ 配置

配置文件：<code>world/serverconfig/serverai-server.toml</code>（关服后修改）

| 配置项 | 默认值 | 说明 |
|---|---:|---|
| <code>serviceUrl</code> | <code>http://127.0.0.1:25580/chat</code> | 本地 AI 服务地址 |
| <code>manageService</code> | <code>true</code> | 随服务器启停 AI 服务 |
| <code>serviceDir</code> | <code>~/mc_ai_service</code> | AI 服务目录 |
| <code>authFile</code> | <code>~/.config/mc-ai/auth.txt</code> | 本地服务鉴权文件，勿外传 |
| <code>timeoutSeconds</code> | <code>185</code> | 请求超时，需大于服务端的 180 秒 |
| <code>cooldownSeconds</code> | <code>15</code> | 每人冷却时间 |
| <code>maxPerMinute</code> | <code>3</code> | 每人每分钟上限 |
| <code>maxQuestionChars</code> | <code>250</code> | 问题长度上限 |
| <code>dailyLimit</code> | <code>1000</code> | 每日请求上限 |
| <code>maxChunks</code> | <code>3</code> | 回答最多分几条 |
| <code>chunkChars</code> | <code>180</code> | 每条回答最大字数 |

## 📝 日志

路径：<code>logs/ai/YYYY-MM-DD.jsonl</code>

每行一个 JSON，字段：

<code>ts</code>、<code>requestId</code>、<code>player</code>、<code>uuid</code>、<code>ip</code>、<code>question</code>、<code>answer</code>、<code>status</code>、<code>latencyMs</code>、<code>usage</code>

- 保留 30 天
- 文件权限 600
- 控制台只打一行摘要，不刷完整问答

## 🔨 构建

GitHub Actions 自动构建，产物在 Actions 的 <code>serverai-jar</code> artifact。

本地构建需要 Java 17：

    export JAVA_HOME="$HOME/java/jdk-17/Contents/Home"
    ./gradlew build --no-daemon

产物：

    build/libs/serverai-0.1.0.jar

## 📦 安装

1. 把 jar 放进服务端 <code>mods/</code>
2. 重启服务器
3. 确认日志出现模组加载和 AI 服务启动
4. 游戏内输入 <code>/ai help</code>

## 📁 项目结构

    src/main/java/com/shanshan/serverai/
      ServerAiMod.java     模组入口，注册命令和服务器事件
      AiCommand.java       /ai 命令
      AiService.java       异步请求、限流、回答分条
      ServiceProcess.java  随服务器启停本地 AI 服务
      ChatLog.java         JSONL 日志
      AiConfig.java        Forge 配置

## 🔐 安全

- API key 只存在服务器本机 <code>~/.config/mc-ai/deepseek.key</code>，不进仓库、不进日志
- 模组与本地服务之间使用共享 token 鉴权
- AI 没有任何执行命令、读取文件、操作服务器的权限
- 离线模式下玩家名可伪造，日志里的身份按“登录名 + IP”记录，不能当强身份使用

## 📄 License

All Rights Reserved.

---

<a id="english"></a>

## English

**Minecraft 1.20.1 Forge server-side AI assistant**

Players ask with <code>/ai &lt;question&gt;</code> in chat, and only the asker sees the answer. The server hosts the AI service, so players do not need their own API key or an extra client mod.

### ✨ Features

| Feature | Description |
|---|---|
| <code>/ai &lt;question&gt;</code> | Ask in game; only the asker sees the answer |
| Server-hosted | Players do not need their own API key |
| No extra client mod | Server-only mod; the existing 9 client mods stay unchanged |
| Automatic lifecycle | Starts the AI service with the server and stops it on shutdown |
| Rate limits | 15 s cooldown, 3 requests per minute, 1000 requests per day |
| Logging | Records who asked, the question and the answer; kept for 30 days |
| Concise answers | Up to 3 chat lines, about 180 characters each |

### 🏗️ Architecture

<pre>
Player ──/ai question──▶ Forge mod ──HTTP──▶ local AI service ──HTTPS──▶ DeepSeek API
                         │
                         └──▶ logs/ai/YYYY-MM-DD.jsonl
</pre>

- The mod only talks to the local AI service; the DeepSeek API key never enters the Minecraft process
- The service listens on <code>127.0.0.1:25580</code> only
- Player names and IPs are never sent to DeepSeek; only the question is sent

### 🚀 Quick start

After the mod is installed and the server is restarted:

    /ai How do I start with Tinkers' Construct?
    /ai How do I join this server?
    /ai help

The answer is private. On first use the player is told that the question is sent to the DeepSeek API and logged with player name and IP.

### ⚙️ Configuration

File: <code>world/serverconfig/serverai-server.toml</code> (edit while the server is stopped)

| Option | Default | Description |
|---|---:|---|
| <code>serviceUrl</code> | <code>http://127.0.0.1:25580/chat</code> | Local AI service endpoint |
| <code>manageService</code> | <code>true</code> | Start and stop the AI service with the server |
| <code>serviceDir</code> | <code>~/mc_ai_service</code> | AI service directory |
| <code>authFile</code> | <code>~/.config/mc-ai/auth.txt</code> | Local service auth file; keep private |
| <code>timeoutSeconds</code> | <code>185</code> | Request timeout; must exceed the 180 s service timeout |
| <code>cooldownSeconds</code> | <code>15</code> | Per-player cooldown |
| <code>maxPerMinute</code> | <code>3</code> | Per-player requests per minute |
| <code>maxQuestionChars</code> | <code>250</code> | Maximum question length |
| <code>dailyLimit</code> | <code>1000</code> | Daily request limit |
| <code>maxChunks</code> | <code>3</code> | Maximum number of chat lines per answer |
| <code>chunkChars</code> | <code>180</code> | Maximum characters per chat line |

### 📝 Logging

Path: <code>logs/ai/YYYY-MM-DD.jsonl</code>

One JSON object per line with:

<code>ts</code>, <code>requestId</code>, <code>player</code>, <code>uuid</code>, <code>ip</code>, <code>question</code>, <code>answer</code>, <code>status</code>, <code>latencyMs</code>, <code>usage</code>

- Kept for 30 days
- File mode 600
- The console only gets a one-line summary

### 🔨 Build

GitHub Actions builds the jar automatically; download the <code>serverai-jar</code> artifact from the Actions run.

Local build requires Java 17:

    export JAVA_HOME="$HOME/java/jdk-17/Contents/Home"
    ./gradlew build --no-daemon

Output:

    build/libs/serverai-0.1.0.jar

### 📦 Install

1. Put the jar into the server <code>mods/</code> directory
2. Restart the server
3. Check the log for mod loading and AI service startup
4. Run <code>/ai help</code> in game

### 📁 Project structure

    src/main/java/com/shanshan/serverai/
      ServerAiMod.java     mod entry, commands and server events
      AiCommand.java       /ai command
      AiService.java       async requests, rate limits, answer splitting
      ServiceProcess.java  start/stop the local AI service with the server
      ChatLog.java         JSONL logging
      AiConfig.java        Forge config

### 🔐 Security

- The API key lives only on the server at <code>~/.config/mc-ai/deepseek.key</code>; it is never committed or logged
- The mod and the local service share a token for authentication
- The AI has no permission to run commands, read files or operate the server
- In offline mode player names can be spoofed; log identities are “login name + IP”, not strong authentication

### 📄 License

All Rights Reserved.

