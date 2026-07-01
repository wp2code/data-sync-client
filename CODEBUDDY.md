# CODEBUDDY.md This file provides guidance to CodeBuddy when working with code in this repository.

## Build & Development Commands

**Build the project (compile + run tests + package Fat JAR + create Windows .exe):**
```bash
./gradlew build
```
On Windows, use `gradlew.bat build`. The build produces a Fat JAR at `build/libs/data-sync-client-1.0.0.jar` and a Windows `.exe` at `build/launch4j/DataSync.exe`.

**Compile only (skip tests and packaging):**
```bash
./gradlew compileJava
```

**Package only the Fat JAR (with all dependencies bundled):**
```bash
./gradlew jar
```

**Create the Windows .exe only (requires prior `jar` task):**
```bash
./gradlew createExe
```

**Run the application directly via Gradle:**
```bash
./gradlew run
```

**Clean build artifacts:**
```bash
./gradlew clean
```

**List all available Gradle tasks:**
```bash
./gradlew tasks
```

There are no unit tests configured in this project. The Gradle build uses Java 17 with source/target compatibility set to 17. All compilation uses UTF-8 encoding with `-Xlint:unchecked` flag.

## Architecture Overview

### Purpose
DataSync Client is a Java 17 Swing desktop application for **full-table data synchronization** and **table structure synchronization** between MySQL and PostgreSQL databases. It provides a dark-themed FlatLaf UI with connection management, table listing, batch data transfer with upsert semantics, and structural diff generation with ALTER script execution.

### Package Structure

```
com.datasync/                    (root: Main entry point)
├── components/                  (Reusable Swing UI widgets)
├── core/                        (Business logic: entities, connectors, sync engine)
├── ui/                          (Swing windows/dialogs)
└── util/                        (Persistence layer and global state)
```

### Entry Point & Lifecycle

`Main.java` is the sole entry point. It applies `FlatDarkLaf` theme, tweaks UI defaults (focus width, corner arc), then creates and displays `DataSyncUI` on the EDT via `SwingUtilities.invokeLater()`. The application stores data source configurations in a local SQLite file (`datasource_config.db`) in the working directory.

### Core Domain Model

**`DataSource`** — The central entity. Represents a database connection configuration with fields: `sourceName` (unique identifier), `dbType` (mysql/postgresql), `host`, `port`, `dbName`, `schema` (PostgreSQL only, defaults to "public"), `username`, `password`. Key methods:
- `buildJdbcUrl()` — Auto-generates the correct JDBC URL string based on `dbType`. MySQL includes timezone and SSL parameters; PostgreSQL appends `?currentSchema=` when schema is set.
- `getDriverClassName()` — Returns the appropriate JDBC driver class.
- `getDefaultPort()` — Returns 3306 for MySQL, 5432 for PostgreSQL.

**`ConnectionWrapper`** — A simple pairing of a `DataSource` config with an open `java.sql.Connection`. Used to pass pre-established connections around the application, avoiding repeated connect/disconnect cycles when the same database is used for multiple operations (e.g., table listing then data sync).

### Persistence Layer

**`SQLiteConfigUtil`** (singleton) — The bottom-level persistence layer. Manages a local SQLite database file with a `data_source_config` table. Provides CRUD operations: `saveDataSource`, `loadAllSourceNames`, `loadDataSourceByName`, `updateDataSource`, `deleteDataSource`. The table schema mirrors `DataSource` fields plus `create_time`/`update_time` timestamps. Includes migration logic to add `schema_name` column for backward compatibility.

**`ConfigUtil`** — A stateless facade/utility class that delegates all persistence calls to `SQLiteConfigUtil.getInstance()`. Also provides log formatting utilities (`logTimestamp`, `appendLog`, `clearLog`) that format timestamped log messages and render them into a `JEditorPane`. Contains `createAppIcon()` which programmatically draws a 64x64 blue-purple gradient icon with sync arrows.

**`GlobalUtil`** — Holds in-memory global state: a `Map<String, DataSource>` (size 2) with keys "SOURCE" and "TARGET" to track which data source configurations are currently selected as source and target in the UI. This allows the `DataSourceManagerDialog` to communicate selections back to `DataSyncUI` without direct coupling.

### Database Connectivity

**`DbConnector`** — Static utility class for all JDBC operations. No instances; all methods are static. Responsibilities:
- `getConnection(DataSource)` — Creates a JDBC connection via `DriverManager`, loading the driver class dynamically.
- `testConnection(DataSource)` — Tests connectivity and returns a human-readable result string.
- `fetchSchemas(DataSource)` — For PostgreSQL, retrieves all non-system schemas (always includes "public").
- `fetchTables(DataSource, schema)` — Lists user tables. Uses `SHOW FULL TABLES` for MySQL (to avoid catalog parameter issues) and JDBC metadata API for PostgreSQL.
- `fetchColumns(DataSource, tableName, schema)` — Lists column names for a table.
- `fetchColumnDetails(DataSource, tableName, schema)` — Returns full `ColumnDetail` objects including data type, nullability, default value, primary key status, auto-increment status, and comment.
- `fetchIndexes(DataSource, tableName, schema)` — Returns `IndexDetail` objects with index name, column, uniqueness, ordinal position, and sort direction. Excludes PRIMARY key indexes.
- `fetchAutoIncrementColumn(DataSource, tableName, schema)` — Finds the auto-increment column if any.

Contains two inner data classes: **`ColumnDetail`** (column metadata) and **`IndexDetail`** (index metadata), both used extensively by the structure comparison logic.

### Data Synchronization Engine

**`DataSyncService`** — The core business logic class. Key capabilities:

1. **Full-table data sync** (`syncTableWithConn`): Accepts source/target `DataSource` configs and optional pre-existing `ConnectionWrapper` objects. Workflow:
   - Reuses existing connections or creates new ones via `DbConnector.getConnection()`
   - Optionally truncates the target table before sync
   - Executes `SELECT *` on source, reads `ResultSetMetaData` to discover columns dynamically
   - Builds an upsert INSERT statement: MySQL uses `ON DUPLICATE KEY UPDATE`, PostgreSQL uses `ON CONFLICT (pk_cols) DO UPDATE SET`. For PostgreSQL, primary key columns are discovered via `DatabaseMetaData.getPrimaryKeys()` to build the conflict target.
   - Batches inserts (500 rows per batch) within a transaction, commits on success, rolls back on error
   - Only closes self-created connections in `finally`; re-used connections are left open for subsequent operations
   - Reports progress via a `Consumer<String>` callback for UI log output

2. **INSERT script export** (`exportInsertScript`): Generates a SQL script file of INSERT statements for a given table, with proper value formatting (numeric types unquoted, strings escaped and single-quoted, NULL for null values).

3. **Table structure comparison** (`compareTableStructure`, `compareIndexes`): Compares source and target table schemas, producing lists of `ColumnDiff` and `IndexDiff` objects. Detects four column difference types: `ADD_COLUMN` (missing in target), `DROP_COLUMN` (extra in target), `MODIFY_COLUMN` (type/size/nullable/default differs), `COMMENT_DIFF` (only the comment/remark differs). Detects three index difference types: `ADD_INDEX`, `DROP_INDEX`, `MODIFY_INDEX`.

4. **ALTER script generation** (`generateAlterScript`): Takes difference lists and produces a complete ALTER TABLE script. Handles MySQL vs PostgreSQL syntax differences (backtick vs double-quote identifiers, `MODIFY COLUMN` vs `ALTER COLUMN ... TYPE / SET NOT NULL`, `COMMENT ON COLUMN` as separate statements in PostgreSQL). Column operations are ordered: ADD first, then MODIFY/COMMENT, then DROP. Index operations: DROP first, then ADD.

### UI Architecture

**`DataSyncUI` (JFrame)** — The main application window (~980x780). Layout structure:
- **Top panel**: Source database selection (combo box + info label) and target database selection. A "管理数据源" button opens the `DataSourceManagerDialog`.
- **Middle panel**: Schema selector (PostgreSQL only), table list with checkboxes (scrollable panel with search filter via `CustomTextField`), sync controls (truncate checkbox, "开始同步" button, "比较结构差异" `LinkJLabel`).
- **Bottom panel**: Scrollable log output area (`JEditorPane`).

Key UI behaviors:
- Selecting a data source combo triggers connection establishment and table/schema loading via background threads (`SwingWorker`-style with `new Thread()`).
- The table list panel dynamically renders checkboxes for each table found, with a search filter that hides non-matching tables.
- The "比较结构差异" link opens a non-modal `JDialog` showing side-by-side column and index differences between source and target for the selected table, with an "执行 ALTER" button that applies the generated DDL.
- Structure sync ALTER execution handles PostgreSQL's multi-statement `ALTER TABLE` by splitting on `\n    ` (4-space indent delimiter) and re-prefixing each sub-statement with `ALTER TABLE <tableName>`.
- Data sync runs in a background thread with periodic UI updates via `SwingUtilities.invokeLater()`.

**`DataSourceManagerDialog` (JDialog)** — A modal dialog for CRUD management of saved data source configurations. Features:
- A `JTable` with `AbstractTableModel` showing all saved sources
- An edit form with fields for all `DataSource` properties
- Dynamic showing/hiding of the Schema field based on database type selection
- "测试连接" button that tests connectivity in a background thread
- Save logic handles three cases: new insert, same-name update, and name-change (delete old + insert new)

### Custom Components

**`CustomTextField`** — A `JTextField` subclass that displays placeholder text when empty and unfocused. Listens to focus and document changes to toggle placeholder visibility, rendering it in gray via overridden `paintComponent()`.

**`LinkJLabel`** — A `JLabel` subclass that behaves as a hyperlink: opens URLs in the system browser via `Desktop.browse()`, changes color on hover (Google Blue → darker blue), and tracks visited state (turns purple after click).

### Data Flow for a Typical Sync Operation

1. User selects source/target data sources from combo boxes → `DataSyncUI` creates `ConnectionWrapper` objects and loads table lists
2. User selects a source schema and checks tables to sync, optionally checks "truncate before sync"
3. User clicks "开始同步" → a background thread calls `syncService.syncTableWithConn()`
4. The service connects to both databases (reusing existing connections), optionally truncates target, reads all source data, builds upsert INSERT, batch-inserts with transaction, and reports progress
5. Results appear in the log area; connections are kept open for subsequent syncs

### Key Design Decisions

- **Connection reuse**: Once a connection is established for table browsing, it is reused for data sync operations rather than creating new connections. `ConnectionWrapper` tracks whether a connection is self-created so the service knows whether to close it.
- **Database-agnostic SQL generation**: The code generates different SQL for MySQL and PostgreSQL at multiple levels — JDBC URL format, identifier quoting (backtick vs double-quote), upsert syntax, ALTER TABLE syntax, and comment handling.
- **No ORM**: All database access uses raw JDBC. SQLite for local persistence also uses raw JDBC with no ORM layer.
- **Thread safety**: UI updates always happen on the EDT via `SwingUtilities.invokeLater()`. Database operations run on background threads. The `srcConn`/`tgtConn` fields are `volatile`.
- **Graceful degradation**: Most `DbConnector` methods catch exceptions and return empty lists rather than propagating errors upward, allowing the UI to remain functional even when database metadata queries fail.
