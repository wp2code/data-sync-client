# CODEBUDDY.md

This file provides guidance to CodeBuddy when working with code in this repository.

## Build & Run Commands

```bash
# Compile (Windows use gradlew.bat)
./gradlew compileJava

# Run the application
./gradlew run

# Full build: compile + tests + thin JAR + Windows .exe + ZIP distribution
./gradlew build

# Run unit tests only (JUnit 5)
./gradlew test

# Generate thin JAR + copy dependencies only
./gradlew jar copyLibs

# Generate Windows .exe only (requires jar first)
./gradlew jar && ./gradlew createExe

# Generate ZIP distribution only
./gradlew packageZip

# Run the thin JAR directly (requires build/libs/lib/ beside it)
java -jar build/libs/omnikit-client-2.0.0.jar
```

Build outputs:
- Thin JAR: `build/libs/omnikit-client-2.0.0.jar` (dependencies in `build/libs/lib/`, referenced via MANIFEST `Class-Path`)
- Windows .exe: `build/launch4j/OmniKit.exe` (bundled `jre/` and `lib/` directories)
- ZIP distribution: `build/distributions/OmniKit-2.0.0-win-x64.zip`

Unit tests (JUnit 5, `src/test/java/com/omnikit/core/`): `ProcessContainerServiceTest`, `UpdateServiceTest`, `UpdateServiceUpgradeTest`. Live GitHub API tests are tagged `live` and skipped by default; enable with `gradlew test -Domnikit.test.live=true`.

Runtime configuration (priority: system property > SQLite `app_config` > default):
- `-Domnikit.update.intervalHours=<n>` — auto-update check interval; `0` = check every launch
- `-Domnikit.update.githubToken=<token>` — GitHub API token (also injected via `githubToken` Gradle property into `version.properties`)

Note: the `smokeTestExcel` / `smokeTestFullscreen` Gradle tasks in `build.gradle` reference classes that no longer exist in the repo — delete or restore them before use.

Release: push a `vX.Y.Z` Git tag to trigger GitHub Actions (`.github/workflows/release.yml`), which validates tag-vs-`build.gradle` version, extracts the matching CHANGELOG.md section as the Release description, and publishes `OmniKit.exe`, `OmniKit.exe.sha256`, the ZIP distribution, plus a legacy exe-name alias asset (same build) so pre-rename clients keep auto-updating during the migration period.

## Architecture Overview

### Project Purpose

OmniKit (Gradle project name `omnikit-client`) is a Java 17 Swing desktop productivity tool (v2.0.0) that combines MySQL↔PostgreSQL data/schema syncing, GitLab API integration (gitlab4j), AI training (question/reply management over remote HTTP APIs), an external-program container (run Java/Python/other commands with live console output), and online auto-update via GitHub Releases.

### Package Structure & Key Classes

```
com.omnikit/  (src/main/java)
├── Main.java                        # Entry point: FlatDarkLaf setup, shutdown hook for child-process cleanup, UI on EDT
├── core/                            # Service layer — no UI code
│   ├── OmniKitService.java          # Core sync engine: upsert-based table sync (500-row batches), schema compare, DDL generation
│   ├── DbConnector.java             # JDBC connection factory + schema/table/column/index introspection
│   ├── GitLabService.java           # Singleton: OAuth2 login + file/commit ops (Guava caches: projects 2h, branches 2min)
│   ├── AiQuestionApiClient.java     # Singleton: AI question/reply HTTP client (per-env host + paths, POST+JSON, code=="0" = success)
│   ├── UpdateService.java           # GitHub Releases auto-update: check / download / SHA-256 / exe replace + restart
│   ├── ProcessContainerService.java # Singleton: external process lifecycle (start/stop/restart) + output log buffer
│   └── EncodingDetectReader.java    # Child-process output decoder (UTF-8 strict check → native.encoding fallback)
├── model/                           # Plain data holders (Lombok)
│   ├── DataSource / DbType / ConnectionWrapper     # DB config (host/port/user/pass/schema); enum MYSQL|POSTGRESQL; mutable JDBC conn wrapper
│   ├── Script / GitLabAuthConfig / FileParams / CommitParams    # GitLab integration
│   ├── AiQuestion / AiAnswer / AiEnvConfig / AiMetaConfig       # AI training
│   ├── ProgramConfig               # External program config (types: JAVA / PYTHON / OTHER)
│   └── ProjectItem / Side          # UI helpers (Side = SOURCE | TARGET)
├── ui/                              # All Swing windows and panels
│   ├── OmniKitUI.java               # Single main JFrame: sync controls, log area, DDL viewer, settings menu
│   ├── DataSourceManagerDialog.java # Data source CRUD + connection test
│   ├── GitLabMangerDialog.java      # GitLab auth config
│   ├── ScriptMangerDialog.java      # SQL script editor + push to GitLab
│   ├── AiQuestionMangerDialog.java  # AI training shell (tab switching, shared helpers)
│   ├── QuestionTrainingPanel.java   # Question tab: pagination, manual entry, Excel import, batch save/update/delete, trigger training
│   ├── AnswerAuditPanel.java        # Reply tab: paginated replies, view/update/delete
│   ├── AiEnvMangerDialog.java       # AI environment config (host, headers, API paths, JSON examples)
│   ├── ProgramContainerDialog.java  # External program container (list + config form + live console)
│   ├── TrainingCastTimeDialog.java  # Training duration query (auto-refresh)
│   ├── ButtonFactory.java           # Unified button styling (primary/destructive/secondary/toolbar/link/pill)
│   ├── AbsDialog.java               # Base dialog with centered positioning
│   └── UiConstants.java             # Colors, fonts, VERSION (from version.properties), GitHub addr, log tag constants
├── components/                      # Reusable custom Swing widgets
│   ├── CustomTextField.java         # JTextField with placeholder text
│   ├── FilterComboBox.java          # JComboBox with text filtering
│   ├── CheckBoxComboBox.java        # Combo box with checkable items
│   ├── LinkJLabel.java              # Clickable hyperlink label
│   ├── FullscreenJDialog.java       # Dialog with F11 fullscreen / ESC exit
│   ├── ChildLayoutPanel.java        # Left/center/right aligned panel
│   ├── OptionJPanel.java            # Selectable + hover-highlighted panel item
│   ├── DbTypeTableCellRenderer.java # DB-type icon renderer for tables
│   └── combobox/                    # IconJComboBox + IconItem + renderers (IconListRenderer, DbTypeListCellRenderer)
└── util/
    ├── SQLiteConfigUtil.java        # Singleton: SQLite persistence at {appDir}/data/datasource_config.db + idempotent migrations
    ├── ConfigUtil.java              # Static facade → SQLiteConfigUtil (UI code should prefer this)
    ├── ExcelQuestionUtil.java       # Question Excel import + runtime template generation (Apache POI)
    ├── ExcelExportUtil.java         # Question/reply list Excel export
    ├── LogUtil.java                 # Colored HTML log helpers (rendered by the JEditorPane log area)
    ├── IconUtil.java                # SVG → ImageIcon via Batik (16/24/32px), fallback icon drawing
    └── GlobalUtil.java              # Clipboard, SQL splitting, fullscreen toggling
```

`src/main/resources`: `version.properties` (version + GitHub token injected at build time), `logback.xml` (console + rolling file), `assets/` (`app.ico` + SVG icons for DB types / program types).

### Data Flow for Table Sync

1. User selects source/target data sources, schema, and table in `OmniKitUI`
2. `OmniKitService.syncTableWithConn()` is invoked (typically via `SwingWorker` for non-blocking UI)
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
2. `OmniKitService.compareTableStructure()` compares columns (name, type, nullable, default, comment) producing `ColumnDiff` objects with types: `ADD_COLUMN`, `DROP_COLUMN`, `MODIFY_COLUMN`, `COMMENT_DIFF`
3. `OmniKitService.compareIndexes()` compares indexes producing `IndexDiff` objects with types: `ADD_INDEX`, `DROP_INDEX`, `MODIFY_INDEX`
4. `generateAlterScript()` produces a complete ALTER TABLE script, sorted by operation type (ADD first, DROP last), with separate PostgreSQL `COMMENT ON COLUMN` statements
5. DDL is displayed in a fullscreen-capable `FullscreenJDialog`; users can copy or execute

### GitLab Integration

- `GitLabService` is a lazy-initialized singleton that authenticates via OAuth2 (`GitLabApi.oauth2Login()`)
- On first API call, it loads `GitLabAuthConfig` from SQLite and logs in automatically
- Uses **Guava Cache** for project lists (2-hour TTL) and branch lists (2-minute TTL)
- Supports: `createOrUpdateFile()` (auto-detects create vs update), `getFile()`, `getRawFile()`, `commit()` with `CommitAction`
- The `ScriptMangerDialog` lets users push SQL script content to a GitLab project/branch/file path

### AI Training

- `AiQuestionApiClient` (singleton) calls remote services per environment config (`AiEnvConfig`: host, common headers, per-API paths)
- API convention: POST + JSON, response envelope `{code:"0", message, data}` where `code=="0"` means success
- Features: paginated question list (training status filtered server-side), batch save/update/delete, trigger training, batch update training params; reply list/detail/update/delete for audit
- Excel import via `ExcelQuestionUtil` (template generated at runtime, no binary resource needed); export via `ExcelExportUtil`

### External Program Container

- `ProcessContainerService` (singleton) owns process sessions; `ProgramContainerDialog` is just a view — closing the dialog keeps processes running, reopening restores state badges and historical logs
- Supports program types `JAVA` (custom JRE/JDK), `PYTHON` (interpreter/venv, entry script prefixed), `OTHER` (bat/exe/PATH commands)
- Child process output is decoded by `EncodingDetectReader` (UTF-8 vs GBK detection)
- On app exit, a ShutdownHook in `Main` calls `stopAll()` unless the user chose to keep processes running (main window close dialog)

### Online Auto-Update

- `UpdateService` checks GitHub Releases (`UiConstants.GITHUB_ADDR` → api.github.com) at startup (silent) and via a manual "检查更新" button
- Release assets: `OmniKit.exe` (upgrade target, `EXE_NAME` constant), optional `OmniKit.exe.sha256` (integrity check), Release body shown as changelog; CI also publishes the same build under a legacy exe-name alias so pre-rename clients keep auto-updating during the migration period
- Update = full exe replacement (JRE dir stays); old exe renamed to `.old`, new exe launched, old process exits; delayed replacement via helper script when the exe is locked by antivirus
- Network errors retry with exponential backoff (max 3); partial downloads cleaned up
- Check interval and GitHub token configurable via system properties (see Runtime configuration above), falling back to the SQLite `app_config` table

### SQLite Persistence (SQLiteConfigUtil)

- Database file: `{appDir}/data/datasource_config.db`
- Tables: `data_source_config`, `script_config`, `gitlab_config`, plus AI env config and program container tables
- Schema migrations are handled by `ALTER TABLE ... ADD COLUMN` in `initialize()` with try/catch for idempotency (tolerates "column already exists" errors)
- `ConfigUtil` is a static facade that UI code should prefer; services use `SQLiteConfigUtil.getInstance()` directly

### Threading Model

- **Swing Event Dispatch Thread (EDT)**: All UI creation and updates occur here via `SwingUtilities.invokeLater()`
- **Background tasks**: table syncing, schema comparison, connection testing, AI API calls all use `SwingWorker<T, String>` to keep the UI responsive. `SwingWorker.publish()` sends log messages; `SwingWorker.process()` appends them to the UI log area
- **Connection reuse**: `ConnectionWrapper` objects (marked `volatile`) hold active source/target connections, allowing multiple operations without reconnecting

### UI Architecture

- **FlatLaf FlatDarkLaf** theme applied in `Main.main()` before any UI creation, with focus ring width 1.5px, component arc 6px, and global text antialiasing for CJK rendering
- `OmniKitUI` is the single main `JFrame`. All other windows are `JDialog` subclasses extending `AbsDialog` or `FullscreenJDialog`
- The log output area is a `JEditorPane` rendering HTML content — `LogUtil` provides helpers that generate colored HTML spans
- Buttons are created via `ButtonFactory` for consistent styling
- SVG icons are rendered via Apache Batik in `IconUtil` to produce `ImageIcon` objects at various sizes (16x16, 24x24, 32x32)
- Fullscreen-capable dialogs (`FullscreenJDialog`) support `F11` to toggle fullscreen and `ESC` to close

### Build & Packaging

- Gradle 8.x with `application` plugin (main class: `com.omnikit.Main`)
- Java compilation targets Java 17 with `-Xlint:unchecked` and UTF-8 encoding (avoids Windows GBK compile issues)
- Thin JAR via `jar` task: MANIFEST `Class-Path` points to `lib/*.jar` generated by the `copyLibs` task
- **Launch4j** (`edu.sc.seis.launch4j` 3.0.5) generates `OmniKit.exe` from the JAR; `downloadJre` task downloads Eclipse Temurin JRE 17 (uses local `jre.zip` if present) into `build/launch4j/jre`
- `packageZip` task bundles exe + jre + lib into a ready-to-run ZIP
- Version number lives in `build.gradle`, injected into `version.properties` at build time and read at runtime by `UiConstants` (GitHub token also injected via `githubToken` Gradle property)
- Key dependency versions: FlatLaf 3.4.1, MySQL Connector/J 8.3.0, PostgreSQL JDBC 42.7.3, SQLite JDBC 3.45.2.0, gitlab4j-api 6.3.0, Guava 33.6.0-jre, Jackson 2.17.2, Apache POI 5.2.5, Batik 1.18, Logback 1.5.6, Lombok 1.18.32, JUnit 5.10.2

### Gotchas

- On Windows run `gradlew.bat`; the project, packages and all identifiers are `omnikit`
- Source/dest connections are cached in `ConnectionWrapper` fields on `OmniKitUI`; changing data source or database, or using "刷新连接" (`refreshConnection`), closes the old connection and reconnects — stale connections after editing a data source config are the first suspect for sync failures
- Chinese text must stay UTF-8 — files are compiled with `-Dfile.encoding`-safe UTF-8 options; don't let editors save Java sources as GBK
- `ScriptMangerDialog` / `GitLabMangerDialog` / `AiEnvMangerDialog` filenames keep the historical "Manger" (sic) spelling — don't "fix" it, references exist across UI code
