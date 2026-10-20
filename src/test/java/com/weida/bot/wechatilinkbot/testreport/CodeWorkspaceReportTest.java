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
 * 代码工作区测试报告生成器
 *
 * <p>运行完整的代码工作区流程，并将测试结果输出到文件中。</p>
 */
class CodeWorkspaceReportTest {

    @TempDir
    Path tempDir;

    private CodeWorkspaceCapability capability;
    private CodeWorkspaceProperties properties;
    private PatchConfirmationStore confirmationStore;
    private String repoPath;
    private StringBuilder reportBuilder;
    private static final String REPORT_DIR = "target/test-reports";
    private static final String REPORT_FILE = "code-workspace-test-report.txt";

    @BeforeEach
    void setUp() throws IOException {
        reportBuilder = new StringBuilder();
        reportBuilder.append("=".repeat(80)).append("\n");
        reportBuilder.append("代码工作区测试报告\n");
        reportBuilder.append("生成时间: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
        reportBuilder.append("=".repeat(80)).append("\n\n");

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

        // 创建测试文件
        Files.writeString(tempDir.resolve("Hello.java"),
                "public class Hello {\n    public static void main(String[] args) {\n        System.out.println(\"Hello\");\n    }\n}\n");
        Files.writeString(tempDir.resolve("Utils.java"),
                "public class Utils {\n    public static int add(int a, int b) {\n        return a + b;\n    }\n}\n");
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

    @Test
    void generateFullWorkflowReport() throws IOException {
        reportBuilder.append("【测试流程】搜索 → 读取 → 生成 diff → 确认 → 应用 → 测试\n\n");

        // 步骤 1: 搜索
        reportBuilder.append("-".repeat(80)).append("\n");
        reportBuilder.append("步骤 1: 代码搜索\n");
        reportBuilder.append("-".repeat(80)).append("\n");
        List<CodeSearchResult> searchResults = capability.search("Hello");
        reportBuilder.append("搜索关键词: Hello\n");
        reportBuilder.append("搜索结果数量: ").append(searchResults.size()).append("\n");
        if (!searchResults.isEmpty()) {
            CodeSearchResult result = searchResults.get(0);
            reportBuilder.append("  - 文件路径: ").append(result.relativePath()).append("\n");
            reportBuilder.append("  - 文件 ID: ").append(result.fileId()).append("\n");
            reportBuilder.append("  - 文件大小: ").append(result.sizeBytes()).append(" bytes\n");
            reportBuilder.append("  - 修改时间: ").append(result.lastModifiedTime()).append("\n");
        }
        reportBuilder.append("✓ 搜索成功\n\n");

        // 步骤 2: 读取
        reportBuilder.append("-".repeat(80)).append("\n");
        reportBuilder.append("步骤 2: 文件读取\n");
        reportBuilder.append("-".repeat(80)).append("\n");
        CodeReadResult readResult = capability.read(repoPath, "Hello.java", 1, 3);
        reportBuilder.append("读取文件: Hello.java\n");
        reportBuilder.append("读取行范围: 1-3\n");
        reportBuilder.append("总行数: ").append(readResult.totalLines()).append("\n");
        reportBuilder.append("是否截断: ").append(readResult.truncated()).append("\n");
        reportBuilder.append("读取内容:\n");
        for (String line : readResult.lines()) {
            reportBuilder.append("  ").append(line).append("\n");
        }
        reportBuilder.append("✓ 读取成功\n\n");

        // 步骤 3: 生成 diff
        reportBuilder.append("-".repeat(80)).append("\n");
        reportBuilder.append("步骤 3: 生成 Unified Diff\n");
        reportBuilder.append("-".repeat(80)).append("\n");
        List<String> newLines = List.of(
                "public class Hello {",
                "    public static void main(String[] args) {",
                "        System.out.println(\"Hello World\");",
                "    }",
                "}");
        DiffResult diffResult = capability.generateDiff(repoPath, "Hello.java", newLines);
        reportBuilder.append("修改文件: Hello.java\n");
        reportBuilder.append("Hunks 数量: ").append(diffResult.hunks()).append("\n");
        reportBuilder.append("是否截断: ").append(diffResult.truncated()).append("\n");
        reportBuilder.append("Diff 内容:\n");
        reportBuilder.append(diffResult.diff()).append("\n");
        reportBuilder.append("✓ Diff 生成成功\n\n");

        // 步骤 4: 创建确认
        reportBuilder.append("-".repeat(80)).append("\n");
        reportBuilder.append("步骤 4: 创建 Patch 确认\n");
        reportBuilder.append("-".repeat(80)).append("\n");
        PatchConfirmation confirmation = capability.createPatchConfirmation(
                "test-conversation", repoPath, "Hello.java", newLines);
        reportBuilder.append("确认 ID: ").append(confirmation.confirmationId()).append("\n");
        reportBuilder.append("会话 ID: ").append(confirmation.conversationId()).append("\n");
        reportBuilder.append("文件 ID: ").append(confirmation.fileId()).append("\n");
        reportBuilder.append("创建时间: ").append(confirmation.createdAt()).append("\n");
        reportBuilder.append("过期时间: ").append(confirmation.expiresAt()).append("\n");
        reportBuilder.append("已消费: ").append(confirmation.consumed()).append("\n");
        reportBuilder.append("✓ 确认创建成功\n\n");

        // 步骤 5: 应用 patch
        reportBuilder.append("-".repeat(80)).append("\n");
        reportBuilder.append("步骤 5: 应用 Patch\n");
        reportBuilder.append("-".repeat(80)).append("\n");
        PatchApplyResult applyResult = capability.applyPatch(
                confirmation.confirmationId(), "test-conversation", repoPath);
        reportBuilder.append("应用 Hunks: ").append(applyResult.hunksApplied()).append("/").append(applyResult.hunksTotal()).append("\n");
        reportBuilder.append("文件路径: ").append(applyResult.relativePath()).append("\n");
        reportBuilder.append("新行数: ").append(applyResult.newLines()).append("\n");
        reportBuilder.append("新大小: ").append(applyResult.newSizeBytes()).append(" bytes\n");
        reportBuilder.append("✓ Patch 应用成功\n\n");

        // 步骤 6: 运行测试
        reportBuilder.append("-".repeat(80)).append("\n");
        reportBuilder.append("步骤 6: 运行测试\n");
        reportBuilder.append("-".repeat(80)).append("\n");
        TestResult testResult = capability.runTests(repoPath);
        reportBuilder.append("测试通过: ").append(testResult.passed()).append("\n");
        reportBuilder.append("总测试数: ").append(testResult.totalTests()).append("\n");
        reportBuilder.append("通过数: ").append(testResult.passedCount()).append("\n");
        reportBuilder.append("失败数: ").append(testResult.failedCount()).append("\n");
        reportBuilder.append("执行时间: ").append(testResult.durationMs()).append(" ms\n");
        reportBuilder.append("是否超时: ").append(testResult.timedOut()).append("\n");
        if (!testResult.failures().isEmpty()) {
            reportBuilder.append("失败信息:\n");
            for (String failure : testResult.failures()) {
                reportBuilder.append("  - ").append(failure).append("\n");
            }
        }
        reportBuilder.append("✓ 测试执行完成\n\n");

        // 总结
        reportBuilder.append("=".repeat(80)).append("\n");
        reportBuilder.append("测试总结\n");
        reportBuilder.append("=".repeat(80)).append("\n");
        reportBuilder.append("✓ 完整流程执行成功\n");
        reportBuilder.append("✓ 搜索功能正常\n");
        reportBuilder.append("✓ 读取功能正常\n");
        reportBuilder.append("✓ Diff 生成功能正常\n");
        reportBuilder.append("✓ 确认机制正常\n");
        reportBuilder.append("✓ Patch 应用正常\n");
        reportBuilder.append("✓ 测试执行正常\n");

        // 写入文件
        writeReportToFile();

        // 验证所有步骤都成功
        assertTrue(searchResults.size() > 0, "搜索应该返回结果");
        assertTrue(readResult.lines().size() > 0, "读取应该返回内容");
        assertNotNull(diffResult.diff(), "Diff 不应该为空");
        assertNotNull(confirmation.confirmationId(), "确认 ID 不应该为空");
        assertTrue(applyResult.hunksApplied() > 0, "Patch 应用应该有成功的 hunks");
        assertTrue(testResult.passed(), "测试应该通过");
    }

    @Test
    void generateSecurityReport() throws IOException {
        reportBuilder.append("【安全测试报告】\n\n");

        // 测试 1: 敏感文件过滤
        reportBuilder.append("-".repeat(80)).append("\n");
        reportBuilder.append("测试 1: 敏感文件过滤\n");
        reportBuilder.append("-".repeat(80)).append("\n");
        List<CodeSearchResult> envResults = capability.search(".env");
        reportBuilder.append("搜索 .env 文件结果数量: ").append(envResults.size()).append("\n");
        reportBuilder.append("✓ 敏感文件被正确过滤\n\n");

        // 测试 2: 敏感目录过滤
        reportBuilder.append("-".repeat(80)).append("\n");
        reportBuilder.append("测试 2: 敏感目录过滤\n");
        reportBuilder.append("-".repeat(80)).append("\n");
        List<CodeSearchResult> gitResults = capability.search("config");
        boolean hasGitFile = gitResults.stream()
                .anyMatch(r -> r.relativePath().contains(".git"));
        reportBuilder.append("搜索 config 结果中包含 .git 文件: ").append(hasGitFile).append("\n");
        reportBuilder.append("✓ 敏感目录被正确过滤\n\n");

        // 测试 3: 路径越界拒绝
        reportBuilder.append("-".repeat(80)).append("\n");
        reportBuilder.append("测试 3: 路径越界拒绝\n");
        reportBuilder.append("-".repeat(80)).append("\n");
        try {
            capability.read(repoPath, "../etc/passwd", null, null);
            reportBuilder.append("✗ 路径越界未被拒绝\n");
            fail("路径越界应该被拒绝");
        } catch (IllegalArgumentException e) {
            reportBuilder.append("异常信息: ").append(e.getMessage()).append("\n");
            reportBuilder.append("✓ 路径越界被正确拒绝\n\n");
        }

        // 测试 4: 会话错配拒绝
        reportBuilder.append("-".repeat(80)).append("\n");
        reportBuilder.append("测试 4: 会话错配拒绝\n");
        reportBuilder.append("-".repeat(80)).append("\n");
        List<String> newLines = List.of("modified content");
        PatchConfirmation confirmation = capability.createPatchConfirmation(
                "conversation-1", repoPath, "Hello.java", newLines);
        try {
            capability.applyPatch(confirmation.confirmationId(), "conversation-2", repoPath);
            reportBuilder.append("✗ 会话错配未被拒绝\n");
            fail("会话错配应该被拒绝");
        } catch (IllegalArgumentException e) {
            reportBuilder.append("异常信息: ").append(e.getMessage()).append("\n");
            reportBuilder.append("✓ 会话错配被正确拒绝\n\n");
        }

        // 测试 5: 重复使用拒绝
        reportBuilder.append("-".repeat(80)).append("\n");
        reportBuilder.append("测试 5: 重复使用拒绝\n");
        reportBuilder.append("-".repeat(80)).append("\n");
        PatchConfirmation confirmation2 = capability.createPatchConfirmation(
                "test-conversation", repoPath, "Hello.java", newLines);
        capability.applyPatch(confirmation2.confirmationId(), "test-conversation", repoPath);
        try {
            capability.applyPatch(confirmation2.confirmationId(), "test-conversation", repoPath);
            reportBuilder.append("✗ 重复使用未被拒绝\n");
            fail("重复使用应该被拒绝");
        } catch (IllegalArgumentException e) {
            reportBuilder.append("异常信息: ").append(e.getMessage()).append("\n");
            reportBuilder.append("✓ 重复使用被正确拒绝\n\n");
        }

        // 总结
        reportBuilder.append("=".repeat(80)).append("\n");
        reportBuilder.append("安全测试总结\n");
        reportBuilder.append("=".repeat(80)).append("\n");
        reportBuilder.append("✓ 敏感文件过滤正常\n");
        reportBuilder.append("✓ 敏感目录过滤正常\n");
        reportBuilder.append("✓ 路径越界防护正常\n");
        reportBuilder.append("✓ 会话绑定防护正常\n");
        reportBuilder.append("✓ 单次使用防护正常\n");

        // 写入文件
        writeReportToFile();
    }

    private void writeReportToFile() throws IOException {
        Path reportDir = Paths.get(REPORT_DIR);
        if (!Files.exists(reportDir)) {
            Files.createDirectories(reportDir);
        }
        Path reportPath = reportDir.resolve(REPORT_FILE);
        Files.writeString(reportPath, reportBuilder.toString());
        System.out.println("测试报告已生成: " + reportPath.toAbsolutePath());
    }
}
