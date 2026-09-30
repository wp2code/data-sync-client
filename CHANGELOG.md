# 更新日志 / Changelog

所有显著变更记录于此文件。格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)。

发布新版本时：在下方新增一节 `## [vX.Y.Z] - 日期`，与 Git Tag、`build.gradle` 的 `version`、`UiConstants.VERSION` 保持一致，Release 描述将自动取自对应小节。

## [v1.1.0] - 2026-09-30

### 新增
- 基于 GitHub Releases 的在线自动更新：启动时自动检查新版本（静默模式，发现新版本才提示），支持下载进度展示、SHA-256 校验、自动替换 exe 并重启，JRE 不随版本升级
- 日志面板新增「检查更新」按钮，手动检查当前是否为最新版本

### 变更
- 升级通道改为 GitHub Releases API，Release 描述正文即版本日志，随升级提示一并展示

## [v1.0.0] - 2026-09-30

### 新增
- MySQL / PostgreSQL 全表数据同步（批量 UPSERT，单事务提交）
- 表结构对比与 ALTER TABLE DDL 脚本生成
- 数据源配置管理（SQLite 本地持久化）
- GitLab 集成：OAuth2 登录、SQL 脚本推送、提交管理
- AI 问题管理（Excel 导入/导出、双击全屏）
- 基于 GitHub Releases 的在线自动更新（JRE 不随版本升级）
