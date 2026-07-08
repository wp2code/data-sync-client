# CODEBUDDY.md This file provides guidance to CodeBuddy when working with code in this repository.

## Build & Run Commands

```bash
# Compile (Windows use gradlew.bat)
./gradlew compileJava

# Run the application
./gradlew run

# Full build: compile + tests + Fat JAR + Windows .exe
./gradlew build

# Generate Fat JAR only
./gradlew jar

# Generate Windows .exe only (requires Fat JAR first)
./gradlew jar && ./gradlew createExe

# Run the Fat JAR directly
java -jar build/libs/data-sync-client-1.0.0.jar
```

Build outputs:
- Fat JAR: `build/libs/data-sync-client-1.0.0.jar`
- Windows .exe: `build/launch4j/DataSync.exe`

There are no unit tests configured in this project. `./gradlew build` compiles and produces the executable artifacts but does not run test suites.

## Architecture Overview

### Project Purpose

DataSync Client is a Java 17 Swing desktop application for syncing full table data and schema structures between **MySQL** and **PostgreSQL** databases. It also includes GitLab API integration (via gitlab4j) for managing SQL script files in repositories.

### Package Structure & Key Classes

```
com.datasync/
├── Main.java                          # Entry point: loads FlatLaf FlatDarkLaf, launches UI on EDT
├── core/
│   ├── DataSyncService.java           # Core sync engine: data sync, schema compare, DDL generation
│   ├── DbConnector.java               # JDBC connection factory + schema introspection
│   └── GitLabService.java             # Singleton, GitLab OAuth2 login + file/commit operations
├── model/
│   ├── DataSource.java                # DB connection config (host/port/user/pass/schema)
│   ├── DbType.java                    # Enum: MYSQL, POSTGRESQL (with driver class, default port)
│   ├── ConnectionWrapper.java         # Mutable wrapper for a JDBC Connection
│   ├── Script.java                    # SQL script config (content + optional GitLab target)
│   ├── GitLabAuthConfig.java          # GitLab OAuth2 credentials
│   ├── FileParams.java / CommitParams.java  # GitLab file/commit operation parameters
│   ├── ProjectItem.java / Side.java   # UI model helpers
├── ui/
│   ├── DataSyncUI.java               # Main JFrame (~2276 lines): sync controls, log output, DDL viewer
│   ├── DataSourceManagerDialog.java   # CRUD dialog for data source configs
│   ├── GitLabMangerDialog.java        # GitLab auth config dialog
│   ├── ScriptMangerDialog.java        # SQL script management dialog (edit + push to GitLab)
│   ├── AbsDialog.java                # Base dialog with centered positioning
│   └── UiConstants.java              # Colors, fonts, version string, placeholder text
├── components/                        # Custom Swing widgets
│   ├── CustomTextField.java           # JTextField with placeholder text
│   ├── FilterComboBox.java            # JComboBox with text filtering
│   ├── LinkJLabel.java                # Clickable hyperlink label
│   ├── FullscreenJDialog.java         # Dialog with F11 fullscreen / ESC exit
│   ├── ChildLayoutPanel.java          # Left/center/right aligned panel
│   ├── OptionJPanel.java              # Selectable + hover-highlighted panel item
│   └── combobox/                      # Icon-based combo box and renderers
├── util/
│   ├── SQLiteConfigUtil.java          # Singleton: SQLite persistence for configs + backward-compatible migrations
│   ├── ConfigUtil.java                # Static facade delegating to SQLiteConfigUtil
│   ├── LogUtil.java                   # HTML-formatted log helpers (colored success/error/info)
│   ├── IconUtil.java                  # SVG-to-PNG conversion via Batik, fallback icon drawing
│   └── GlobalUtil.java                # Clipboard, SQL splitting, fullscreen toggling
```

### Data Flow for Table Sync

1. User selects source/target data sources, schema, and table in `DataSyncUI`
2. `DataSyncService.syncTableWithConn()` is invoked (typically via `SwingWorker` for non-blocking UI)
3. Connections are established or reused via `DbConnector.getConnection()`
4. Source data is read via `SELECT *` from source table
5. A dynamic INSERT/UPSERT SQL is built based on target DB type:
   - **MySQL**: `INSERT INTO ... ON DUPLICATE KEY UPDATE col=VALUES(col)`
   - **PostgreSQL**: `INSERT INTO ... ON CONFLICT (pk_cols) DO UPDATE SET col=EXCLUDED.col`
6. Data is inserted in **500-row batches** within a single transaction
7. On any exception, the transaction is rolled back; on success, committed
8. All progress is fed to the UI via a `Consumer<String>` log callback

### Schema Comparison & DDL Generation

1. `DbConnector.fetchColumnDetails()` and `fetchIndexes()` retrieve metadata from both source and target
2. `DataSyncService.compareTableStructure()` compares columns (name, type, nullable, default, comment) producing `ColumnDiff` objects with types: `ADD_COLUMN`, `DROP_COLUMN`, `MODIFY_COLUMN`, `COMMENT_DIFF`
3. `DataSyncService.compareIndexes()` compares indexes producing `IndexDiff` objects with types: `ADD_INDEX`, `DROP_INDEX`, `MODIFY_INDEX`
4. `generateAlterScript()` produces a complete ALTER TABLE script, sorted by operation type (ADD first, DROP last), with separate PostgreSQL `COMMENT ON COLUMN` statements
5. DDL is displayed in a fullscreen-capable `FullscreenJDialog`; users can copy or execute

### GitLab Integration

- `GitLabService` is a lazy-initialized singleton that authenticates via OAuth2 (`GitLabApi.oauth2Login()`)
- On first API call, it loads `GitLabAuthConfig` from SQLite and logs in automatically
- Uses **Guava Cache** for project lists (2-hour TTL) and branch lists (2-minute TTL)
- Supports: `createOrUpdateFile()` (auto-detects create vs update), `getFile()`, `getRawFile()`, `commit()` with `CommitAction`
- The `ScriptMangerDialog` lets users push SQL script content to a GitLab project/branch/file path

### SQLite Persistence (SQLiteConfigUtil)

- Database file: `{appDir}/data/datasource_config.db`
- Three tables: `data_source_config`, `script_config`, `gitlab_config`
- Schema migrations are handled by `ALTER TABLE ... ADD COLUMN` in `initialize()` with try/catch for idempotency (tolerates "column already exists" errors)
- `ConfigUtil` is a static facade that UI code should prefer; `SQLiteConfigUtil.getInstance()` is used by `GitLabService` directly for auto-login

### Threading Model

- **Swing Event Dispatch Thread (EDT)**: All UI creation and updates occur here via `SwingUtilities.invokeLater()`
- **Background tasks**: Data sync, schema comparison, connection testing all use `SwingWorker<T, String>` to keep the UI responsive. `SwingWorker.publish()` sends log messages; `SwingWorker.process()` appends them to the UI log area
- **Connection reuse**: `ConnectionWrapper` objects (marked `volatile`) hold active source/target connections, allowing multiple operations without reconnecting

### UI Architecture

- **FlatLaf FlatDarkLaf** theme applied in `Main.main()` before any UI creation, with focus ring width 1.5px and component arc 6px
- `DataSyncUI` is the single main `JFrame`. All other windows are `JDialog` subclasses extending `AbsDialog`
- The log output area is a `JEditorPane` rendering HTML content — `LogUtil` provides helpers that generate colored HTML spans (`<span style='color:green'>`, etc.)
- SVG icons are rendered via Apache Batik in `IconUtil` to produce `ImageIcon` objects at various sizes (16x16, 24x24, 32x32)
- The DDL/SQL viewer dialogs use `FullscreenJDialog` which supports `F11` to toggle fullscreen and `ESC` to close

### Build & Packaging

- Gradle 8.x with `application` plugin (main class: `com.datasync.Main`)
- Java compilation targets Java 17 with `-Xlint:unchecked` and UTF-8 encoding
- Fat JAR via `jar` task: includes all runtime dependencies, excludes signature files
- **Launch4j** (`edu.sc.seis.launch4j`) generates `DataSync.exe` from the Fat JAR
- A custom `downloadJre` task downloads Eclipse Temurin JRE 17 for Windows x64 bundling
- Key dependency versions: FlatLaf 3.4.1, MySQL Connector/J 8.3.0, PostgreSQL JDBC 42.7.3, SQLite JDBC 3.45.2.0, gitlab4j-api 6.3.0, Guava 33.6.0, Batik 1.18, Logback 1.5.6, Lombok 1.18.32
