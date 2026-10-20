package com.weida.bot.wechatilinkbot.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentUserOutputRendererTest {

    @Test
    void rendersFileResultsAndStoresOnlyNaturalSelectionContext() {
        ConversationReferenceStore store = new ConversationReferenceStore(
                Duration.ofMinutes(30),
                20,
                Clock.fixed(Instant.parse("2026-08-05T00:00:00Z"), ZoneOffset.UTC));
        AgentUserOutputRenderer renderer = new AgentUserOutputRenderer(
                new ObjectMapper(),
                store);

        String rendered = renderer.render(
                "wechat|a",
                """
                        {"status":"ok","query":"最近修改的 PDF","sort_criteria":"RECENT_FIRST",
                         "results":[{"index":1,"file_name":"课程表.pdf","directory":"桌面\\\\课程",
                         "size_human":"12 KB","last_modified":"2026-08-04T16:00:00Z",
                         "file_id":"file-12345678-1234-1234-1234-123456789abc"}]}
                        """,
                List.of());

        assertThat(rendered)
                .contains("课程表.pdf", "这个文件", "修改于")
                .doesNotContain("file-12345678-1234-1234-1234-123456789abc")
                .doesNotContain("sort_criteria", "绝对路径");
        assertThat(store.resolve(
                "wechat|a",
                ConversationReferenceStore.ReferenceType.FILE,
                "把第一个发给我")).get()
                .extracting(ConversationReferenceStore.Reference::identifier)
                .isEqualTo("file-12345678-1234-1234-1234-123456789abc");
    }

    @Test
    void stripsInternalIdsPathsAndStatusEnumsFromTextResults() {
        ConversationReferenceStore store = new ConversationReferenceStore(
                Duration.ofMinutes(30),
                20,
                Clock.fixed(Instant.parse("2026-08-05T00:00:00Z"), ZoneOffset.UTC));
        AgentUserOutputRenderer renderer = new AgentUserOutputRenderer(
                new ObjectMapper(),
                store);

        String rendered = renderer.render(
                "wechat|a",
                """
                        [C1] 高等数学
                        1. [TD-123456789abc] 提交作业
                        任务 DT-12345678 当前状态：RUNNING
                        OCS 学习会话 OS-12345678 状态：WAITING_LOGIN
                        文件位置：C:\\Users\\white\\secret\\report.docx
                        """,
                List.of());

        assertThat(rendered)
                .contains("高等数学", "提交作业", "进行中", "等待登录")
                .doesNotContain("[C1]", "TD-123456789abc", "DT-12345678", "OS-12345678")
                .doesNotContain("C:\\Users\\white\\secret");
        assertThat(store.resolve(
                "wechat|a",
                ConversationReferenceStore.ReferenceType.COURSE,
                "开始第一门课程")).get()
                .extracting(ConversationReferenceStore.Reference::identifier)
                .isEqualTo("C1");
        assertThat(store.resolve(
                "wechat|a",
                ConversationReferenceStore.ReferenceType.TODO,
                "完成第一项")).get()
                .extracting(ConversationReferenceStore.Reference::identifier)
                .isEqualTo("TD-123456789ABC");
    }

    @Test
    void turnsStructuredErrorsIntoAContinueablePhoneMessage() {
        AgentUserOutputRenderer renderer = new AgentUserOutputRenderer(
                new ObjectMapper(),
                new ConversationReferenceStore(
                        Duration.ofMinutes(30),
                        20,
                        Clock.systemUTC()));

        String rendered = renderer.render(
                "wechat|a",
                "{\"status\":\"error\",\"message\":\"读取 C:\\\\private\\\\secret.txt 失败\"}",
                List.of());

        assertThat(rendered)
                .contains("读取", "失败")
                .doesNotContain("C:\\private\\secret.txt", "status", "error");
    }

    @Test
    void rendersDirectoryResultsWithoutCreatingFileSelectionReferences() {
        ConversationReferenceStore store = new ConversationReferenceStore(
                Duration.ofMinutes(30),
                20,
                Clock.fixed(Instant.parse("2026-08-05T00:00:00Z"), ZoneOffset.UTC));
        AgentUserOutputRenderer renderer = new AgentUserOutputRenderer(
                new ObjectMapper(),
                store);

        String rendered = renderer.render(
                "wechat|a",
                """
                        {"status":"ok","entry_type":"directory","result_count":2,
                         "directories":[
                           {"index":1,"directory_name":"campus","relative_path":"campus"},
                           {"index":2,"directory_name":"self","relative_path":"campus/self"}]}
                        """,
                List.of());

        assertThat(rendered)
                .contains("找到 2 个文件夹", "campus", "campus/self")
                .doesNotContain("file_id", "绝对路径");
        assertThat(store.resolve(
                "wechat|a",
                ConversationReferenceStore.ReferenceType.FILE,
                "把第一个发给我")).isEmpty();
    }
}
