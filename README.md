# OmniKit

OmniKit 是一款基于 Java 17 + Swing 的桌面效率工具，集数据库数据同步、GitLab 脚本管理、AI 训练、程序容器与在线自动更新于一体。

![Java 17](https://img.shields.io/badge/Java-17-blue)
![Gradle](https://img.shields.io/badge/Build-Gradle-green)
![Swing](https://img.shields.io/badge/UI-Swing%20FlatLaf-orange)
![Version](https://img.shields.io/badge/Version-2.0.0-brightgreen)

## 功能特性

### 数据同步

- **数据源管理**：保存、编辑、测试多个 MySQL / PostgreSQL 数据源配置。
- **表数据同步**：将源表数据全量同步到目标表，自动插入或更新已有记录，同步失败自动回滚。
- **表结构同步**：对比源表与目标表的列与索引差异，自动生成变更脚本，支持一键执行。

### GitLab 集成

- **OAuth2 登录**：配置 GitLab 认证信息后自动登录。
- **SQL 脚本管理**：脚本编辑后可直接推送到 GitLab 指定项目 / 分支 / 文件路径，支持创建与更新、多文件提交；脚本类型可选「SQL 脚本」或「普通文件」，普通文件仅编辑 / 同步，不连数据库执行。

### AI 训练

- **问题训练**：分页查询问题列表，支持界面手动录入、Excel 批量导入、批量保存 / 更新 / 删除、触发训练与批量更新训练参数。
- **回复审计**：分页查询问题回复列表，支持回复详情查看、更新与删除。
- **环境管理**：多环境（测试 / 生产等）接口配置，支持自定义 Host、请求头与各接口路径。
- **Excel 导入导出**：问题列表与回复列表均可导出为 Excel 文件。

### 程序容器

- **外部程序管理**：配置并启动 / 停止 / 重启外部程序，支持 Java、Python 及通用命令（脚本、可执行文件）。
- **后台运行**：关闭对话框后进程继续运行，重新打开可恢复运行状态与历史日志。
- **控制台输出**：实时查看程序输出，自动处理编码，避免中文乱码。
- **退出保护**：关闭应用时可选择停止或保留后台运行的程序，下次启动可检测并停止遗留进程。



## 技术栈

| 依赖 | 版本 | 用途 |
| --- | --- | --- |
| FlatLaf | 3.4.1 | 现代 Swing 主题 |
| MySQL Connector/J | 8.3.0 | MySQL JDBC 驱动 |
| PostgreSQL JDBC | 42.7.3 | PostgreSQL JDBC 驱动 |
| SQLite JDBC | 3.45.2.0 | 本地配置持久化 |
| gitlab4j-api | 6.3.0 | GitLab API 集成 |
| Guava | 33.6.0-jre | 缓存（项目 / 分支列表） |
| Jackson | 2.17.2 | JSON 处理（AI 接口 / GitHub API） |
| Apache POI | 5.2.5 | Excel 导入导出 |
| Apache Batik | 1.18 | SVG 图标渲染 |
| Logback | 1.5.6 | 日志（控制台 + 文件滚动） |
| Lombok | 1.18.32 | 简化模型类代码 |
| JUnit 5 | 5.10.2 | 单元测试 |

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

# 运行单元测试（JUnit 5）
./gradlew test

# 完整构建（编译 + 测试 + 瘦 JAR + Windows .exe + ZIP 分发包）
./gradlew build
```

Windows 环境下请将 `./gradlew` 替换为 `gradlew.bat`。

构建产物：

- 瘦 JAR：`build/libs/omnikit-client-2.0.0.jar`（依赖位于同目录 `build/libs/lib/`，通过 MANIFEST `Class-Path` 引用）
- Windows 可执行文件：`build/launch4j/OmniKit.exe`（自带 `jre/` 与 `lib/` 目录）
- ZIP 分发包：`build/distributions/OmniKit-2.0.0-win-x64.zip`（解压即可运行，无需 JAVA_HOME）

## 使用说明

### 数据同步

1. 打开程序后，点击 **管理数据源** 配置源数据库与目标数据库。
2. 返回主界面，分别在 **源数据库** 与 **目标数据库** 下拉框中选择已配置的数据源。
3. 选择源端 Schema（PostgreSQL）以及需要同步的表。
4. 点击 **开始同步** 进行数据同步。
5. 点击 **比较结构差异** 可查看列与索引差异，并执行 ALTER 脚本。

### GitLab 脚本管理

1. 点击 **GitLab 配置**，填写 GitLab 地址与 OAuth2 凭证。
2. 在 **脚本管理** 中新建 / 编辑脚本，新建时选择脚本类型：
   - **SQL 脚本**：选择数据库类型，右侧可选择数据源与库 / Schema 后执行，展示运行结果与运行日志；
   - **普通文件**：任意文本文件（md / yml / java 等），右侧仅提供内容编辑与保存，不需要数据源、运行按钮、运行结果与运行日志。
3. 选择目标 GitLab 项目、分支与文件路径，推送脚本内容。

### AI 训练

1. 点击 **AI 环境** 配置环境信息（Host、请求头、问题训练 / 回复审计相关接口路径）。
2. 在 **AI 训练** 中切换「问题训练」/「回复审计」页签：
   - **问题训练**：分页查询、手动录入、导入 Excel（可先下载模板）、批量保存 / 更新 / 删除、触发训练；
   - **回复审计**：分页查询问题回复、查看 / 更新 / 删除回复详情。
3. 问题列表与回复列表均可导出为 Excel。

### 程序容器

1. 在程序容器左侧列表新增程序配置，选择程序类型：
   - **Java 程序**：填写主程序入口 / JAR 与可选的 JRE / JDK 路径；
   - **Python 程序**：填写入口脚本与可选的解释器 / 虚拟环境路径；
   - **其他命令**：填写可执行文件、bat 脚本或 PATH 中的命令名。
2. 配置命令参数、工作目录与输出编码（自动探测 / UTF-8 / GBK）。
3. 点击 **启动 / 停止 / 重启**，右侧控制台实时展示输出。
4. 关闭对话框不影响进程运行，重新打开可恢复状态与历史日志。

### 设置

点击主界面右上角的 **设置** 按钮打开下拉菜单，集中提供以下入口：

- **数据源管理**：新增、编辑、删除并测试 MySQL / PostgreSQL 数据源配置；
- **GitLab 配置**：填写 GitLab 地址与 OAuth2 凭证；
- **检查更新**：菜单项中灰显当前版本号，点击即可手动检查新版本；
- **帮助**：打开项目 GitHub 主页。


## 项目结构

```
src/main/java/com/omnikit/
├─ Main.java                       # 程序入口（FlatLaf 主题初始化 + 退出钩子清理子进程）
├─ core/
│  ├─ OmniKitService.java          # 同步引擎：数据同步、结构对比、DDL 生成
│  ├─ DbConnector.java             # JDBC 连接工厂 + Schema 元数据读取
│  ├─ GitLabService.java           # GitLab OAuth2 登录与文件 / 提交操作（单例）
│  ├─ AiQuestionApiClient.java     # AI 问题远端接口客户端（单例）
│  ├─ UpdateService.java           # GitHub Releases 在线更新
│  ├─ ProcessContainerService.java # 外部进程生命周期与输出日志管理（单例）
│  └─ EncodingDetectReader.java    # 子进程输出编码探测 Reader
├─ model/                          # 数据模型（数据源、脚本、AI 问题 / 回复 / 环境、程序容器等）
├─ ui/
│  ├─ OmniKitUI.java               # 主界面
│  ├─ DataSourceManagerDialog.java # 数据源管理
│  ├─ GitLabMangerDialog.java      # GitLab 认证配置
│  ├─ ScriptMangerDialog.java      # SQL 脚本管理
│  ├─ AiQuestionMangerDialog.java  # AI 训练（问题训练 / 回复审计页签）
│  ├─ QuestionTrainingPanel.java   # 问题训练面板
│  ├─ AnswerAuditPanel.java        # 回复审计面板
│  ├─ AiEnvMangerDialog.java       # AI 环境管理
│  ├─ ProgramContainerDialog.java  # 程序容器（列表 + 配置表单 + 控制台）
│  ├─ TrainingCastTimeDialog.java  # 训练耗时查询
│  └─ ButtonFactory.java           # 统一按钮样式工厂
├─ components/                     # 自定义 Swing 组件
└─ util/
   ├─ SQLiteConfigUtil.java        # SQLite 配置持久化（含兼容性迁移）
   ├─ ConfigUtil.java              # 配置访问静态门面
   ├─ ExcelQuestionUtil.java       # AI 问题 Excel 导入 / 模板生成
   ├─ ExcelExportUtil.java         # AI 问题 / 回复 Excel 导出
   ├─ LogUtil.java                 # 彩色 HTML 日志
   └─ IconUtil.java                # SVG 图标渲染
```

单元测试位于 `src/test/java/com/omnikit/core/`，覆盖程序容器与在线更新核心逻辑。

## 打包发布

### 仅生成瘦 JAR

```bash
./gradlew jar copyLibs
```

生成后可通过以下命令运行（需保证 `lib/` 与 JAR 同目录，即位于 `build/libs/` 下）：

```bash
java -jar build/libs/omnikit-client-2.0.0.jar
```

### 生成 Windows .exe（自带 JRE + 依赖）

```bash
./gradlew jar
./gradlew createExe
```

构建过程中会自动从 Eclipse Temurin 下载 Windows x64 JRE（项目根目录存在 `jre.zip` 时优先使用本地文件），并解压到 `build/launch4j/jre` 目录。最终产物：

- Windows 可执行文件：`build/launch4j/OmniKit.exe`
- 内置 JRE 目录：`build/launch4j/jre/`
- 第三方依赖目录：`build/launch4j/lib/`（瘦 JAR 所需的运行时依赖）

运行 `OmniKit.exe` 时，程序会优先使用同目录下的 `jre` 文件夹，无需用户本机安装 JDK。

### 生成 ZIP 分发包

```bash
./gradlew packageZip
```

将 `OmniKit.exe`、`jre/` 与 `lib/` 打包为 `build/distributions/OmniKit-<version>-win-x64.zip`，解压即可运行。`./gradlew build` 会依次完成瘦 JAR、`.exe` 与 ZIP 分发包的全部构建。

### 生成依赖库升级包

```bash
./gradlew packageLib
```

生成两个固定命名的产物（不带版本号，供客户端在线更新按名查找）：

- `build/distributions/OmniKit-lib.zip`：`lib/` 下全部第三方依赖 JAR
- `build/distributions/OmniKit-lib.manifest`：依赖清单，每行为 `<jarName>\t<size>\t<sha256>`

客户端用清单与本地 `lib/` 逐文件比对，仅当依赖发生变化（新增依赖、依赖版本变化、依赖被移除）时才下载升级包，避免每次升级都重复下载几十MB 依赖。

## 版本发布

发布流程已完全自动化：推送 `vX.Y.Z` 格式的 Git Tag 即触发 GitHub Actions（[release.yml](.github/workflows/release.yml)）自动完成构建、校验与发布。

**发布步骤：**

1. 修改 `build.gradle` 中的 `version`（版本号会在构建时注入 `version.properties`，运行期由 `UiConstants` 动态读取，无需改代码）；
2. 在 [CHANGELOG.md](CHANGELOG.md) 中新增 `## [vX.Y.Z] - 日期` 小节（该小节将被自动提取为 GitHub Release 描述）；
3. 提交后打 Tag 并推送：`git tag -a vX.Y.Z -m "Release vX.Y.Z" && git push origin main vX.Y.Z`。

**CI 会自动校验并完成：**

- Tag 必须位于 `main` 分支历史中，且与 `build.gradle` 版本号一致；
- 从 CHANGELOG.md 提取对应版本小节作为 Release 描述；
- 构建 `.exe`、ZIP 分发包与依赖库升级包，生成 `OmniKit.exe.sha256`、`OmniKit-lib.zip.sha256` 校验文件，并作为 Release 资产发布。

**Release 资产清单（客户端在线更新依赖）：**

| 资产 | 用途 |
| --- | --- |
| `OmniKit.exe` | 在线更新的升级目标（必需） |
| `OmniKit.exe.sha256` | exe 下载完整性校验（可选，缺失时跳过校验） |
| `OmniKit-lib.zip` | 依赖库升级包（可选，缺失时仅升级 exe；新增/变更依赖时必需） |
| `OmniKit-lib.zip.sha256` | 依赖库升级包完整性校验（可选，缺失时跳过校验） |
| `OmniKit-lib.manifest` | 依赖库清单（与 zip 同时提供时才会启用依赖库升级） |
| `OmniKit-<version>-win-x64.zip` | 新用户完整分发包（解压即用） |
| `DataSync.exe` / `DataSync.exe.sha256` | 旧名兼容资产：与 `OmniKit.exe` 内容相同的副本，供改名前安装的旧版本在线更新（迁移期保留，可择期移除） |

> **改名迁移说明**：工具由 DataSync Client 更名为 OmniKit。改名前安装的旧版本程序内固定按 `DataSync.exe` 资产名查找升级包，CI 在迁移期会同时以旧名发布同一构建，旧版本可正常在线更新（更新后文件名仍为 DataSync.exe，下次手动重新安装为新目录结构即可）。

## 许可证

本项目采用 [LICENSE.md](LICENSE.md) 中声明的许可证。

## 参与贡献

欢迎提交 Issue 或 Pull Request 改进本项目。
