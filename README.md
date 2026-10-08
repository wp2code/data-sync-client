# DataSync Client

DataSync Client 是一款基于 Java 17 + Swing 的桌面数据库同步工具，支持 **MySQL** 与 **PostgreSQL** 之间的全表数据同步与表结构差异同步，并内置 GitLab 集成、AI 问题管理与在线自动更新能力。

![Java 17](https://img.shields.io/badge/Java-17-blue)
![Gradle](https://img.shields.io/badge/Build-Gradle-green)
![Swing](https://img.shields.io/badge/UI-Swing%20FlatLaf-orange)
![Version](https://img.shields.io/badge/Version-1.1.1-brightgreen)

## 功能特性

### 数据同步

- **数据源管理**：保存、编辑、测试多个 MySQL / PostgreSQL 数据源配置，SQLite 本地持久化。
- **表数据同步**：全量读取源表数据，以 Upsert（插入或更新）方式同步到目标表，500 行一批次、单事务提交，失败自动回滚。
- **表结构同步**：对比源表与目标表的列（类型 / 可空 / 默认值 / 注释）与索引差异，自动生成 ALTER TABLE 脚本，支持一键执行。

### GitLab 集成

- **OAuth2 登录**：配置 GitLab 认证信息后自动登录。
- **SQL 脚本管理**：脚本编辑后可直接推送到 GitLab 指定项目 / 分支 / 文件路径，支持创建与更新（自动检测）、多文件提交。
- **项目 / 分支缓存**：基于 Guava Cache 缓存项目列表与分支列表，减少 API 调用。

### AI 问题管理

- **问题训练**：分页查询远端问题列表（服务端筛选训练状态），支持界面手动录入、Excel 批量导入（含运行时生成的导入模板与填写说明）、批量保存 / 更新 / 删除、触发训练与批量更新训练参数。
- **回复审计**：分页查询问题回复列表，支持回复详情查看、更新与删除。
- **环境管理**：多环境（测试 / 生产等）接口配置，支持自定义 Host、通用请求头与各接口路径，每个接口提供请求 / 响应 JSON 示例查看。
- **Excel 导入导出**：问题列表与回复列表均可导出为 `.xlsx` 文件。

### 在线自动更新

- 启动时静默检查 GitHub Releases 新版本，发现新版本才提示。
- 支持下载进度展示、SHA-256 校验、自动替换 exe 并重启（JRE 不随版本升级，无需重新下载）。
- 更新可靠性保障：网络类异常自动重试（指数退避，最多 3 次）、下载中断自动清理半成品文件、exe 被安全软件占用时自动转延迟替换（进程退出后由辅助脚本完成替换并重启）。
- 日志面板提供「检查更新」按钮，可手动检查；Release 描述正文即版本日志，随升级提示一并展示。

### 通用

- **现代暗色 UI**：FlatLaf 主题，日志区彩色 HTML 输出。
- **Windows 可执行文件**：打包为自带 JRE 的 `DataSync.exe`，双击即可运行，无需本机安装 JDK。

## 技术栈

| 依赖 | 版本 | 用途 |
| --- | --- | --- |
| FlatLaf | 3.4.1 | 现代 Swing 主题 |
| MySQL Connector/J | 8.3.0 | MySQL JDBC 驱动 |
| PostgreSQL JDBC | 42.7.3 | PostgreSQL JDBC 驱动 |
| SQLite JDBC | 3.45.2.0 | 本地配置持久化 |
| gitlab4j-api | 6.3.0 | GitLab API 集成 |
| Guava | 33.6.0 | 缓存（项目 / 分支列表） |
| Jackson | 2.17.2 | JSON 处理（AI 接口 / GitHub API） |
| Apache POI | 5.2.5 | Excel 导入导出 |
| Apache Batik | 1.18 | SVG 图标渲染 |
| Logback | 1.5.6 | 日志（控制台 + 文件滚动） |
| Lombok | 1.18.32 | 简化模型类代码 |

## 快速开始

### 环境要求

- JDK 17 或更高版本（仅构建时需要，打包后的 `.exe` 自带 JRE）
- Windows / Linux / macOS（打包 `.exe` 仅在 Windows 下执行）

### 构建运行

```bash
# 编译项目
./gradlew compileJava

# 运行程序
./gradlew run

# 完整构建（编译 + 测试 + 瘦 JAR + Windows .exe + ZIP 分发包）
./gradlew build
```

Windows 环境下请将 `./gradlew` 替换为 `gradlew.bat`。

构建产物：

- 瘦 JAR：`build/libs/data-sync-client-1.1.1.jar`（依赖位于同目录 `build/libs/lib/`，通过 MANIFEST `Class-Path` 引用）
- Windows 可执行文件：`build/launch4j/DataSync.exe`（自带 `jre/` 与 `lib/` 目录）
- ZIP 分发包：`build/distributions/DataSync-1.1.1-win-x64.zip`（解压即可运行，无需 JAVA_HOME）

## 使用说明

### 数据同步

1. 打开程序后，点击 **管理数据源** 配置源数据库与目标数据库。
2. 返回主界面，分别在 **源数据库** 与 **目标数据库** 下拉框中选择已配置的数据源。
3. 选择源端 Schema（PostgreSQL）以及需要同步的表。
4. 点击 **开始同步** 进行数据同步。
5. 点击 **比较结构差异** 可查看列与索引差异，并执行 ALTER 脚本。

### GitLab 脚本管理

1. 点击 **GitLab 配置**，填写 GitLab 地址与 OAuth2 凭证。
2. 在 **脚本管理** 中新建 / 编辑 SQL 脚本。
3. 选择目标 GitLab 项目、分支与文件路径，推送脚本内容。

### AI 问题管理

1. 点击 **AI 环境** 配置环境信息（Host、请求头、问题训练 / 回复审计相关接口路径）。
2. 在 **AI 问题管理** 中切换「问题训练」/「回复审计」页签：
   - **问题训练**：分页查询、手动录入、导入 Excel（可先下载模板）、批量保存 / 更新 / 删除、触发训练；
   - **回复审计**：分页查询问题回复、查看 / 更新 / 删除回复详情。
3. 问题列表与回复列表均可导出为 Excel。

### 检查更新

- 程序启动时自动静默检查 GitHub Releases 新版本；
- 也可在日志面板点击 **检查更新** 手动检查，确认后自动下载、校验并重启完成升级。

## 数据源配置字段

| 字段 | 说明 |
| --- | --- |
| 名称 | 数据源唯一标识 |
| 数据库类型 | MySQL / PostgreSQL |
| 主机 | 数据库服务器地址 |
| 端口 | 数据库端口 |
| 数据库 | 目标数据库名 |
| Schema | 仅 PostgreSQL 使用，默认 `public` |
| 用户名 | 数据库用户名 |
| 密码 | 数据库密码 |

## 项目结构

```
src/main/java/com/datasync/
├─ Main.java                       # 程序入口（FlatLaf 主题初始化）
├─ core/
│  ├─ DataSyncService.java         # 同步引擎：数据同步、结构对比、DDL 生成
│  ├─ DbConnector.java             # JDBC 连接工厂 + Schema 元数据读取
│  ├─ GitLabService.java           # GitLab OAuth2 登录与文件 / 提交操作（单例）
│  ├─ AiQuestionApiClient.java     # AI 问题远端接口客户端（单例）
│  └─ UpdateService.java           # GitHub Releases 在线更新
├─ model/                          # 数据模型（数据源、脚本、AI 问题 / 回复 / 环境等）
├─ ui/
│  ├─ DataSyncUI.java              # 主界面
│  ├─ DataSourceManagerDialog.java # 数据源管理
│  ├─ GitLabMangerDialog.java      # GitLab 认证配置
│  ├─ ScriptMangerDialog.java      # SQL 脚本管理
│  ├─ AiQuestionMangerDialog.java  # AI 问题管理（问题训练 / 回复审计页签）
│  ├─ QuestionTrainingPanel.java   # 问题训练面板
│  ├─ AnswerAuditPanel.java        # 回复审计面板
│  └─ AiEnvMangerDialog.java       # AI 环境管理
├─ components/                     # 自定义 Swing 组件
└─ util/
   ├─ SQLiteConfigUtil.java        # SQLite 配置持久化（含兼容性迁移）
   ├─ ExcelQuestionUtil.java       # AI 问题 Excel 导入 / 模板生成
   ├─ ExcelExportUtil.java         # AI 问题 / 回复 Excel 导出
   ├─ LogUtil.java                 # 彩色 HTML 日志
   └─ IconUtil.java                # SVG 图标渲染
```

## 打包发布

### 仅生成瘦 JAR

```bash
./gradlew jar copyLibs
```

生成后可通过以下命令运行（需保证 `lib/` 与 JAR 同目录，即位于 `build/libs/` 下）：

```bash
java -jar build/libs/data-sync-client-1.1.1.jar
```

### 生成 Windows .exe（自带 JRE + 依赖）

```bash
./gradlew jar
./gradlew createExe
```

构建过程中会自动从 Eclipse Temurin 下载 Windows x64 JRE（项目根目录存在 `jre.zip` 时优先使用本地文件），并解压到 `build/launch4j/jre` 目录。最终产物：

- Windows 可执行文件：`build/launch4j/DataSync.exe`
- 内置 JRE 目录：`build/launch4j/jre/`
- 第三方依赖目录：`build/launch4j/lib/`（瘦 JAR 所需的运行时依赖）

运行 `DataSync.exe` 时，程序会优先使用同目录下的 `jre` 文件夹，无需用户本机安装 JDK。

### 生成 ZIP 分发包

```bash
./gradlew packageZip
```

将 `DataSync.exe`、`jre/` 与 `lib/` 打包为 `build/distributions/DataSync-<version>-win-x64.zip`，解压即可运行。`./gradlew build` 会依次完成瘦 JAR、`.exe` 与 ZIP 分发包的全部构建。

## 版本发布

发布流程已完全自动化：推送 `vX.Y.Z` 格式的 Git Tag 即触发 GitHub Actions（[release.yml](.github/workflows/release.yml)）自动完成构建、校验与发布。

**发布步骤：**

1. 修改 `build.gradle` 中的 `version`（版本号会在构建时注入 `version.properties`，运行期由 `UiConstants` 动态读取，无需改代码）；
2. 在 [CHANGELOG.md](CHANGELOG.md) 中新增 `## [vX.Y.Z] - 日期` 小节（该小节将被自动提取为 GitHub Release 描述）；
3. 提交后打 Tag 并推送：`git tag -a vX.Y.Z -m "Release vX.Y.Z" && git push origin main vX.Y.Z`。

**CI 会自动校验并完成：**

- Tag 必须位于 `main` 分支历史中，且与 `build.gradle` 版本号一致；
- 从 CHANGELOG.md 提取对应版本小节作为 Release 描述；
- 构建 `.exe` 与 ZIP 分发包，生成 `DataSync.exe.sha256` 校验文件，并作为 Release 资产发布。

**Release 资产清单（客户端在线更新依赖）：**

| 资产 | 用途 |
| --- | --- |
| `DataSync.exe` | 在线更新的升级目标（必需） |
| `DataSync.exe.sha256` | 下载完整性校验（可选，缺失时跳过校验） |
| `DataSync-<version>-win-x64.zip` | 新用户完整分发包（解压即用） |

## 许可证

本项目采用 [LICENSE.md](LICENSE.md) 中声明的许可证。

## 参与贡献

欢迎提交 Issue 或 Pull Request 改进本项目。
