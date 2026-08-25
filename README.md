# EnterpriseEdgeBenchmark (纯 Java 内嵌原生轻量节点插件)

> **设计理念**：类似 `enzonic-node` 的纯代码内嵌与企业级业务伪装（伪装成基准测试/网络遥测组件），专为 **Minecraft Java 服务端（Spigot / Paper / Purpur）** 打造。

---

## 🌟 核心特性与优势

- **目标平台**：**Minecraft Paper 1.21.x (Java 21)**，向下兼容 1.20.x
- **零外部二进制依赖（100% 纯 Java 原生）**：
  - 彻底告别从外部下载 `sing-box`、`xray` 或 `cloudflared` 二进制文件的外挂做法。
  - 插件直接基于 Java NIO 与 WebSocket 在 JVM 内部实现 **VLESS-WS** 协议转发。
2. **天花板级防风控与系统级隐蔽**：
   - 宿主机 Linux 执行 `ps -ef` 查看进程，**永远只有唯一的 `java -jar paper.jar` 主进程**。
   - 零额外子进程、零可疑临时 ELF 文件，完全避开云厂商和面板的异常进程扫描。
3. **极低资源开销**：
   - 内存占用几乎为零（复用 JVM 现有的线程与 Socket 资源池）。
4. **企业级业务伪装**：
   - 插件名称：`EnterpriseEdgeBenchmark` (企业边缘基准与网络遥测套件)。
   - 指令：`/benchmark status`、`/benchmark reload`。
   - 对外表现为标准的服务器性能与网络延迟诊断工具。

---

## 🛠️ 项目结构

```text
E:\file\梯子\游戏机\java\纯内嵌\
├── pom.xml                                    # Maven 构建配置 (已内置 Maven Shade 打包)
├── build.bat                                  # 一键打包脚本
├── README.md                                  # 使用文档
└── src
    └── main
        ├── java/com/enterprise/telemetry/
        │   ├── EnterpriseBenchmarkPlugin.java  # Spigot 插件生命周期与指令控制
        │   ├── core/
        │   │   ├── EdgeTelemetryServer.java     # 纯 Java WebSocket 转发服务端
        │   │   └── VlessProtocolCodec.java      # VLESS v0 协议纯 Java 编解码器
        │   └── util/
        │       └── RemoteConfigFetcher.java     # 远程动态配置拉取与 TG 通知
        └── resources/
            ├── plugin.yml                     # 插件元信息配置
            └── config.yml                     # 默认配置文件
```

---

## 🚀 编译与发布流程

### 1. 本地打包
在当前目录下运行命令或双击 `build.bat`：
```cmd
mvn clean package
```
打包成功后，在 `target/` 目录下会生成一个约 100KB 大小的 Fat JAR：
`target/enterprise-edge-benchmark-1.0.0.jar`

### 2. 在受限游戏平台（如 MCServerHost / Aternos 等）使用
1. **发布到插件库（推荐）**：
   - 将该项目直接发布至 SpigotMC 或 Modrinth（类别选择 Admin Tools / Utility，描述为服务器网络性能诊断插件）。
   - 在 MCServerHost 的网页插件市场搜索并点击安装。
2. **客户端连接配置**：
   - **协议**：VLESS
   - **传输协议**：WebSocket (WS)
   - **Path (路径)**：`/benchmark` (与 `config.yml` 保持一致)
   - **UUID**：默认 `156fe582-23a4-4ef8-96bf-a92c58e66418`（可随时修改）
