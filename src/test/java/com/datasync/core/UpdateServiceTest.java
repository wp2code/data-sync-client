package com.datasync.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.datasync.ui.UiConstants;
import com.datasync.util.SQLiteConfigUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * UpdateService 单元测试：版本比较、API 地址推导、节流逻辑。
 * <p>节流相关用例会修改本地 app_config 配置，测试前后自动备份恢复，不污染本机配置。</p>
 * <p>GitHub 真实接口的连通性测试标记为 live，默认跳过，需要时执行：
 * {@code gradlew test -Ddatasync.test.live=true}</p>
 */
@DisplayName("UpdateService 在线升级服务")
class UpdateServiceTest {

    private static final String KEY_INTERVAL = UpdateService.CONFIG_KEY_CHECK_INTERVAL_HOURS;
    private static final String KEY_LAST_CHECK = UpdateService.CONFIG_KEY_LAST_CHECK_TIME;

    private static String savedInterval;
    private static String savedLastCheck;

    @BeforeAll
    static void initDb() {
        // 确保测试工作目录下 app_config 表存在（CI 全新环境首次运行时建表）
        SQLiteConfigUtil.getInstance().initialize();
        // 备份本机原有配置，测试结束后恢复
        savedInterval = SQLiteConfigUtil.getInstance().getAppConfig(KEY_INTERVAL, null);
        savedLastCheck = SQLiteConfigUtil.getInstance().getAppConfig(KEY_LAST_CHECK, null);
    }

    @AfterAll
    static void restoreDb() {
        SQLiteConfigUtil sqlite = SQLiteConfigUtil.getInstance();
        if (savedInterval != null) {
            sqlite.setAppConfig(KEY_INTERVAL, savedInterval);
        }
        if (savedLastCheck != null) {
            sqlite.setAppConfig(KEY_LAST_CHECK, savedLastCheck);
        }
    }

    @AfterEach
    void cleanUpSystemProperty() {
        System.clearProperty("datasync.update.intervalHours");
    }

    // ────────── 版本比较 ──────────

    @Test
    @DisplayName("isNewerVersion：常规三段版本号比较")
    void testNewerVersion() {
        assertTrue(UpdateService.isNewerVersion("v1.1.0", "v1.0.0"), "次版本号更大应为新版本");
        assertTrue(UpdateService.isNewerVersion("v1.0.1", "v1.0.0"), "修订号更大应为新版本");
        assertTrue(UpdateService.isNewerVersion("v2.0.0", "v1.9.9"), "主版本号更大应为新版本");
        assertFalse(UpdateService.isNewerVersion("v1.0.0", "v1.0.0"), "相同版本不算新版本");
        assertFalse(UpdateService.isNewerVersion("v1.0.0", "v1.0.1"), "更小版本不算新版本");
        assertFalse(UpdateService.isNewerVersion("v1.0.0", "v1.0"), "等价版本（1.0.0 == 1.0）不算新版本");
    }

    @Test
    @DisplayName("isNewerVersion：v 前缀可有可无，大小写不敏感")
    void testNewerVersionPrefix() {
        assertTrue(UpdateService.isNewerVersion("1.2.0", "v1.1.0"), "远端无 v 前缀也能正确比较");
        assertTrue(UpdateService.isNewerVersion("V1.2.0", "v1.1.0"), "远端大写 V 前缀也能正确比较");
    }

    @Test
    @DisplayName("isNewerVersion：位数不足的版本号按 0 补齐比较")
    void testNewerVersionDifferentLength() {
        assertTrue(UpdateService.isNewerVersion("v1.1", "v1.0.9"), "1.1 等价于 1.1.0，应大于 1.0.9");
        assertFalse(UpdateService.isNewerVersion("v1.0", "v1.0.1"), "1.0 等价于 1.0.0，应小于 1.0.1");
    }

    @Test
    @DisplayName("isNewerVersion：非法输入按 0 处理不抛异常")
    void testNewerVersionInvalidInput() {
        assertFalse(UpdateService.isNewerVersion("abc", "abc"), "完全非法的版本视为相等");
        assertTrue(UpdateService.isNewerVersion("v1.0.0", null), "本地版本为 null 时任何远端版本都算新");
        assertFalse(UpdateService.isNewerVersion(null, "v1.0.0"), "远端版本为 null 时不触发升级");
    }

    // ────────── API 地址推导 ──────────

    @Test
    @DisplayName("REPO_API：由 GITHUB_ADDR 正确推导 GitHub API 前缀")
    void testRepoApiDerivation() {
        assertEquals("https://api.github.com/repos/wp2code/data-sync-client", UpdateService.REPO_API,
                "github.com 前缀应被替换为 api.github.com/repos/");
        assertTrue(UpdateService.LATEST_RELEASE_API.endsWith("/releases/latest"),
                "最新 Release API 应以 /releases/latest 结尾");
        assertTrue(UpdateService.LATEST_RELEASE_API.startsWith(UpdateService.REPO_API),
                "LATEST_RELEASE_API 应基于 REPO_API 构建");
    }

    @Test
    @DisplayName("REPO_API：与 UiConstants.GITHUB_ADDR 保持一致")
    void testRepoApiConsistency() {
        String expected = UiConstants.GITHUB_ADDR.replace("https://github.com/", "https://api.github.com/repos/");
        assertEquals(expected + "/releases/latest", UpdateService.LATEST_RELEASE_API,
                "仓库地址变更时应同步检查推导逻辑");
    }

    // ────────── 节流配置 ──────────

    @Test
    @DisplayName("getCheckIntervalHours：默认间隔常量为 24 小时")
    void testDefaultIntervalConstant() {
        assertEquals(24, UpdateService.DEFAULT_CHECK_INTERVAL_HOURS, "默认间隔常量应为 24");
    }

    @Test
    @DisplayName("getCheckIntervalHours：本地 app_config 配置生效")
    void testConfiguredInterval() {
        SQLiteConfigUtil.getInstance().setAppConfig(KEY_INTERVAL, "8");
        assertEquals(8, UpdateService.getCheckIntervalHours(), "本地配置的间隔应被读取");
    }

    @Test
    @DisplayName("getCheckIntervalHours：启动参数优先于本地配置")
    void testSystemPropertyOverridesConfig() {
        SQLiteConfigUtil.getInstance().setAppConfig(KEY_INTERVAL, "8");
        System.setProperty("datasync.update.intervalHours", "6");
        assertEquals(6, UpdateService.getCheckIntervalHours(), "启动参数应优先于本地配置");
    }

    @Test
    @DisplayName("getCheckIntervalHours：非法启动参数回退到本地配置")
    void testInvalidIntervalProperty() {
        SQLiteConfigUtil.getInstance().setAppConfig(KEY_INTERVAL, "8");
        System.setProperty("datasync.update.intervalHours", "abc");
        assertEquals(8, UpdateService.getCheckIntervalHours(), "非法参数应被忽略并回退到本地配置");
    }

    // ────────── 节流判断 ──────────

    @Test
    @DisplayName("shouldAutoCheck：间隔为 0 时总是检查")
    void testShouldAutoCheckAlways() {
        System.setProperty("datasync.update.intervalHours", "0");
        assertTrue(UpdateService.shouldAutoCheck(), "间隔为 0 表示每次启动都检查");
    }

    @Test
    @DisplayName("shouldAutoCheck：负数间隔视为总是检查")
    void testShouldAutoCheckNegative() {
        System.setProperty("datasync.update.intervalHours", "-1");
        assertTrue(UpdateService.shouldAutoCheck(), "负数间隔视为总是检查");
    }

    @Test
    @DisplayName("shouldAutoCheck：距上次检查未超过间隔时跳过")
    void testShouldAutoCheckThrottled() {
        System.setProperty("datasync.update.intervalHours", "24");
        SQLiteConfigUtil.getInstance().setAppConfig(KEY_LAST_CHECK,
                String.valueOf(System.currentTimeMillis() - 3_600_000)); // 1 小时前检查过
        assertFalse(UpdateService.shouldAutoCheck(), "1 小时前刚检查过，间隔 24 小时内应跳过");
    }

    @Test
    @DisplayName("shouldAutoCheck：距上次检查超过间隔时允许检查")
    void testShouldAutoCheckExpired() {
        System.setProperty("datasync.update.intervalHours", "1");
        SQLiteConfigUtil.getInstance().setAppConfig(KEY_LAST_CHECK,
                String.valueOf(System.currentTimeMillis() - 7_200_000)); // 2 小时前检查过
        assertTrue(UpdateService.shouldAutoCheck(), "超过间隔后应允许检查");
    }

    @Test
    @DisplayName("shouldAutoCheck：无检查记录或记录非法时视为首次")
    void testShouldAutoCheckFirstTime() {
        System.setProperty("datasync.update.intervalHours", "24");
        SQLiteConfigUtil.getInstance().setAppConfig(KEY_LAST_CHECK, "not-a-number");
        assertTrue(UpdateService.shouldAutoCheck(), "记录非法时应视为从未检查过");
    }

    @Test
    @DisplayName("markChecked：记录后节流立即生效")
    void testMarkChecked() {
        System.setProperty("datasync.update.intervalHours", "24");
        long before = System.currentTimeMillis();
        UpdateService.markChecked();
        String recorded = SQLiteConfigUtil.getInstance().getAppConfig(KEY_LAST_CHECK, "0");
        long recordedMillis = Long.parseLong(recorded);
        assertTrue(recordedMillis >= before, "记录时间应为当前时间附近");
        assertFalse(UpdateService.shouldAutoCheck(), "刚记录过检查时间，24 小时内应跳过");
    }

    // ────────── 实例与路径定位 ──────────

    @Test
    @DisplayName("create()：返回可用实例")
    void testCreateInstance() {
        assertNotNull(UpdateService.create(), "工厂方法应返回非空实例");
    }

    @Test
    @DisplayName("currentExePath：安全执行不抛异常")
    void testCurrentExePathInDev() {
        // gradlew test 运行在项目目录，通常不存在 DataSync.exe，应安全返回 null；存在则返回路径
        UpdateService.create().currentExePath();
        // 不做断言，仅验证不抛异常
    }

    // ────────── live 集成测试（默认跳过） ──────────

    @Test
    @Tag("live")
    @DisplayName("checkForUpdate：真实请求 GitHub 最新 Release（-Ddatasync.test.live=true 启用）")
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "datasync.test.live", matches = "true")
    void testCheckForUpdateLive() throws Exception {
        UpdateService.UpdateInfo info = UpdateService.create().checkForUpdate();
        assertNotNull(info, "应返回 Release 信息");
        assertTrue(info.version().matches("v?\\d+\\.\\d+.*"), "版本号格式应符合 Tag 规范: " + info.version());
        assertTrue(info.url().startsWith("https://"), "下载地址应为 HTTPS: " + info.url());
        System.out.println("最新版本: " + info.version());
        System.out.println("下载地址: " + info.url());
        System.out.println("版本日志: " + info.notes());
    }
}
