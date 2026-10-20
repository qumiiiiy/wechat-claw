package com.weida.bot.wechatilinkbot.capability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class FileBrowseQueryCompilerTest {

    private final FileBrowseQueryCompiler compiler = new FileBrowseQueryCompiler(
            Clock.fixed(Instant.parse("2026-08-05T00:00:00Z"), ZoneOffset.UTC),
            50,
            5);

    @Test
    void compilesStructuredNameFragmentsAndTypes() {
        FileBrowseQuery query = compiler.compile(new FileBrowseSearchRequest(
                List.of("实验报告", "实验"),
                List.of("word", "docx"),
                null,
                null,
                null,
                null));

        assertThat(query.nameFragments()).containsExactly("实验报告", "实验");
        assertThat(query.fileTypes()).containsExactly("word", "docx");
        assertThat(query.sortCriteria()).isEqualTo(FileBrowseQuery.FileSortCriteria.NAME_ASC);
        assertThat(query.modifiedAfter()).isNull();
        assertThat(query.directoriesOnly()).isFalse();
    }

    @Test
    void compilesRecentFirstWithoutATimeWindow() {
        FileBrowseQuery query = compiler.compile(new FileBrowseSearchRequest(
                List.of(),
                List.of("pdf"),
                "桌面",
                "recent_first",
                null,
                null));

        assertThat(query.directoryHint()).isEqualTo("桌面");
        assertThat(query.fileTypes()).containsExactly("pdf");
        assertThat(query.sortCriteria()).isEqualTo(FileBrowseQuery.FileSortCriteria.RECENT_FIRST);
        assertThat(query.modifiedAfter()).isNull();
    }

    @Test
    void normalizesChineseDriveHintsAndBackslashes() {
        FileBrowseQuery query = compiler.compile(new FileBrowseSearchRequest(
                List.of(),
                List.of(),
                "D盘campus\\self",
                null,
                null,
                null));

        assertThat(query.directoryHint()).isEqualTo("d:/campus/self");
    }

    @Test
    void normalizesABareDriveLetterHintToTheDriveRoot() {
        FileBrowseQuery query = compiler.compile(new FileBrowseSearchRequest(
                List.of(),
                List.of("exe"),
                "D:",
                null,
                null,
                null));

        assertThat(query.directoryHint()).isEqualTo("d:/");
    }

    @Test
    void preservesAnExplicitFilenameIncludingItsExtension() {
        FileBrowseQuery query = compiler.compile(new FileBrowseSearchRequest(
                List.of("在两岸融合发展中承担青年责任.doc"),
                List.of(),
                null,
                null,
                null,
                null));

        assertThat(query.nameFragments()).containsExactly("在两岸融合发展中承担青年责任.doc");
        assertThat(query.hasNameFragments()).isTrue();
    }

    @Test
    void compilesDirectoryOnlyListing() {
        FileBrowseQuery query = compiler.compile(new FileBrowseSearchRequest(
                List.of(),
                List.of(),
                null,
                null,
                true,
                null));

        assertThat(query.directoriesOnly()).isTrue();
    }

    @Test
    void acceptsRecentFileReferenceAsTheOnlyCondition() {
        FileBrowseQuery query = compiler.compile(new FileBrowseSearchRequest(
                List.of(),
                List.of(),
                null,
                null,
                null,
                true));

        assertThat(query.hasNameFragments()).isFalse();
    }

    @Test
    void rejectsBlankOrOverlongConditions() {
        assertThatThrownBy(() -> compiler.compile(new FileBrowseSearchRequest(
                List.of(),
                List.of(),
                null,
                null,
                null,
                null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能为空");
        assertThatThrownBy(() -> compiler.compile(new FileBrowseSearchRequest(
                List.of("x".repeat(51)),
                List.of(),
                null,
                null,
                null,
                null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("长度限制");
        assertThatThrownBy(() -> compiler.compile(new FileBrowseSearchRequest(
                List.of("a", "b", "c", "d", "e", "f"),
                List.of(),
                null,
                null,
                null,
                null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("最多");
    }

    @Test
    void rejectsPathSeparatorsParentComponentsAndInvalidTypes() {
        assertThatThrownBy(() -> compiler.compile(new FileBrowseSearchRequest(
                List.of("a/b"),
                List.of(),
                null,
                null,
                null,
                null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("路径分隔符");
        assertThatThrownBy(() -> compiler.compile(new FileBrowseSearchRequest(
                List.of(),
                List.of(),
                "D:/..",
                null,
                null,
                null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("上级路径");
        assertThatThrownBy(() -> compiler.compile(new FileBrowseSearchRequest(
                List.of(),
                List.of("bad.type"),
                null,
                null,
                null,
                null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("合法扩展名");
    }

    @Test
    void rejectsUnknownSortCriteria() {
        assertThatThrownBy(() -> compiler.compile(new FileBrowseSearchRequest(
                List.of("a"),
                List.of(),
                null,
                "random",
                null,
                null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("排序条件");
    }
}
