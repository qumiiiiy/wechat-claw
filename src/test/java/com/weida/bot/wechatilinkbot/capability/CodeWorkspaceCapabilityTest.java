package com.weida.bot.wechatilinkbot.capability;

import static org.junit.jupiter.api.Assertions.*;

import com.weida.bot.wechatilinkbot.infrastructure.code.CodeFileReader;
import com.weida.bot.wechatilinkbot.infrastructure.code.CodeRepositoryScanner;
import com.weida.bot.wechatilinkbot.infrastructure.code.CodeWorkspaceProperties;
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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for CodeWorkspaceCapability.
 *
 * <p>Uses a temporary directory as a fake repository to test the full
 * search -> read -> diff -> confirm -> apply -> test flow.</p>
 */
class CodeWorkspaceCapabilityTest {

    @TempDir
    Path tempDir;

    private CodeWorkspaceCapability capability;
    private CodeWorkspaceProperties properties;
    private PatchConfirmationStore confirmationStore;
    private String repoPath;

    @BeforeEach
    void setUp() throws IOException {
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

        // Create test files
        Files.writeString(tempDir.resolve("Hello.java"),
                "public class Hello {\n    public static void main(String[] args) {\n        System.out.println(\"Hello\");\n    }\n}\n");
        Files.writeString(tempDir.resolve("Utils.java"),
                "public class Utils {\n    public static int add(int a, int b) {\n        return a + b;\n    }\n}\n");
        Files.createDirectory(tempDir.resolve("src"));
        Files.writeString(tempDir.resolve("src/Main.java"),
                "public class Main {\n    public static void main(String[] args) {\n        System.out.println(\"Main\");\n    }\n}\n");

        // Create sensitive files that should be rejected
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

    // ===== Search tests =====

    @Test
    void searchesFilesByKeywordSuccessfully() {
        List<CodeSearchResult> results = capability.search("Hello");
        assertEquals(1, results.size());
        assertEquals("Hello.java", results.get(0).relativePath());
        assertNotNull(results.get(0).fileId());
        assertTrue(results.get(0).sizeBytes() > 0);
        assertNotNull(results.get(0).lastModifiedTime());
    }

    @Test
    void searchesFilesCaseInsensitive() {
        List<CodeSearchResult> results = capability.search("hello");
        assertEquals(1, results.size());
        assertEquals("Hello.java", results.get(0).relativePath());
    }

    @Test
    void searchesFilesRecursively() {
        List<CodeSearchResult> results = capability.search("Main");
        assertEquals(1, results.size());
        assertEquals("src" + Path.of("").getFileSystem().getSeparator() + "Main.java",
                results.get(0).relativePath());
    }

    @Test
    void rejectsSearchWhenDisabled() {
        properties.setEnabled(false);
        assertThrows(IllegalStateException.class, () -> capability.search("Hello"));
    }

    @Test
    void rejectsEmptyKeyword() {
        assertThrows(IllegalArgumentException.class, () -> capability.search(""));
    }

    @Test
    void rejectsTooLongKeyword() {
        assertThrows(IllegalArgumentException.class,
                () -> capability.search("a".repeat(101)));
    }

    @Test
    void limitsResultsByMaxResults() {
        properties.setMaxSearchResults(1);
        List<CodeSearchResult> results = capability.search("java");
        assertTrue(results.size() <= 1);
    }

    @Test
    void rejectsSensitiveFiles() {
        List<CodeSearchResult> results = capability.search(".env");
        assertTrue(results.isEmpty(), "sensitive file .env should not be found");
    }

    @Test
    void rejectsSensitiveDirectories() {
        List<CodeSearchResult> results = capability.search("config");
        for (CodeSearchResult result : results) {
            assertFalse(result.relativePath().contains(".git"),
                    "files in .git should not be found");
        }
    }

    // ===== Read tests =====

    @Test
    void readsFileWithBoundedOutput() {
        CodeReadResult result = capability.read(repoPath, "Hello.java", 1, 2);
        assertEquals("Hello.java", result.relativePath());
        assertEquals(2, result.lines().size());
        assertEquals(5, result.totalLines());
        assertTrue(result.truncated());
        assertEquals(1, result.startLine());
    }

    @Test
    void readsFileFromBeginning() {
        CodeReadResult result = capability.read(repoPath, "Hello.java", null, null);
        assertEquals(5, result.lines().size());
        assertFalse(result.truncated());
    }

    @Test
    void rejectsAbsolutePath() {
        assertThrows(IllegalArgumentException.class,
                () -> capability.read(repoPath, "/etc/passwd", null, null));
    }

    @Test
    void rejectsPathTraversal() {
        assertThrows(IllegalArgumentException.class,
                () -> capability.read(repoPath, "../etc/passwd", null, null));
    }

    @Test
    void rejectsFileOutsideRepo() {
        assertThrows(IllegalArgumentException.class,
                () -> capability.read(repoPath, "../../etc/passwd", null, null));
    }

    @Test
    void returnsRelativePathOnly() {
        CodeReadResult result = capability.read(repoPath, "Hello.java", null, null);
        assertFalse(result.relativePath().contains(repoPath),
                "path should be relative, not absolute");
        assertEquals("Hello.java", result.relativePath());
    }

    @Test
    void returnsOpaqueFileId() {
        List<CodeSearchResult> results = capability.search("Hello");
        assertEquals(1, results.size());
        String fileId = results.get(0).fileId();
        assertNotNull(fileId);
        assertTrue(fileId.startsWith("code-"), "file ID should be opaque");
    }

    // ===== Diff tests =====

    @Test
    void generatesDiffSuccessfully() {
        List<String> newLines = List.of(
                "public class Hello {",
                "    public static void main(String[] args) {",
                "        System.out.println(\"Hello World\");",
                "    }",
                "}");

        DiffResult diff = capability.generateDiff(repoPath, "Hello.java", newLines);
        assertEquals("Hello.java", diff.relativePath());
        assertNotNull(diff.diff());
        assertTrue(diff.diff().contains("--- a/Hello.java"));
        assertTrue(diff.diff().contains("+++ b/Hello.java"));
        assertTrue(diff.hunks() > 0);
        assertFalse(diff.truncated());
    }

    @Test
    void doesNotExposeAbsolutePathInDiff() {
        List<String> newLines = List.of("modified content");
        DiffResult diff = capability.generateDiff(repoPath, "Hello.java", newLines);
        assertFalse(diff.diff().contains(repoPath),
                "diff should not contain absolute path");
        assertTrue(diff.diff().contains("a/Hello.java"));
        assertTrue(diff.diff().contains("b/Hello.java"));
    }

    @Test
    void generatesDiffWithTruncation() throws IOException {
        StringBuilder largeContent = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            largeContent.append("line ").append(i).append("\n");
        }
        Files.writeString(tempDir.resolve("Large.java"), largeContent.toString());

        List<String> newLines = List.of("modified line");
        properties.setMaxDiffLines(10);

        DiffResult diff = capability.generateDiff(repoPath, "Large.java", newLines);
        assertTrue(diff.truncated());
    }

    // ===== Patch confirmation tests =====

    @Test
    void createsPatchConfirmation() {
        List<String> newLines = List.of(
                "public class Hello {",
                "    public static void main(String[] args) {",
                "        System.out.println(\"Hello World\");",
                "    }",
                "}");

        PatchConfirmation confirmation = capability.createPatchConfirmation(
                "test-conversation", repoPath, "Hello.java", newLines);

        assertNotNull(confirmation.confirmationId());
        assertEquals("test-conversation", confirmation.conversationId());
        assertFalse(confirmation.consumed());
        assertNotNull(confirmation.diff());
    }

    @Test
    void rejectsPatchWithWrongConversation() {
        List<String> newLines = List.of("modified content");
        PatchConfirmation confirmation = capability.createPatchConfirmation(
                "conversation-1", repoPath, "Hello.java", newLines);

        assertThrows(IllegalArgumentException.class,
                () -> capability.applyPatch(confirmation.confirmationId(), "conversation-2", repoPath));
    }

    @Test
    void rejectsPatchAfterConsumption() throws IOException {
        List<String> newLines = List.of("modified content");
        PatchConfirmation confirmation = capability.createPatchConfirmation(
                "test-conversation", repoPath, "Hello.java", newLines);

        capability.applyPatch(confirmation.confirmationId(), "test-conversation", repoPath);

        assertThrows(IllegalArgumentException.class,
                () -> capability.applyPatch(confirmation.confirmationId(), "test-conversation", repoPath));
    }

    @Test
    void rejectsPatchWithDirtyWorkingTree() throws IOException {
        List<String> newLines = List.of("modified content");
        PatchConfirmation confirmation = capability.createPatchConfirmation(
                "test-conversation", repoPath, "Hello.java", newLines);

        Files.writeString(tempDir.resolve("Hello.java"), "dirty content");

        assertThrows(IllegalArgumentException.class,
                () -> capability.applyPatch(confirmation.confirmationId(), "test-conversation", repoPath));
    }

    @Test
    void rejectsPatchWithExpiredConfirmation() throws InterruptedException {
        // Use a very short TTL so the confirmation expires quickly
        properties.setPatchConfirmTtl(Duration.ofMillis(10));
        PatchConfirmationStore shortTtlStore = new PatchConfirmationStore(properties, Clock.systemUTC());

        PatchConfirmation confirmation = shortTtlStore.create(
                "test-conversation", "file-id", null, "diff");

        // Wait for the confirmation to expire
        Thread.sleep(50);

        assertThrows(IllegalArgumentException.class,
                () -> shortTtlStore.validateAndConsume(confirmation.confirmationId(), "test-conversation"));
    }

    @Test
    void clearsConversationConfirmations() {
        List<String> newLines = List.of("modified content");
        PatchConfirmation confirmation = capability.createPatchConfirmation(
                "test-conversation", repoPath, "Hello.java", newLines);

        capability.clearConversation("test-conversation");

        assertThrows(IllegalArgumentException.class,
                () -> capability.applyPatch(confirmation.confirmationId(), "test-conversation", repoPath));
    }

    // ===== Patch apply tests =====

    @Test
    void appliesPatchSuccessfully() throws IOException {
        List<String> newLines = List.of(
                "public class Hello {",
                "    public static void main(String[] args) {",
                "        System.out.println(\"Hello World\");",
                "    }",
                "}");

        PatchConfirmation confirmation = capability.createPatchConfirmation(
                "test-conversation", repoPath, "Hello.java", newLines);

        PatchApplyResult result = capability.applyPatch(
                confirmation.confirmationId(), "test-conversation", repoPath);

        assertEquals("Hello.java", result.relativePath());
        assertTrue(result.hunksApplied() > 0);

        String content = Files.readString(tempDir.resolve("Hello.java"));
        assertTrue(content.contains("Hello World"));
    }

    @Test
    void rejectsPatchExceedingSizeLimit() {
        properties.setMaxPatchBytes(10);
        List<String> newLines = List.of("this content is too long for the limit");
        PatchConfirmation confirmation = capability.createPatchConfirmation(
                "test-conversation", repoPath, "Hello.java", newLines);

        assertThrows(IllegalArgumentException.class,
                () -> capability.applyPatch(confirmation.confirmationId(), "test-conversation", repoPath));
    }

    @Test
    void rejectsRepoOutsideWhitelist() {
        assertThrows(IllegalArgumentException.class,
                () -> capability.read("C:\\Windows\\System32", "notepad.exe", null, null));
    }

    // ===== Test runner tests =====

    @Test
    void runsTestsSuccessfully() {
        TestResult result = capability.runTests(repoPath);
        assertNotNull(result);
        assertTrue(result.passed());
        assertTrue(result.totalTests() > 0);
        assertTrue(result.durationMs() > 0);
        assertFalse(result.timedOut());
    }

    @Test
    void runsTestsWithTimeout() {
        // Use ping which takes time on Windows; use system temp to avoid locking @TempDir
        properties.setTestCommand("cmd /c ping -n 5 127.0.0.1");
        properties.setTestTimeout(Duration.ofMillis(500));

        TestRunner testRunner = new TestRunner(properties);
        Path systemTemp = Path.of(System.getProperty("java.io.tmpdir"));
        TestResult result = testRunner.run(systemTemp);

        assertTrue(result.timedOut());
        assertFalse(result.passed());
    }

    @Test
    void rejectsTestWithUnconfiguredCommand() {
        properties.setTestCommand("");
        assertThrows(IllegalArgumentException.class, () -> capability.runTests(repoPath));
    }

    @Test
    void doesNotExposeEnvironmentVariables() {
        TestResult result = capability.runTests(repoPath);
        for (String failure : result.failures()) {
            assertFalse(failure.contains("PATH="), "should not expose env vars");
            assertFalse(failure.contains("HOME="), "should not expose env vars");
        }
    }
}
