package com.weida.bot.wechatilinkbot.infrastructure.learning;

import com.weida.bot.wechatilinkbot.model.Course;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CourseListParserTest {

    private CourseListParser parser;

    @BeforeEach
    void setUp() {
        parser = new CourseListParser();
    }

    @Test
    void parseMoocStructureCourseList() {
        String html = loadTestResource("learning/course-list-sample.html");
        List<Course> courses = parser.parseCourseList(html);

        assertThat(courses).hasSize(3);

        Course first = courses.get(0);
        assertThat(first.courseId()).isEqualTo("MjAwMDAwMDAx");
        assertThat(first.courseName()).isEqualTo("大学计算机基础");
        assertThat(first.teacherName()).isEqualTo("张教授");
        assertThat(first.schoolName()).isEqualTo("示例大学");
        assertThat(first.courseUrl()).contains("/mooc-ans/course/portal/MjAwMDAwMDAx");

        Course second = courses.get(1);
        assertThat(second.courseId()).isEqualTo("MjAwMDAwMDAy");
        assertThat(second.courseName()).isEqualTo("数据结构与算法");
        assertThat(second.teacherName()).isEqualTo("李教授");

        Course third = courses.get(2);
        assertThat(third.courseId()).isEqualTo("MjAwMDAwMDAz");
        assertThat(third.courseName()).isEqualTo("操作系统原理");
        assertThat(third.teacherName()).isEqualTo("王教授");
    }

    @Test
    void parseSyntheticStructureCourseList() {
        String html = """
            <html><body>
            <div class="course-item" data-courseid="200000001">
                <a class="course-link" href="/course/200000001">
                    <div class="course-name">合成样本课程</div>
                </a>
                <div class="course-teacher">测试教师</div>
                <div class="course-school">测试学校</div>
            </div>
            </body></html>
            """;
        List<Course> courses = parser.parseCourseList(html);

        assertThat(courses).hasSize(1);
        Course course = courses.get(0);
        assertThat(course.courseId()).isEqualTo("200000001");
        assertThat(course.courseName()).isEqualTo("合成样本课程");
        assertThat(course.teacherName()).isEqualTo("测试教师");
        assertThat(course.schoolName()).isEqualTo("测试学校");
    }

    @Test
    void parseEmptyCourseList() {
        String html = loadTestResource("learning/empty-course-list.html");
        List<Course> courses = parser.parseCourseList(html);

        assertThat(courses).isEmpty();
    }

    @Test
    void parseMalformedPage() {
        String html = loadTestResource("learning/malformed-page.html");
        List<Course> courses = parser.parseCourseList(html);

        assertThat(courses).isEmpty();
    }

    @Test
    void parseNullInput() {
        List<Course> courses = parser.parseCourseList(null);
        assertThat(courses).isEmpty();
    }

    @Test
    void parseBlankInput() {
        List<Course> courses = parser.parseCourseList("   ");
        assertThat(courses).isEmpty();
    }

    @Test
    void parseEmptyStringInput() {
        List<Course> courses = parser.parseCourseList("");
        assertThat(courses).isEmpty();
    }

    @Test
    void parseInvalidHtml() {
        List<Course> courses = parser.parseCourseList("这不是HTML");
        assertThat(courses).isEmpty();
    }

    @Test
    void parseMoocStructureWithMissingOptionalFields() {
        String html = """
            <html><body>
            <div class="listCon">
                <dl>
                    <dt><a href="/mooc-ans/course/portal/abc123"><img src="test.jpg"/></a></dt>
                    <dd>
                        <h3><a href="/mooc-ans/course/portal/abc123" title="只有必填字段">只有必填字段</a></h3>
                    </dd>
                </dl>
            </div>
            </body></html>
            """;
        List<Course> courses = parser.parseCourseList(html);

        assertThat(courses).hasSize(1);
        Course course = courses.get(0);
        assertThat(course.courseId()).isEqualTo("abc123");
        assertThat(course.courseName()).isEqualTo("只有必填字段");
        assertThat(course.teacherName()).isEmpty();
        assertThat(course.schoolName()).isEmpty();
    }

    @Test
    void extractCourseIdFromVariousUrlFormats() {
        // 测试不同的 URL 格式
        String html1 = """
            <html><body>
            <div class="listCon">
                <dl>
                    <dt><a href="/mooc-ans/course/portal/axPm0q07Dmey7ur-X6SIaw=="><img/></a></dt>
                    <dd><h3><a href="/mooc-ans/course/portal/axPm0q07Dmey7ur-X6SIaw==" title="课程1">课程1</a></h3></dd>
                </dl>
            </div>
            </body></html>
            """;
        List<Course> courses1 = parser.parseCourseList(html1);
        assertThat(courses1).hasSize(1);
        assertThat(courses1.get(0).courseId()).isEqualTo("axPm0q07Dmey7ur-X6SIaw==");

        // 测试带参数的 URL
        String html2 = """
            <html><body>
            <div class="listCon">
                <dl>
                    <dt><a href="/mooc-ans/course/portal/xyz789?uid=123"><img/></a></dt>
                    <dd><h3><a href="/mooc-ans/course/portal/xyz789?uid=123" title="课程2">课程2</a></h3></dd>
                </dl>
            </div>
            </body></html>
            """;
        List<Course> courses2 = parser.parseCourseList(html2);
        assertThat(courses2).hasSize(1);
        assertThat(courses2.get(0).courseId()).isEqualTo("xyz789");
    }

    private String loadTestResource(String resourcePath) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new RuntimeException("Test resource not found: " + resourcePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load test resource: " + resourcePath, e);
        }
    }
}
