package com.weida.bot.wechatilinkbot.capability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileBrowseCapabilityTest {

    @TempDir
    Path tempDir;

    private FileBrowseProperties properties;
    private FileBrowseCapability capability;

    @BeforeEach
    void setUp() {
        properties = new FileBrowseProperties();
        properties.setEnabled(true);
    }

    @Test
    void searchesFilesByKeywordSuccessfully() throws IOException {
        // 准备测试数据
        Path allowedDir = tempDir.resolve("allowed");
        Files.createDirectories(allowedDir);
        Files.writeString(allowedDir.resolve("test-file.txt"), "content1");
        Files.writeString(allowedDir.resolve("test-data.csv"), "content2");
        Files.writeString(allowedDir.resolve("other.txt"), "content3");

        properties.setAllowedPaths(List.of(allowedDir.toString()));
        properties.setMaxResults(10);
        properties.setMaxDepth(1);
        capability = new FileBrowseCapability(properties);

        // 执行搜索
        List<FileBrowseCapability.FileSearchResult> results = capability.search("test");

        // 验证结果
        assertThat(results).hasSize(2);
        assertThat(results)
                .extracting(FileBrowseCapability.FileSearchResult::relativePath)
                .containsExactlyInAnyOrder("test-file.txt", "test-data.csv");
        assertThat(results.get(0).fileId()).isNotBlank();
        assertThat(results.get(0).sizeBytes()).isGreaterThan(0);
        assertThat(results.get(0).lastModifiedTime()).isNotNull();
    }

    @Test
    void wildcardScopeExpandsToTheCurrentFileSystemRoots() throws IOException {
        properties.setAllowedPaths(List.of("*"));
        capability = new FileBrowseCapability(properties);

        Path currentRoot = Path.of(System.getProperty("user.dir"))
                .toRealPath()
                .getRoot();

        assertThat(capability.available()).isTrue();
        assertThat(capability.resolveAllowedRoots())
                .startsWith(Path.of(System.getProperty("user.dir")).toRealPath())
                .contains(currentRoot);
    }

    @Test
    void structuredSearchHandlesAFileSystemRootWithoutAFileName() throws IOException {
        Path fileSystemRoot = Path.of(System.getProperty("user.dir")).toRealPath().getRoot();
        properties.setAllowedPaths(List.of(fileSystemRoot.toString()));
        properties.setMaxDepth(0);
        capability = new FileBrowseCapability(properties);

        List<StructuredFileResult> results = capability.searchStructured(
                new FileBrowseQuery(
                        List.of(),
                        List.of(),
                        null,
                        null,
                        FileBrowseQuery.FileSortCriteria.NAME_ASC),
                "conversation");

        assertThat(results).isEmpty();
    }

    @Test
    void structuredSearchGivesEachAllowedRootItsOwnEntryBudget() throws IOException {
        Path firstRoot = tempDir.resolve("first-root");
        Path secondRoot = tempDir.resolve("second-root");
        Files.createDirectories(firstRoot);
        Files.createDirectories(secondRoot);
        for (int index = 0; index < 5; index++) {
            Files.writeString(firstRoot.resolve("noise-" + index + ".txt"), "noise");
        }
        Files.writeString(secondRoot.resolve("target.doc"), "target");

        properties.setAllowedPaths(List.of(firstRoot.toString(), secondRoot.toString()));
        properties.setMaxEntries(2);
        properties.setMaxDepth(1);
        properties.setAllowedExtensions(List.of("doc", "txt"));
        capability = new FileBrowseCapability(properties);

        List<StructuredFileResult> results = capability.searchStructured(
                new FileBrowseQuery(List.of("target"), List.of("doc"), null, null,
                        FileBrowseQuery.FileSortCriteria.NAME_ASC),
                "conversation");

        assertThat(results).extracting(StructuredFileResult::displayName)
                .containsExactly("target.doc");
    }

    @Test
    void structuredSearchRestrictsAnAbsoluteDirectoryHintToThatDirectory() throws IOException {
        Path targetDirectory = tempDir.resolve("campus").resolve("self");
        Path otherDirectory = tempDir.resolve("campus").resolve("other");
        Files.createDirectories(targetDirectory);
        Files.createDirectories(otherDirectory);
        Files.writeString(targetDirectory.resolve("target.txt"), "target");
        Files.writeString(otherDirectory.resolve("other.txt"), "other");

        properties.setAllowedPaths(List.of(tempDir.toString()));
        properties.setMaxDepth(4);
        properties.setMaxResults(10);
        capability = new FileBrowseCapability(properties);

        List<StructuredFileResult> results = capability.searchStructured(
                new FileBrowseQuery(
                        List.of(),
                        List.of("txt"),
                        targetDirectory.toString(),
                        null,
                        FileBrowseQuery.FileSortCriteria.NAME_ASC),
                "conversation");

        assertThat(results).extracting(StructuredFileResult::displayName)
                .containsExactly("target.txt");
    }

    @Test
    void structuredSearchWithAnAncestorDirectoryHintFindsNestedFiles() throws IOException {
        Path allowedDir = tempDir.resolve("allowed");
        Path nestedDir = allowedDir.resolve("sub").resolve("deep");
        Files.createDirectories(nestedDir);
        Files.writeString(nestedDir.resolve("app.exe"), "binary");
        Files.writeString(allowedDir.resolve("root.txt"), "text");

        properties.setAllowedPaths(List.of(allowedDir.toString()));
        properties.setAllowedExtensions(List.of("txt", "exe"));
        properties.setMaxDepth(5);
        properties.setMaxResults(10);
        capability = new FileBrowseCapability(properties);

        List<StructuredFileResult> results = capability.searchStructured(
                new FileBrowseQuery(
                        List.of(),
                        List.of("exe"),
                        allowedDir.toString(),
                        null,
                        FileBrowseQuery.FileSortCriteria.NAME_ASC),
                "conversation");

        assertThat(results).extracting(StructuredFileResult::displayName)
                .containsExactly("app.exe");
    }

    @Test
    void recentFirstSortsAllMatchingFilesWithoutATimeWindow() throws IOException {
        Path allowedDir = tempDir.resolve("allowed");
        Files.createDirectories(allowedDir);
        Path oldExe = allowedDir.resolve("old.exe");
        Path newExe = allowedDir.resolve("new.exe");
        Files.writeString(oldExe, "old");
        Files.writeString(newExe, "new");
        Files.setLastModifiedTime(oldExe, FileTime.from(Instant.parse("2025-01-01T00:00:00Z")));
        Files.setLastModifiedTime(newExe, FileTime.from(Instant.parse("2026-08-01T00:00:00Z")));

        properties.setAllowedPaths(List.of(allowedDir.toString()));
        properties.setAllowedExtensions(List.of("exe"));
        properties.setMaxDepth(1);
        properties.setMaxResults(10);
        capability = new FileBrowseCapability(properties);

        List<StructuredFileResult> results = capability.searchStructured(
                new FileBrowseQuery(
                        List.of(),
                        List.of("exe"),
                        null,
                        null,
                        FileBrowseQuery.FileSortCriteria.RECENT_FIRST),
                "conversation");

        assertThat(results).extracting(StructuredFileResult::displayName)
                .containsExactly("new.exe", "old.exe");
    }

    @Test
    void structuredSearchWithJunkSuffixedDriveHintFallsBackToDriveRoot() throws IOException {
        Path allowedDir = tempDir.resolve("allowed");
        Path nestedDir = allowedDir.resolve("program").resolve("bin");
        Files.createDirectories(nestedDir);
        Files.writeString(nestedDir.resolve("setup.exe"), "binary");

        properties.setAllowedPaths(List.of(allowedDir.toString()));
        properties.setAllowedExtensions(List.of("exe"));
        properties.setMaxDepth(5);
        properties.setMaxResults(10);
        capability = new FileBrowseCapability(properties);

        String hint = allowedDir.toString() + "下";
        List<StructuredFileResult> results = capability.searchStructured(
                new FileBrowseQuery(
                        List.of(),
                        List.of("exe"),
                        hint,
                        null,
                        FileBrowseQuery.FileSortCriteria.NAME_ASC),
                "conversation");

        assertThat(results).extracting(StructuredFileResult::displayName)
                .containsExactly("setup.exe");
    }

    @Test
    void structuredSearchMatchesAnySemanticNameFragment() throws IOException {
        Path allowedDir = tempDir.resolve("allowed");
        Files.createDirectories(allowedDir);
        Files.writeString(allowedDir.resolve("课程表2026.xlsx"), "content1");
        Files.writeString(allowedDir.resolve("课表.pdf"), "content2");
        Files.writeString(allowedDir.resolve("作业.docx"), "content3");

        properties.setAllowedPaths(List.of(allowedDir.toString()));
        properties.setMaxDepth(1);
        properties.setMaxResults(10);
        capability = new FileBrowseCapability(properties);

        List<StructuredFileResult> results = capability.searchStructured(
                new FileBrowseQuery(
                        List.of("课表", "课程表"),
                        List.of(),
                        null,
                        null,
                        FileBrowseQuery.FileSortCriteria.NAME_ASC),
                "conversation");

        assertThat(results)
                .extracting(StructuredFileResult::displayName)
                .containsExactlyInAnyOrder("课程表2026.xlsx", "课表.pdf")
                .doesNotContain("作业.docx");
    }

    @Test
    void structuredSearchRanksFilesMatchingMoreFragmentsFirst() throws IOException {
        Path allowedDir = tempDir.resolve("allowed");
        Files.createDirectories(allowedDir);
        Files.writeString(allowedDir.resolve("项目周报-2026-08.xlsx"), "content1");
        Files.writeString(allowedDir.resolve("周报.docx"), "content2");

        properties.setAllowedPaths(List.of(allowedDir.toString()));
        properties.setMaxDepth(1);
        properties.setMaxResults(10);
        capability = new FileBrowseCapability(properties);

        List<StructuredFileResult> results = capability.searchStructured(
                new FileBrowseQuery(
                        List.of("项目", "周报"),
                        List.of(),
                        null,
                        null,
                        FileBrowseQuery.FileSortCriteria.NAME_ASC),
                "conversation");

        assertThat(results)
                .extracting(StructuredFileResult::displayName)
                .containsExactly("项目周报-2026-08.xlsx", "周报.docx");
    }

    @Test
    void rejectsSearchWhenDisabled() {
        properties.setEnabled(false);
        properties.setAllowedPaths(List.of(tempDir.toString()));
        capability = new FileBrowseCapability(properties);

        assertThatThrownBy(() -> capability.search("test"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("文件搜索功能未启用");
    }

    @Test
    void rejectsSearchWithEmptyKeyword() {
        properties.setAllowedPaths(List.of(tempDir.toString()));
        capability = new FileBrowseCapability(properties);

        assertThatThrownBy(() -> capability.search(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("搜索关键词不能为空");

        assertThatThrownBy(() -> capability.search("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("搜索关键词不能为空");
    }

    @Test
    void rejectsSearchWithTooLongKeyword() {
        properties.setAllowedPaths(List.of(tempDir.toString()));
        properties.setMaxKeywordLength(5);
        capability = new FileBrowseCapability(properties);

        assertThatThrownBy(() -> capability.search("123456"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("搜索关键词超过长度限制");
    }

    @Test
    void limitsResultsByMaxResults() throws IOException {
        // 准备超过限制的测试数据
        Path allowedDir = tempDir.resolve("allowed");
        Files.createDirectories(allowedDir);
        for (int i = 1; i <= 10; i++) {
            Files.writeString(allowedDir.resolve("test-" + i + ".txt"), "content");
        }

        properties.setAllowedPaths(List.of(allowedDir.toString()));
        properties.setMaxResults(3);
        properties.setMaxDepth(1);
        capability = new FileBrowseCapability(properties);

        // 执行搜索
        List<FileBrowseCapability.FileSearchResult> results = capability.search("test");

        // 验证结果数量被限制
        assertThat(results).hasSize(3);
    }

    @Test
    void searchesRecursivelyWithinMaxDepth() throws IOException {
        // 准备嵌套目录结构
        Path allowedDir = tempDir.resolve("allowed");
        Path subDir = allowedDir.resolve("subdir");
        Files.createDirectories(subDir);
        Files.writeString(allowedDir.resolve("root-test.txt"), "content1");
        Files.writeString(subDir.resolve("nested-test.txt"), "content2");

        properties.setAllowedPaths(List.of(allowedDir.toString()));
        properties.setMaxResults(10);
        properties.setMaxDepth(2);
        capability = new FileBrowseCapability(properties);

        // 执行搜索
        List<FileBrowseCapability.FileSearchResult> results = capability.search("test");

        // 验证能搜索到嵌套文件
        assertThat(results).hasSize(2);
        assertThat(results)
                .extracting(FileBrowseCapability.FileSearchResult::relativePath)
                .containsExactlyInAnyOrder("root-test.txt", "subdir" + java.io.File.separator + "nested-test.txt");
    }

    @Test
    void onlySearchesInAllowedPaths() throws IOException {
        // 准备两个目录，只允许搜索其中一个
        Path allowedDir = tempDir.resolve("allowed");
        Path forbiddenDir = tempDir.resolve("forbidden");
        Files.createDirectories(allowedDir);
        Files.createDirectories(forbiddenDir);
        Files.writeString(allowedDir.resolve("allowed-test.txt"), "content1");
        Files.writeString(forbiddenDir.resolve("forbidden-test.txt"), "content2");

        properties.setAllowedPaths(List.of(allowedDir.toString()));
        properties.setMaxResults(10);
        properties.setMaxDepth(1);
        capability = new FileBrowseCapability(properties);

        // 执行搜索
        List<FileBrowseCapability.FileSearchResult> results = capability.search("test");

        // 验证只返回允许目录的结果
        assertThat(results).hasSize(1);
        assertThat(results.get(0).relativePath()).isEqualTo("allowed-test.txt");
    }

    @Test
    void handlesCaseInsensitiveSearch() throws IOException {
        Path allowedDir = tempDir.resolve("allowed");
        Files.createDirectories(allowedDir);
        Files.writeString(allowedDir.resolve("TEST-FILE.txt"), "content1");
        Files.writeString(allowedDir.resolve("test-data.csv"), "content2");
        Files.writeString(allowedDir.resolve("Test-Report.md"), "content3");

        properties.setAllowedPaths(List.of(allowedDir.toString()));
        properties.setMaxResults(10);
        properties.setMaxDepth(1);
        capability = new FileBrowseCapability(properties);

        // 使用不同大小写搜索
        List<FileBrowseCapability.FileSearchResult> results = capability.search("test");

        // 验证不区分大小写
        assertThat(results).hasSize(3);
    }

    @Test
    void selectsSearchedFileByOpaqueId() throws IOException {
        Path allowedDir = tempDir.resolve("allowed");
        Files.createDirectories(allowedDir);
        Path file = allowedDir.resolve("demo.txt");
        Files.writeString(file, "afternoon-demo");
        properties.setAllowedPaths(List.of(allowedDir.toString()));
        capability = new FileBrowseCapability(properties);

        FileBrowseCapability.FileSearchResult result = capability.search("demo").getFirst();
        var media = capability.select(result.fileId());

        assertThat(result.fileId()).startsWith("file-").doesNotContain("demo.txt");
        assertThat(media.fileName()).isEqualTo("demo.txt");
        assertThat(media.bytes()).isEqualTo("afternoon-demo".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void excludesSensitiveAndExecutableFiles() throws IOException {
        Path allowedDir = tempDir.resolve("allowed");
        Files.createDirectories(allowedDir.resolve(".git"));
        Files.writeString(allowedDir.resolve(".env"), "TOKEN=secret");
        Files.writeString(allowedDir.resolve("run.exe"), "binary");
        Files.writeString(allowedDir.resolve(".git").resolve("config.txt"), "secret");
        properties.setAllowedPaths(List.of(allowedDir.toString()));
        properties.setAllowedExtensions(List.of("txt", "exe", "env"));
        capability = new FileBrowseCapability(properties);

        assertThat(capability.search("env")).isEmpty();
        assertThat(capability.search("run")).hasSize(1);
        assertThat(capability.search("config")).isEmpty();
    }

    @Test
    void rejectsExpiredSelection() throws IOException {
        Path allowedDir = tempDir.resolve("allowed");
        Files.createDirectories(allowedDir);
        Files.writeString(allowedDir.resolve("demo.txt"), "demo");
        properties.setAllowedPaths(List.of(allowedDir.toString()));
        properties.setSelectionExpireAfter(Duration.ofSeconds(1));
        MutableClock clock = new MutableClock(Instant.parse("2026-08-01T00:00:00Z"));
        capability = new FileBrowseCapability(properties, clock, System::nanoTime);
        String id = capability.search("demo").getFirst().fileId();

        clock.advance(Duration.ofSeconds(2));

        assertThatThrownBy(() -> capability.select(id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("无效或已过期");
    }

    @Test
    void rejectsFileChangedAfterSearch() throws IOException {
        Path allowedDir = tempDir.resolve("allowed");
        Files.createDirectories(allowedDir);
        Path file = allowedDir.resolve("demo.txt");
        Files.writeString(file, "before");
        properties.setAllowedPaths(List.of(allowedDir.toString()));
        capability = new FileBrowseCapability(properties);
        String id = capability.search("demo").getFirst().fileId();

        Files.writeString(file, "after-change");
        Files.setLastModifiedTime(file, FileTime.from(Instant.now().plusSeconds(2)));

        assertThatThrownBy(() -> capability.select(id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("发生变化");
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
