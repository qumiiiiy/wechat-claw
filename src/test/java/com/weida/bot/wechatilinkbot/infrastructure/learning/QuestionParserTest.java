package com.weida.bot.wechatilinkbot.infrastructure.learning;

import static org.assertj.core.api.Assertions.assertThat;

import com.weida.bot.wechatilinkbot.model.ParseStatus;
import com.weida.bot.wechatilinkbot.model.QuestionSnapshot;
import com.weida.bot.wechatilinkbot.model.QuestionType;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QuestionParserTest {

    private QuestionParser parser;

    @BeforeEach
    void setUp() {
        parser = new QuestionParser();
    }

    // --- 正常解析 ---

    @Test
    void parseSingleChoice() {
        String html = loadFixture("question-single-choice.html");
        QuestionSnapshot snapshot = parser.parse(html, "C001", "CH01", "https://mooc1.chaoxing.com/chapter/1");

        assertThat(snapshot.parseStatus()).isEqualTo(ParseStatus.OK);
        assertThat(snapshot.questionType()).isEqualTo(QuestionType.SINGLE_CHOICE);
        assertThat(snapshot.stem()).contains("OSI参考模型");
        assertThat(snapshot.options()).hasSize(4);
        assertThat(snapshot.options().get(0).label()).isEqualTo("A");
        assertThat(snapshot.options().get(0).text()).contains("5层");
        assertThat(snapshot.allowMultiple()).isFalse();
        assertThat(snapshot.questionId()).isEqualTo("Q-SC-001");
        assertThat(snapshot.fingerprint()).startsWith("sha256:");
        assertThat(snapshot.courseId()).isEqualTo("C001");
        assertThat(snapshot.chapterId()).isEqualTo("CH01");
    }

    @Test
    void parseMultipleChoice() {
        String html = loadFixture("question-multiple-choice.html");
        QuestionSnapshot snapshot = parser.parse(html, "C001", "CH02", null);

        assertThat(snapshot.parseStatus()).isEqualTo(ParseStatus.OK);
        assertThat(snapshot.questionType()).isEqualTo(QuestionType.MULTIPLE_CHOICE);
        assertThat(snapshot.stem()).contains("传输层");
        assertThat(snapshot.options()).hasSize(5);
        assertThat(snapshot.allowMultiple()).isTrue();
    }

    @Test
    void parseTrueFalse() {
        String html = loadFixture("question-true-false.html");
        QuestionSnapshot snapshot = parser.parse(html, "C001", "CH03", null);

        assertThat(snapshot.parseStatus()).isEqualTo(ParseStatus.OK);
        assertThat(snapshot.questionType()).isEqualTo(QuestionType.TRUE_FALSE);
        assertThat(snapshot.stem()).contains("TCP协议");
        assertThat(snapshot.options()).hasSize(2);
        assertThat(snapshot.allowMultiple()).isFalse();
    }

    @Test
    void parseFillBlank() {
        String html = loadFixture("question-fill-blank.html");
        QuestionSnapshot snapshot = parser.parse(html, "C001", "CH04", null);

        assertThat(snapshot.parseStatus()).isEqualTo(ParseStatus.OK);
        assertThat(snapshot.questionType()).isEqualTo(QuestionType.FILL_BLANK);
        assertThat(snapshot.stem()).contains("加密和解密");
        assertThat(snapshot.allowMultiple()).isFalse();
    }

    @Test
    void parseUnknownType() {
        String html = loadFixture("question-unknown-type.html");
        QuestionSnapshot snapshot = parser.parse(html, "C001", "CH05", null);

        assertThat(snapshot.parseStatus()).isEqualTo(ParseStatus.OK);
        assertThat(snapshot.questionType()).isEqualTo(QuestionType.UNKNOWN);
        assertThat(snapshot.stem()).contains("连线");
    }

    // --- 边界条件 ---

    @Test
    void parseEmptyStemReturnsMissingStem() {
        String html = loadFixture("question-empty-stem.html");
        QuestionSnapshot snapshot = parser.parse(html, "C001", "CH01", null);

        assertThat(snapshot.parseStatus()).isEqualTo(ParseStatus.MISSING_STEM);
    }

    @Test
    void parseDuplicateOptionsReturnsDuplicateOptions() {
        String html = loadFixture("question-duplicate-options.html");
        QuestionSnapshot snapshot = parser.parse(html, "C001", "CH01", null);

        assertThat(snapshot.parseStatus()).isEqualTo(ParseStatus.DUPLICATE_OPTIONS);
    }

    @Test
    void parseNoStableIdReturnsNoStableId() {
        String html = loadFixture("question-no-stable-id.html");
        QuestionSnapshot snapshot = parser.parse(html, "C001", "CH01", null);

        assertThat(snapshot.parseStatus()).isEqualTo(ParseStatus.NO_STABLE_ID);
        // questionId 应降级为 fingerprint
        assertThat(snapshot.questionId()).isEqualTo(snapshot.fingerprint());
    }

    @Test
    void parseIframeNotLoaded() {
        String html = loadFixture("chapter-iframe.html");
        QuestionSnapshot snapshot = parser.parse(html, "C001", "CH02", null);

        assertThat(snapshot.parseStatus()).isEqualTo(ParseStatus.IFRAME_NOT_LOADED);
    }

    @Test
    void parseIframeContentLoaded() {
        String html = loadFixture("chapter-iframe-content.html");
        QuestionSnapshot snapshot = parser.parse(html, "C001", "CH02", null);

        assertThat(snapshot.parseStatus()).isEqualTo(ParseStatus.OK);
        assertThat(snapshot.questionType()).isEqualTo(QuestionType.MULTIPLE_CHOICE);
        assertThat(snapshot.stem()).contains("数据链路层");
    }

    @Test
    void parseAsyncLoadedQuestion() {
        String html = loadFixture("question-async-loaded.html");
        QuestionSnapshot snapshot = parser.parse(html, "C001", "CH03", null);

        assertThat(snapshot.parseStatus()).isEqualTo(ParseStatus.OK);
        assertThat(snapshot.questionType()).isEqualTo(QuestionType.SINGLE_CHOICE);
        assertThat(snapshot.stem()).contains("子网掩码");
    }

    @Test
    void parseDomChangedFallsBackGracefully() {
        String html = loadFixture("question-dom-changed.html");
        QuestionSnapshot snapshot = parser.parse(html, "C001", "CH01", null);

        // DOM 结构异常，应降级解析或返回错误
        assertThat(snapshot.parseStatus()).isIn(
                ParseStatus.OK, ParseStatus.MISSING_STEM, ParseStatus.UNKNOWN_TYPE,
                ParseStatus.EMPTY_OPTIONS, ParseStatus.PARSE_ERROR);
    }

    @Test
    void parseSsoPageReturnsUnknownType() {
        String html = loadFixture("question-ssr-sso.html");
        QuestionSnapshot snapshot = parser.parse(html, "C001", "CH01", null);

        assertThat(snapshot.parseStatus()).isEqualTo(ParseStatus.UNKNOWN_TYPE);
    }

    @Test
    void parseQrWaitingPageReturnsUnknownType() {
        String html = loadFixture("question-qr-waiting.html");
        QuestionSnapshot snapshot = parser.parse(html, "C001", "CH01", null);

        assertThat(snapshot.parseStatus()).isEqualTo(ParseStatus.UNKNOWN_TYPE);
    }

    @Test
    void parseChapterWithVideo() {
        String html = loadFixture("chapter-with-video.html");
        QuestionSnapshot snapshot = parser.parse(html, "C001", "CH01", null);

        assertThat(snapshot.parseStatus()).isEqualTo(ParseStatus.OK);
        assertThat(snapshot.questionType()).isEqualTo(QuestionType.SINGLE_CHOICE);
        assertThat(snapshot.stem()).contains("拓扑结构");
        assertThat(snapshot.options()).hasSize(4);
    }

    // --- null / 空输入 ---

    @Test
    void parseNullHtmlReturnsEmptyHtml() {
        QuestionSnapshot snapshot = parser.parse(null, "C001", "CH01", null);
        assertThat(snapshot.parseStatus()).isEqualTo(ParseStatus.EMPTY_HTML);
    }

    @Test
    void parseBlankHtmlReturnsEmptyHtml() {
        QuestionSnapshot snapshot = parser.parse("  ", "C001", "CH01", null);
        assertThat(snapshot.parseStatus()).isEqualTo(ParseStatus.EMPTY_HTML);
    }

    @Test
    void parseEmptyHtmlReturnsEmptyHtml() {
        QuestionSnapshot snapshot = parser.parse("", "C001", "CH01", null);
        assertThat(snapshot.parseStatus()).isEqualTo(ParseStatus.EMPTY_HTML);
    }

    // --- 指纹稳定性 ---

    @Test
    void fingerprintStableAcrossFormats() {
        String html1 = """
                <div class="question-area" data-qid="Q001">
                    <div class="question-stem">   OSI   参考模型共分为几层？  </div>
                    <ul class="option-list">
                        <li>A. 5层</li>
                        <li>B. 6层</li>
                        <li>C. 7层</li>
                        <li>D. 8层</li>
                    </ul>
                </div>
                """;
        String html2 = """
                <div data-qid="Q001">
                    <p class="question-stem">OSI 参考模型共分为几层？</p>
                    <ul><li>A. 5层</li><li>B. 6层</li><li>C. 7层</li><li>D. 8层</li></ul>
                </div>
                """;
        QuestionSnapshot s1 = parser.parse(html1, "C001", "CH01", null);
        QuestionSnapshot s2 = parser.parse(html2, "C001", "CH01", null);

        assertThat(s1.fingerprint()).isEqualTo(s2.fingerprint());
    }

    @Test
    void differentQuestionsHaveDifferentFingerprints() {
        String html1 = loadFixture("question-single-choice.html");
        String html2 = loadFixture("question-true-false.html");
        QuestionSnapshot s1 = parser.parse(html1, "C001", "CH01", null);
        QuestionSnapshot s2 = parser.parse(html2, "C001", "CH01", null);

        assertThat(s1.fingerprint()).isNotEqualTo(s2.fingerprint());
    }

    // --- URL 脱敏 ---

    @Test
    void pageSourceNormalized() {
        String html = loadFixture("question-single-choice.html");
        QuestionSnapshot snapshot = parser.parse(html, "C001", "CH01",
                "https://mooc1.chaoxing.com/chapter/1?token=secret&sid=12345");

        assertThat(snapshot.pageSource()).doesNotContain("token").doesNotContain("sid");
    }

    @Test
    void nullCourseAndChapterDefaultToEmpty() {
        String html = loadFixture("question-single-choice.html");
        QuestionSnapshot snapshot = parser.parse(html, null, null, null);

        assertThat(snapshot.courseId()).isEmpty();
        assertThat(snapshot.chapterId()).isEmpty();
    }

    // --- 解析空选项的单选题 ---

    @Test
    void parseSingleChoiceWithNoOptionsReturnsEmptyOptions() {
        String html = """
                <div class="question-area" data-qid="Q-EMPTY-001" data-type="SINGLE_CHOICE">
                    <div class="question-type">单选题</div>
                    <div class="question-stem">这道题没有选项</div>
                    <ul class="option-list"></ul>
                </div>
                """;
        QuestionSnapshot snapshot = parser.parse(html, "C001", "CH01", null);
        assertThat(snapshot.parseStatus()).isEqualTo(ParseStatus.EMPTY_OPTIONS);
    }

    private String loadFixture(String name) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("learning/fixtures/" + name)) {
            if (is == null) {
                throw new IllegalStateException("Fixture not found: " + name);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load fixture: " + name, e);
        }
    }
}
