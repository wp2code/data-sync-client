# DataSync Client

DataSync Client 是一款基于 Java 17 + Swing 的桌面数据库同步工具，支持 **MySQL** 与 **PostgreSQL** 之间的全表数据同步与表结构差异同步。

![Java 17](https://img.shields.io/badge/Java-17-blue)
![Gradle](https://img.shields.io/badge/Build-Gradle-green)
![Swing](https://img.shields.io/badge/UI-Swing%20FlatLaf-orange)

## 功能特性

-  **数据源管理**：保存、编辑、测试多个 MySQL / PostgreSQL 数据源配置。
-  **表数据同步**：全量读取源表数据，以 Upsert（插入或更新）方式同步到目标表。
-  **表结构同步**：对比源表与目标表的列、索引差异，自动生成并执行 ALTER 脚本。
-  **批量传输**：500 行一批次执行插入，事务包裹，失败自动回滚。
-  **本地配置持久化**：数据源配置保存在本地 SQLite 文件 `datasource_config.db` 中。
-  **现代暗色 UI**：使用 FlatLaf 主题，界面简洁清晰。
-  **Windows 可执行文件**：支持打包为 `DataSync.exe`，双击即可运行。

## 技术栈

- Java 17
- Gradle 8.x
- FlatLaf 3.4.1
- MySQL Connector/J 8.3.0
- PostgreSQL JDBC 42.7.3
- SQLite JDBC 3.45.2.0
- Apache Batik（SVG 图标渲染）

## 快速开始

### 环境要求

- JDK 17 或更高版本
- Windows / Linux / macOS（打包 `.exe` 仅在 Windows 下执行）

### 构建运行

```bash
# 编译项目
./gradlew compileJava

# 运行程序
./gradlew run

# 完整构建（编译 + 测试 + Fat JAR + Windows .exe）
./gradlew build
```

Windows 环境下请将 `./gradlew` 替换为 `gradlew.bat`。

构建产物：

- Fat JAR：`build/libs/data-sync-client-1.0.0.jar`
- Windows 可执行文件：`build/launch4j/DataSync.exe`

## 使用说明

1. 打开程序后，点击 **管理数据源** 配置源数据库与目标数据库。
2. 返回主界面，分别在 **源数据库** 与 **目标数据库** 下拉框中选择已配置的数据源。
3. 选择源端 Schema（PostgreSQL）以及需要同步的表。
4. 点击 **开始同步** 进行数据同步。
5. 点击 **比较结构差异** 可查看列与索引差异，并执行 ALTER 脚本。

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
d:
├─src/main/java/com/datasync/
│  ├─Main.java                        # 程序入口
│  ├─components/                       # 自定义 Swing 组件
│  ├─core/                             # 数据库连接、实体、同步引擎
│  ├─ui/                               # 主界面与对话框
│  └─util/                             # 工具类与本地配置持久化
└─src/main/resources/                  # 图标资源（SVG/ICO）
```


## 打包发布

### 仅生成 Fat JAR

```bash
./gradlew jar
```

生成后可通过以下命令运行：

```bash
java -jar build/libs/data-sync-client-1.0.0.jar
```

### 生成 Windows .exe

```bash
./gradlew jar
./gradlew createExe
```

可执行文件位于：`build/launch4j/DataSync.exe`

> 注意：`.exe` 依赖于系统已安装的 JDK 17（或设置 `JAVA_HOME`）。

## 许可证

本项目采用 [LICENSE.md](LICENSE.md) 中声明的许可证。

## 参与贡献

欢迎提交 Issue 或 Pull Request 改进本项目。

