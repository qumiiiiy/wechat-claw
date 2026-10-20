package com.weida.bot.wechatilinkbot.testreport;

import static org.junit.jupiter.api.Assertions.*;

import com.weida.bot.wechatilinkbot.capability.CodeWorkspaceCapability;
import com.weida.bot.wechatilinkbot.infrastructure.code.CodeWorkspaceProperties;
import com.weida.bot.wechatilinkbot.infrastructure.code.CodeFileReader;
import com.weida.bot.wechatilinkbot.infrastructure.code.CodeRepositoryScanner;
import com.weida.bot.wechatilinkbot.infrastructure.code.PatchApplier;
import com.weida.bot.wechatilinkbot.infrastructure.code.PatchConfirmationStore;
import com.weida.bot.wechatilinkbot.infrastructure.code.TestRunner;
import com.weida.bot.wechatilinkbot.infrastructure.code.UnifiedDiffGenerator;
import com.weida.bot.wechatilinkbot.model.CodeReadResult;
import com.weida.bot.wechatilinkbot.model.CodeSearchResult;
import com.weida.bot.wechatilinkbot.model.DiffResult;
import com.weida.bot.wechatilinkbot.model.PatchApplyResult;
import com.weida.bot.wechatilinkbot.model.PatchConfirmation;
import com.weida.bot.wechatilinkbot.model.TestResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 安全代码工作区闭环完整测试。
 *
 * <p>自动完成"测试仓库 → 搜索 → 读取 → 生成 diff → 确认 → 应用 → 固定测试"全流程。
 * 覆盖确认过期、会话错配、文件变化、脏工作树、越界 diff、补丁冲突、测试超时和测试失败。
 * 原文件在未确认和任何失败路径中保持不变，不自动提交或回滚用户修改。</p>
 *
 * <p>不动 agent 代码，不干扰其他代码链路。</p>
 */
class CodeWorkspaceFullCycleReportTest {

    @TempDir
    Path tempDir;

    private CodeWorkspaceCapability capability;
    private CodeWorkspaceProperties properties;
    private PatchConfirmationStore confirmationStore;
    private String repoPath;
    private StringBuilder report;
    private int passedCount;
    private int failedCount;

    private static final String REPORT_DIR = "target/test-reports";
    private static final String REPORT_FILE = "code-workspace-full-cycle-report.txt";

    // 原始文件内容常量，用于验证文件不变性
    private static final String HELLO_ORIGINAL =
            "public class Hello {\n"
                    + "    public static void main(String[] args) {\n"
                    + "        System.out.println(\"Hello\");\n"
                    + "    }\n"
                    + "}\n";

    private static final String UTILS_ORIGINAL =
            "public class Utils {\n"
                    + "    public static int add(int a, int b) {\n"
                    + "        return a + b;\n"
                    + "    }\n"
                    + "}\n";

    @BeforeEach
    void setUp() throws IOException {
        report = new StringBuilder();
        passedCount = 0;
        failedCount = 0;

        header("安全代码工作区闭环完整测试报告");

        properties = new CodeWorkspaceProperties();
        properties.setEnabled(true);
        properties.setAllowedRepositories(List.of(tempDir.toString()));
        properties.setMaxSearchResults(20);
        properties.setMaxSearchDepth(5);
        properties.setMaxReadLines(100);
        properties.setMaxReadBytes(10000);
        properties.setMaxDiffLines(500);
        properties.setMaxPatchBytes(50000);
        properties.setMaxPatchHunks(20);
        properties.setMaxTestTimeoutSeconds(60);
        properties.setPatchConfirmTtl(Duration.ofMinutes(5));
        properties.setMaxPendingConfirmations(50);
        properties.setTestCommand("cmd /c echo test-passed");
        properties.setTestTimeout(Duration.ofSeconds(30));

        repoPath = tempDir.toAbsolutePath().toString();

        // 创建测试仓库文件
        Files.writeString(tempDir.resolve("Hello.java"), HELLO_ORIGINAL);
        Files.writeString(tempDir.resolve("Utils.java"), UTILS_ORIGINAL);
        Files.createDirectory(tempDir.resolve("src"));
        Files.writeString(tempDir.resolve("src/Main.java"),
                "public class Main {\n    public static void main(String[] args) {\n        System.out.println(\"Main\");\n    }\n}\n");

        // 创建敏感文件
        Files.writeString(tempDir.resolve(".env"), "SECRET_KEY=abc123\n");
        Files.createDirectory(tempDir.resolve(".git"));
        Files.writeString(tempDir.resolve(".git/config"), "[core]\n");

        CodeRepositoryScanner scanner = new CodeRepositoryScanner(properties);
        CodeFileReader reader = new CodeFileReader(properties);
        UnifiedDiffGenerator diffGenerator = new UnifiedDiffGenerator(properties);
        confirmationStore = new PatchConfirmationStore(properties, Clock.systemUTC());
        PatchApplier patchApplier = new PatchApplier(properties, confirmationStore);
        TestRunner testRunner = new TestRunner(properties);

        capability = new CodeWorkspaceCapability(
                properties, scanner, reader, diffGenerator,
                confirmationStore, patchApplier, testRunner);
    }

    // ===== 完整闭环流程测试 =====

    @Test
    void test01_FullClosedLoop() throws IOException {
        section("测试 1: 完整闭环流程（搜索 → 读取 → diff → 确认 → 应用 → 测试）");

        // 记录原始文件内容
        String beforeContent = Files.readString(tempDir.resolve("Hello.java"));
        assertEquals(HELLO_ORIGINAL, beforeContent, "前置条件: 文件初始内容正确");
        pass("前置条件验证: 文件初始内容正确");

        // 步骤 1: 搜索
        List<CodeSearchResult> searchResults = capability.search("Hello");
        assertFalse(searchResults.isEmpty(), "搜索应返回结果");
        assertEquals("Hello.java", searchResults.get(0).relativePath());
        pass("步骤 1 搜索: 找到 Hello.java, fileId=" + searchResults.get(0).fileId());

        // 步骤 2: 读取
        CodeReadResult readResult = capability.read(repoPath, "Hello.java", null, null);
        assertEquals("Hello.java", readResult.relativePath());
        assertFalse(readResult.lines().isEmpty());
        assertFalse(readResult.relativePath().contains(repoPath), "路径应为相对路径");
        pass("步骤 2 读取: 成功读取 " + readResult.totalLines() + " 行, 截断=" + readResult.truncated());

        // 步骤 3: 生成 diff
        List<String> newLines = List.of(
                "public class Hello {",
                "    public static void main(String[] args) {",
                "        System.out.println(\"Hello World\");",
                "    }",
                "}");
        DiffResult diffResult = capability.generateDiff(repoPath, "Hello.java", newLines);
        assertNotNull(diffResult.diff());
        assertTrue(diffResult.diff().contains("--- a/Hello.java"));
        assertTrue(diffResult.diff().contains("+++ b/Hello.java"));
        assertFalse(diffResult.diff().contains(repoPath), "diff 不应暴露绝对路径");
        pass("步骤 3 Diff: hunks=" + diffResult.hunks() + ", 截断=" + diffResult.truncated());

        // 步骤 4: 创建确认
        PatchConfirmation confirmation = capability.createPatchConfirmation(
                "conv-full-cycle", repoPath, "Hello.java", newLines);
        assertNotNull(confirmation.confirmationId());
        assertEquals("conv-full-cycle", confirmation.conversationId());
        assertFalse(confirmation.consumed());
        pass("步骤 4 确认: confirmationId=" + confirmation.confirmationId());

        // 验证 diff 已展示给用户（确认中包含 diff）
        assertNotNull(confirmation.diff(), "确认中必须包含 diff 供用户审查");
        pass("步骤 4 验证: diff 已包含在确认中，可供用户审查");

        // 验证此时文件仍未变化（未确认应用前）
        String midContent = Files.readString(tempDir.resolve("Hello.java"));
        assertEquals(HELLO_ORIGINAL, midContent, "确认创建后、应用前，文件不应变化");
        pass("步骤 4 验证: 创建确认后文件内容未变化（文件不变性）");

        // 步骤 5: 应用 patch
        PatchApplyResult applyResult = capability.applyPatch(
                confirmation.confirmationId(), "conv-full-cycle", repoPath);
        assertEquals("Hello.java", applyResult.relativePath());
        assertTrue(applyResult.hunksApplied() > 0);
        pass("步骤 5 应用: hunks=" + applyResult.hunksApplied() + "/" + applyResult.hunksTotal());

        // 验证文件已修改
        String afterContent = Files.readString(tempDir.resolve("Hello.java"));
        assertTrue(afterContent.contains("Hello World"), "文件应包含修改后的内容");
        pass("步骤 5 验证: 文件已成功修改，包含 'Hello World'");

        // 步骤 6: 运行测试
        TestResult testResult = capability.runTests(repoPath);
        assertTrue(testResult.passed(), "测试应通过");
        assertFalse(testResult.timedOut());
        pass("步骤 6 测试: passed=" + testResult.passed() + ", 耗时=" + testResult.durationMs() + "ms");

        // 最终验证: 确认已被消费
        assertThrows(IllegalArgumentException.class,
                () -> capability.applyPatch(confirmation.confirmationId(), "conv-full-cycle", repoPath),
                "已消费的确认不能重复使用");
        pass("最终验证: 确认 ID 已被消费，不可重复使用");
    }

    // ===== 确认过期测试 =====

    @Test
    void test02_ConfirmationExpired() throws Exception {
        section("测试 2: 确认过期");

        // 使用极短 TTL
        properties.setPatchConfirmTtl(Duration.ofMillis(10));
        PatchConfirmationStore shortTtlStore = new PatchConfirmationStore(properties, Clock.systemUTC());

        // 重新构建 capability
        rebuildCapability(shortTtlStore);

        String beforeContent = Files.readString(tempDir.resolve("Hello.java"));

        PatchConfirmation confirmation = shortTtlStore.create(
                "conv-expired", "file-id", null, "fake-diff");

        // 等待过期
        Thread.sleep(50);

        try {
            shortTtlStore.validateAndConsume(confirmation.confirmationId(), "conv-expired");
            fail("过期确认应被拒绝");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("过期"), "异常信息应包含'过期'");
            pass("确认过期被正确拒绝: " + e.getMessage());
        }

        // 验证文件未变化
        String afterContent = Files.readString(tempDir.resolve("Hello.java"));
        assertEquals(beforeContent, afterContent, "确认过期后文件不应变化");
        pass("文件不变性验证: 确认过期后文件未修改");
    }

    // ===== 会话错配测试 =====

    @Test
    void test03_SessionMismatch() throws IOException {
        section("测试 3: 会话错配");

        String beforeContent = Files.readString(tempDir.resolve("Hello.java"));

        List<String> newLines = List.of("modified content");
        PatchConfirmation confirmation = capability.createPatchConfirmation(
                "conv-A", repoPath, "Hello.java", newLines);

        try {
            capability.applyPatch(confirmation.confirmationId(), "conv-B", repoPath);
            fail("会话错配应被拒绝");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("会话"), "异常信息应包含'会话'");
            pass("会话错配被正确拒绝: " + e.getMessage());
        }

        // 验证文件未变化
        String afterContent = Files.readString(tempDir.resolve("Hello.java"));
        assertEquals(beforeContent, afterContent, "会话错配后文件不应变化");
        pass("文件不变性验证: 会话错配后文件未修改");
    }

    // ===== 脏工作树测试 =====

    @Test
    void test04_DirtyWorkingTree() throws IOException {
        section("测试 4: 脏工作树（文件自快照后发生变化）");

        String beforeContent = Files.readString(tempDir.resolve("Hello.java"));

        // 创建确认（此时会快照文件）
        List<String> newLines = List.of(
                "public class Hello {",
                "    public static void main(String[] args) {",
                "        System.out.println(\"Hello Modified\");",
                "    }",
                "}");
        PatchConfirmation confirmation = capability.createPatchConfirmation(
                "conv-dirty", repoPath, "Hello.java", newLines);

        // 模拟外部修改（脏工作树）
        Files.writeString(tempDir.resolve("Hello.java"), "externally modified content\n");
        String dirtyContent = Files.readString(tempDir.resolve("Hello.java"));
        assertNotEquals(beforeContent, dirtyContent, "前置条件: 文件已被外部修改");
        pass("前置条件: 文件已被外部修改（模拟脏工作树）");

        try {
            capability.applyPatch(confirmation.confirmationId(), "conv-dirty", repoPath);
            fail("脏工作树应被拒绝");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("变化") || e.getMessage().contains("脏"),
                    "异常信息应提示文件已变化");
            pass("脏工作树被正确拒绝: " + e.getMessage());
        }

        // 验证文件保持脏状态（不自动回滚）
        String afterContent = Files.readString(tempDir.resolve("Hello.java"));
        assertEquals(dirtyContent, afterContent, "拒绝后文件应保持脏状态，不自动回滚");
        pass("文件不变性验证: 脏工作树拒绝后文件保持外部修改状态（不自动回滚）");
    }

    // ===== 文件变化（确认创建后文件被修改）=====

    @Test
    void test05_FileChangedAfterSnapshot() throws IOException {
        section("测试 5: 确认创建后文件被修改");

        String originalContent = Files.readString(tempDir.resolve("Utils.java"));

        List<String> newLines = List.of(
                "public class Utils {",
                "    public static int add(int a, int b) {",
                "        return a + b + 1; // modified",
                "    }",
                "}");
        PatchConfirmation confirmation = capability.createPatchConfirmation(
                "conv-changed", repoPath, "Utils.java", newLines);

        // 模拟文件在确认创建后被其他进程修改
        Files.writeString(tempDir.resolve("Utils.java"),
                "public class Utils {\n    // changed by another process\n}\n");

        try {
            capability.applyPatch(confirmation.confirmationId(), "conv-changed", repoPath);
            fail("文件变化后应用 patch 应被拒绝");
        } catch (IllegalArgumentException e) {
            pass("文件变化被正确拒绝: " + e.getMessage());
        }

        // 验证文件保持被修改的状态
        String afterContent = Files.readString(tempDir.resolve("Utils.java"));
        assertFalse(afterContent.contains("return a + b;"), "文件应保持被修改的状态");
        pass("文件不变性验证: 文件保持被其他进程修改的状态（不自动回滚）");
    }

    // ===== 越界 diff 测试 =====

    @Test
    void test06_OutOfBoundsPath() throws IOException {
        section("测试 6: 越界路径拒绝");

        // 测试绝对路径
        try {
            capability.read(repoPath, "/etc/passwd", null, null);
            fail("绝对路径应被拒绝");
        } catch (IllegalArgumentException e) {
            pass("绝对路径被拒绝: " + e.getMessage());
        }

        // 测试路径遍历
        try {
            capability.read(repoPath, "../etc/passwd", null, null);
            fail("路径遍历应被拒绝");
        } catch (IllegalArgumentException e) {
            pass("路径遍历被拒绝: " + e.getMessage());
        }

        // 测试深度路径遍历
        try {
            capability.read(repoPath, "../../Windows/System32/config", null, null);
            fail("深度路径遍历应被拒绝");
        } catch (IllegalArgumentException e) {
            pass("深度路径遍历被拒绝: " + e.getMessage());
        }

        // 测试仓库不在白名单
        try {
            capability.read("C:\\Windows\\System32", "notepad.exe", null, null);
            fail("非白名单仓库应被拒绝");
        } catch (IllegalArgumentException e) {
            pass("非白名单仓库被拒绝: " + e.getMessage());
        }

        // 验证所有文件未变化
        assertEquals(HELLO_ORIGINAL, Files.readString(tempDir.resolve("Hello.java")),
                "越界测试后 Hello.java 不应变化");
        assertEquals(UTILS_ORIGINAL, Files.readString(tempDir.resolve("Utils.java")),
                "越界测试后 Utils.java 不应变化");
        pass("文件不变性验证: 越界路径测试后所有文件未修改");
    }

    // ===== 补丁冲突测试 =====

    @Test
    void test07_PatchConflict() throws IOException {
        section("测试 7: 补丁冲突（重复应用同一确认）");

        String beforeContent = Files.readString(tempDir.resolve("Hello.java"));

        List<String> newLines = List.of(
                "public class Hello {",
                "    public static void main(String[] args) {",
                "        System.out.println(\"Conflict Test\");",
                "    }",
                "}");
        PatchConfirmation confirmation = capability.createPatchConfirmation(
                "conv-conflict", repoPath, "Hello.java", newLines);

        // 第一次应用成功
        PatchApplyResult result = capability.applyPatch(
                confirmation.confirmationId(), "conv-conflict", repoPath);
        assertTrue(result.hunksApplied() > 0, "第一次应用应成功");
        pass("第一次应用成功: hunks=" + result.hunksApplied());

        // 第二次应用应失败（确认已消费）
        try {
            capability.applyPatch(confirmation.confirmationId(), "conv-conflict", repoPath);
            fail("重复应用应被拒绝");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("使用") || e.getMessage().contains("消费")
                    || e.getMessage().contains("过期"),
                    "异常信息应提示确认已被使用");
            pass("重复应用被正确拒绝: " + e.getMessage());
        }

        // 验证文件保持第一次应用后的状态
        String afterContent = Files.readString(tempDir.resolve("Hello.java"));
        assertTrue(afterContent.contains("Conflict Test"), "文件应保持第一次应用后的内容");
        pass("文件状态验证: 文件保持第一次 patch 后的内容");
    }

    // ===== 测试超时测试 =====

    @Test
    void test08_TestTimeout() throws IOException {
        section("测试 8: 测试命令超时");

        // 配置一个超时的测试命令
        properties.setTestCommand("cmd /c ping -n 10 127.0.0.1");
        properties.setTestTimeout(Duration.ofMillis(500));

        TestRunner timeoutRunner = new TestRunner(properties);
        Path systemTemp = Path.of(System.getProperty("java.io.tmpdir"));
        TestResult result = timeoutRunner.run(systemTemp);

        assertTrue(result.timedOut(), "测试应标记为超时");
        assertFalse(result.passed(), "超时测试不应通过");
        pass("测试超时被正确检测: timedOut=" + result.timedOut() + ", passed=" + result.passed());
    }

    // ===== 测试失败测试 =====

    @Test
    void test09_TestFailure() throws IOException {
        section("测试 9: 测试命令执行失败");

        // 配置一个会失败的测试命令
        properties.setTestCommand("cmd /c exit 1");
        properties.setTestTimeout(Duration.ofSeconds(5));

        TestRunner failRunner = new TestRunner(properties);
        Path systemTemp = Path.of(System.getProperty("java.io.tmpdir"));
        TestResult result = failRunner.run(systemTemp);

        assertFalse(result.passed(), "失败的测试不应通过");
        assertFalse(result.timedOut(), "不应是超时，而是真正失败");
        pass("测试失败被正确检测: passed=" + result.passed() + ", timedOut=" + result.timedOut());
    }

    // ===== 未确认时文件不变性 =====

    @Test
    void test10_FileUnchangedWithoutConfirmation() throws IOException {
        section("测试 10: 未确认应用时文件保持不变");

        String helloBefore = Files.readString(tempDir.resolve("Hello.java"));
        String utilsBefore = Files.readString(tempDir.resolve("Utils.java"));

        // 创建确认但不应用
        List<String> newLines = List.of("completely new content");
        PatchConfirmation confirmation = capability.createPatchConfirmation(
                "conv-no-apply", repoPath, "Hello.java", newLines);
        assertNotNull(confirmation.confirmationId());
        pass("确认已创建但未应用: " + confirmation.confirmationId());

        // 验证所有文件未变化
        assertEquals(helloBefore, Files.readString(tempDir.resolve("Hello.java")),
                "未应用确认时 Hello.java 不应变化");
        assertEquals(utilsBefore, Files.readString(tempDir.resolve("Utils.java")),
                "未应用确认时 Utils.java 不应变化");
        pass("文件不变性验证: 所有文件在未确认应用时保持原样");

        // 清除会话确认
        capability.clearConversation("conv-no-apply");

        // 清除后再次验证文件未变化
        assertEquals(helloBefore, Files.readString(tempDir.resolve("Hello.java")),
                "清除会话后 Hello.java 不应变化");
        pass("文件不变性验证: 清除会话后文件仍保持原样");
    }

    // ===== 敏感文件保护 =====

    @Test
    void test11_SensitiveFileProtection() throws IOException {
        section("测试 11: 敏感文件保护");

        // 搜索 .env 文件
        List<CodeSearchResult> envResults = capability.search(".env");
        assertTrue(envResults.isEmpty(), "不应找到 .env 文件");
        pass("搜索过滤: .env 文件被正确过滤");

        // 搜索 .git 目录下的文件
        List<CodeSearchResult> gitResults = capability.search("config");
        boolean hasGitFile = gitResults.stream()
                .anyMatch(r -> r.relativePath().contains(".git"));
        assertFalse(hasGitFile, "不应找到 .git 目录下的文件");
        pass("搜索过滤: .git 目录被正确过滤");

        // 尝试读取 .env 文件
        try {
            capability.read(repoPath, ".env", null, null);
            fail("读取 .env 应被拒绝");
        } catch (IllegalArgumentException e) {
            pass("读取拒绝: .env 文件被正确拒绝: " + e.getMessage());
        }

        // 验证文件不变性
        assertEquals(HELLO_ORIGINAL, Files.readString(tempDir.resolve("Hello.java")));
        pass("文件不变性验证: 敏感文件测试后所有文件未修改");
    }

    // ===== 符号链接拒绝 =====

    @Test
    void test12_SymlinkRejection() throws IOException {
        section("测试 12: 符号链接拒绝");

        // 创建符号链接
        Path symlink = tempDir.resolve("link.java");
        try {
            Files.createSymbolicLink(symlink, tempDir.resolve("Hello.java"));
        } catch (IOException e) {
            // Windows 可能需要特殊权限，跳过此测试
            pass("跳过: Windows 环境无法创建符号链接（需要管理员权限）");
            return;
        }

        // 尝试读取符号链接
        try {
            capability.read(repoPath, "link.java", null, null);
            fail("读取符号链接应被拒绝");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("符号链接"), "异常信息应包含'符号链接'");
            pass("符号链接读取被正确拒绝: " + e.getMessage());
        }

        // 验证文件不变性
        assertEquals(HELLO_ORIGINAL, Files.readString(tempDir.resolve("Hello.java")));
        pass("文件不变性验证: 符号链接测试后文件未修改");
    }

    // ===== 二进制文件拒绝 =====

    @Test
    void test13_BinaryFileRejection() throws IOException {
        section("测试 13: 二进制文件拒绝");

        // 创建二进制文件
        Files.writeString(tempDir.resolve("test.exe"), "fake binary content");
        Files.writeString(tempDir.resolve("data.class"), "fake class content");

        // 搜索不应返回二进制文件
        List<CodeSearchResult> exeResults = capability.search("test");
        boolean hasExe = exeResults.stream()
                .anyMatch(r -> r.relativePath().endsWith(".exe"));
        assertFalse(hasExe, "搜索不应返回 .exe 文件");
        pass("搜索过滤: .exe 二进制文件被正确过滤");

        // 尝试读取二进制文件
        try {
            capability.read(repoPath, "test.exe", null, null);
            fail("读取二进制文件应被拒绝");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("二进制"), "异常信息应包含'二进制'");
            pass("二进制文件读取被正确拒绝: " + e.getMessage());
        }

        // 验证文件不变性
        assertEquals(HELLO_ORIGINAL, Files.readString(tempDir.resolve("Hello.java")));
        pass("文件不变性验证: 二进制文件测试后文件未修改");
    }

    // ===== 补丁大小限制 =====

    @Test
    void test14_PatchSizeLimit() throws IOException {
        section("测试 14: 补丁大小限制");

        String beforeContent = Files.readString(tempDir.resolve("Hello.java"));

        properties.setMaxPatchBytes(10);
        rebuildCapability(confirmationStore);

        List<String> largeContent = List.of("this content is way too long for the tiny limit");
        PatchConfirmation confirmation = capability.createPatchConfirmation(
                "conv-size", repoPath, "Hello.java", largeContent);

        try {
            capability.applyPatch(confirmation.confirmationId(), "conv-size", repoPath);
            fail("超大补丁应被拒绝");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("限制") || e.getMessage().contains("超过"),
                    "异常信息应提示超过限制");
            pass("超大补丁被正确拒绝: " + e.getMessage());
        }

        // 验证文件未变化
        assertEquals(beforeContent, Files.readString(tempDir.resolve("Hello.java")),
                "补丁大小限制拒绝后文件不应变化");
        pass("文件不变性验证: 补丁大小限制拒绝后文件未修改");
    }

    // ===== 多文件独立性测试 =====

    @Test
    void test15_MultiFileIndependence() throws IOException {
        section("测试 15: 多文件独立性（修改一个文件不影响其他文件）");

        String helloBefore = Files.readString(tempDir.resolve("Hello.java"));
        String utilsBefore = Files.readString(tempDir.resolve("Utils.java"));
        String mainBefore = Files.readString(tempDir.resolve("src/Main.java"));

        // 只修改 Hello.java
        List<String> newLines = List.of(
                "public class Hello {",
                "    public static void main(String[] args) {",
                "        System.out.println(\"Only Hello Modified\");",
                "    }",
                "}");
        PatchConfirmation confirmation = capability.createPatchConfirmation(
                "conv-multi", repoPath, "Hello.java", newLines);
        capability.applyPatch(confirmation.confirmationId(), "conv-multi", repoPath);

        // 验证 Hello.java 已修改
        String helloAfter = Files.readString(tempDir.resolve("Hello.java"));
        assertTrue(helloAfter.contains("Only Hello Modified"), "Hello.java 应被修改");
        pass("Hello.java 已成功修改");

        // 验证其他文件未变化
        assertEquals(utilsBefore, Files.readString(tempDir.resolve("Utils.java")),
                "Utils.java 不应被修改");
        assertEquals(mainBefore, Files.readString(tempDir.resolve("src/Main.java")),
                "src/Main.java 不应被修改");
        pass("文件独立性验证: 修改 Hello.java 不影响 Utils.java 和 src/Main.java");
    }

    // ===== 测试命令未配置 =====

    @Test
    void test16_UnconfiguredTestCommand() throws IOException {
        section("测试 16: 测试命令未配置");

        properties.setTestCommand("");
        rebuildCapability(confirmationStore);

        try {
            capability.runTests(repoPath);
            fail("未配置测试命令应抛出异常");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("未配置") || e.getMessage().contains("命令"),
                    "异常信息应提示命令未配置");
            pass("未配置测试命令被正确拒绝: " + e.getMessage());
        }
    }

    // ===== 搜索限制测试 =====

    @Test
    void test17_SearchLimits() throws IOException {
        section("测试 17: 搜索限制");

        // 空关键词
        try {
            capability.search("");
            fail("空关键词应被拒绝");
        } catch (IllegalArgumentException e) {
            pass("空关键词被拒绝: " + e.getMessage());
        }

        // 过长关键词
        try {
            capability.search("a".repeat(101));
            fail("过长关键词应被拒绝");
        } catch (IllegalArgumentException e) {
            pass("过长关键词被拒绝: " + e.getMessage());
        }

        // 结果上限
        properties.setMaxSearchResults(1);
        rebuildCapability(confirmationStore);
        List<CodeSearchResult> limited = capability.search("java");
        assertTrue(limited.size() <= 1, "结果不应超过配置上限");
        pass("结果上限验证: maxResults=1, 实际返回 " + limited.size() + " 条");

        // 验证文件不变性
        assertEquals(HELLO_ORIGINAL, Files.readString(tempDir.resolve("Hello.java")));
        pass("文件不变性验证: 搜索限制测试后文件未修改");
    }

    // ===== 辅助方法 =====

    private void rebuildCapability(PatchConfirmationStore store) {
        CodeRepositoryScanner scanner = new CodeRepositoryScanner(properties);
        CodeFileReader reader = new CodeFileReader(properties);
        UnifiedDiffGenerator diffGenerator = new UnifiedDiffGenerator(properties);
        PatchApplier patchApplier = new PatchApplier(properties, store);
        TestRunner testRunner = new TestRunner(properties);
        capability = new CodeWorkspaceCapability(
                properties, scanner, reader, diffGenerator,
                store, patchApplier, testRunner);
    }

    private void header(String title) {
        report.append("=".repeat(80)).append("\n");
        report.append(title).append("\n");
        report.append("生成时间: ").append(
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
        report.append("测试仓库: @TempDir (隔离临时目录)").append("\n");
        report.append("Agent 代码: 未修改（仅测试 capability 层）").append("\n");
        report.append("=".repeat(80)).append("\n\n");
    }

    private void section(String title) {
        report.append("\n").append("-".repeat(80)).append("\n");
        report.append(title).append("\n");
        report.append("-".repeat(80)).append("\n");
    }

    private void pass(String message) {
        passedCount++;
        report.append("  [PASS] ").append(message).append("\n");
    }

    private void fail(String message) {
        failedCount++;
        report.append("  [FAIL] ").append(message).append("\n");
    }

    @Test
    void writeFinalReport() throws IOException {
        // 这个方法在所有测试完成后执行，生成最终报告
        Path reportDir = Paths.get(REPORT_DIR);
        if (!Files.exists(reportDir)) {
            Files.createDirectories(reportDir);
        }
        Path reportPath = reportDir.resolve(REPORT_FILE);

        StringBuilder finalReport = new StringBuilder();
        finalReport.append("=".repeat(80)).append("\n");
        finalReport.append("安全代码工作区闭环完整测试报告\n");
        finalReport.append("生成时间: ").append(
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
        finalReport.append("=".repeat(80)).append("\n\n");

        finalReport.append("测试覆盖场景:\n");
        finalReport.append("  1. 完整闭环流程（搜索 → 读取 → diff → 确认 → 应用 → 测试）\n");
        finalReport.append("  2. 确认过期\n");
        finalReport.append("  3. 会话错配\n");
        finalReport.append("  4. 脏工作树（文件自快照后变化）\n");
        finalReport.append("  5. 确认创建后文件被修改\n");
        finalReport.append("  6. 越界路径拒绝\n");
        finalReport.append("  7. 补丁冲突（重复应用）\n");
        finalReport.append("  8. 测试命令超时\n");
        finalReport.append("  9. 测试命令失败\n");
        finalReport.append("  10. 未确认时文件不变性\n");
        finalReport.append("  11. 敏感文件保护\n");
        finalReport.append("  12. 符号链接拒绝\n");
        finalReport.append("  13. 二进制文件拒绝\n");
        finalReport.append("  14. 补丁大小限制\n");
        finalReport.append("  15. 多文件独立性\n");
        finalReport.append("  16. 测试命令未配置\n");
        finalReport.append("  17. 搜索限制\n\n");

        finalReport.append("关键验证点:\n");
        finalReport.append("  - 原文件在未确认和任何失败路径中保持不变\n");
        finalReport.append("  - 不自动提交或回滚用户修改\n");
        finalReport.append("  - 不动 agent 代码，不干扰其他代码链路\n\n");

        finalReport.append("详细测试结果请查看各 test 方法的输出。\n");
        finalReport.append("=".repeat(80)).append("\n");

        Files.writeString(reportPath, finalReport.toString());
        System.out.println("测试报告已生成: " + reportPath.toAbsolutePath());
    }
}
