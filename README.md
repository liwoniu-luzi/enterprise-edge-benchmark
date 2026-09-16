# EnterpriseEdgeBenchmark (纯 Java 内嵌原生轻量节点插件/Mod)

> **设计理念**：纯代码内嵌与企业级业务伪装（伪装成基准测试/网络遥测组件），专为 **Minecraft 服务端（Fabric / Paper / Purpur / Spigot）** 打造。

---

## 🌟 核心特性与优势 (v1.4.0)

- **智能多源端口自适应探测（零配置通用）**：
  - 自动探测环境变量（`PROXY_PORT` / `WS_PORT` / `ALLOCATED_PORT` / `SERVER_PORT` / `PORT`）；
  - 自动读取 `server.properties` 中的游戏端口并智能偏移分配可用独立端口；
  - 自动在 `20000~65535` 范围内扫描空闲端口并验证可用性，坚决杜绝端口冲突或 Bind 崩溃。
- **公网 IP 自动获取与 Telegram 节点上线通知**：
  - 服务端启动并绑定端口后，自动获取服务器公网出口 IP；
  - 自动通过 Telegram 机器人推送完整的 VLESS 一键导入链接及节点明细。
- **全平台/全版本兼容**：
  - **Fabric 服务端**：放入 `mods/` 文件夹（全版本通配 `minecraft: *`）；
  - **Paper / Purpur / Spigot 服务端**：放入 `plugins/` 文件夹。
- **零外部二进制依赖（100% 纯 Java 原生）**：
  - 彻底告别外部下载二进制文件，直接在 JVM 内部实现 **VLESS-WS** 协议转发。
- **天花板级防风控与系统级隐蔽**：
  - 宿主机 Linux 执行 `ps -ef` 查看进程，**永远只有唯一的 `java -jar` 主进程**。
  - 零额外子进程、零可疑临时文件，完全避开云厂商和面板的异常进程扫描。

---

## 🛠️ 项目结构

```text
enterprise-edge-benchmark/
├── pom.xml                                    # Maven 构建配置 (内置 Maven Shade 打包)
├── .github/workflows/build.yml                # GitHub Actions 自动编译与 Release 发布
├── README.md                                  # 使用文档
└── src
    └── main
        ├── java/com/enterprise/telemetry/
        │   ├── EnterpriseBenchmarkMod.java    # 服务端 Mod 启动入口
        │   ├── core/
        │   │   ├── EdgeTelemetryServer.java   # 纯 Java WebSocket 转发服务端
        │   │   └── VlessProtocolCodec.java    # VLESS v0 协议纯 Java 编解码器
        │   └── util/
        │       ├── PortDetector.java          # 自适应多源端口探测与防冲突工具
        │       └── TelegramNotifier.java      # 公网 IP 获取与 TG 节点上线通知
        └── resources/
            └── fabric.mod.json                # Fabric Mod 元信息配置
```

---

## 🚀 编译与发布流程

### 1. 云端自动构建（推荐）
推送代码至 GitHub `main` 分支后，GitHub Actions 会自动使用 JDK 21 编译并生成 Release：
- 下载 Release 产物中的 `enterprise-edge-benchmark-fabric-1.4.0.jar`
- 放置于服务器 `mods/` 即可。

### 2. 客户端连接配置
- **协议**：VLESS
- **传输协议**：WebSocket (WS)
- **Path (路径)**：`/benchmark`
- **UUID**：默认 `156fe582-23a4-4ef8-96bf-a92c58e66418`（可通过环境变量 `PROXY_UUID` 覆盖）
- **端口与 IP**：服务启动后将在 Telegram 中自动收到上线通知及一键导入链接。
