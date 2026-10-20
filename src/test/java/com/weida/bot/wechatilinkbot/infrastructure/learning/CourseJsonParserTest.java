package com.weida.bot.wechatilinkbot.infrastructure.learning;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weida.bot.wechatilinkbot.infrastructure.learning.CourseJsonParser.CourseDetail;
import com.weida.bot.wechatilinkbot.model.Course;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CourseJsonParserTest {

    private CourseJsonParser parser;

    @BeforeEach
    void setUp() {
        parser = new CourseJsonParser(new ObjectMapper());
    }

    @Test
    void parseCourseListFromApiResponse() {
        String json = loadTestResource("learning/course-list-api-response.json");
        List<Course> courses = parser.parseCourseList(json);

        assertThat(courses).hasSize(3);

        Course first = courses.get(0);
        assertThat(first.courseId()).isEqualTo("100000001");
        assertThat(first.courseName()).isEqualTo("大学计算机基础");
        assertThat(first.teacherName()).isEqualTo("张教授");
        assertThat(first.schoolName()).isEqualTo("示例大学");
        assertThat(first.courseUrl()).contains("example1.png");

        Course second = courses.get(1);
        assertThat(second.courseId()).isEqualTo("100000002");
        assertThat(second.courseName()).isEqualTo("数据结构与算法");
        assertThat(second.teacherName()).isEqualTo("李教授");

        Course third = courses.get(2);
        assertThat(third.courseId()).isEqualTo("100000003");
        assertThat(third.courseName()).isEqualTo("操作系统原理");
        assertThat(third.teacherName()).isEqualTo("王教授");
    }

    @Test
    void parseCourseDetailsFromApiResponse() {
        String json = loadTestResource("learning/course-list-api-response.json");
        List<CourseDetail> details = parser.parseCourseDetails(json);

        assertThat(details).hasSize(3);

        CourseDetail first = details.get(0);
        assertThat(first.classId()).isEqualTo("100000001");
        assertThat(first.courseId()).isEqualTo("100000001");
        assertThat(first.cpi()).isEqualTo("synthetic-cpi-1");
        assertThat(first.enc()).isEqualTo("enc_value_001");
        assertThat(first.courseName()).isEqualTo("大学计算机基础");
        assertThat(first.className()).isEqualTo("默认班级");
        assertThat(first.teacher()).isEqualTo("张教授");
        assertThat(first.school()).isEqualTo("示例大学");
        assertThat(first.studentCount()).isEqualTo(50);
        assertThat(first.beginDate()).isEqualTo("2026-03-01 00:00");
        assertThat(first.endDate()).isEqualTo("2026-06-30 23:59");
        assertThat(first.state()).isEqualTo(1);
        assertThat(first.enc()).isEqualTo("enc_value_001");
        assertThat(details.get(1).cpi()).isEqualTo("synthetic-cpi-2");
        assertThat(details.get(1).enc()).isEqualTo("enc_value_002");
        assertThat(details.get(2).enc()).isEqualTo("enc_value_003");
    }

    @Test
    void preservesStringCourseAndClassIdentifiers() {
        String json = """
            {
              "channelList": [
                {
                  "key": "class-encoded",
                  "cpi": "cpi-encoded",
                  "content": {
                    "enc": "enc-encoded",
                    "course": {
                      "data": [
                        {"id": "course-encoded", "name": "编码课程"}
                      ]
                    }
                  }
                }
              ]
            }
            """;

        List<CourseDetail> details = parser.parseCourseDetails(json);

        assertThat(details).singleElement().satisfies(detail -> {
            assertThat(detail.classId()).isEqualTo("class-encoded");
            assertThat(detail.courseId()).isEqualTo("course-encoded");
            assertThat(detail.cpi()).isEqualTo("cpi-encoded");
            assertThat(detail.enc()).isEqualTo("enc-encoded");
        });
    }

    @Test
    void parseEmptyJson() {
        List<Course> courses = parser.parseCourseList("{}");
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
    void parseInvalidJson() {
        List<Course> courses = parser.parseCourseList("这不是JSON");
        assertThat(courses).isEmpty();
    }

    @Test
    void parseJsonWithEmptyChannelList() {
        String json = "{\"channelList\":[]}";
        List<Course> courses = parser.parseCourseList(json);
        assertThat(courses).isEmpty();
    }

    @Test
    void parseJsonWithMissingCourseData() {
        String json = """
            {
              "channelList": [
                {
                  "key": 123,
                  "content": {
                    "name": "测试班级",
                    "course": {"data": []}
                  }
                }
              ]
            }
            """;
        List<Course> courses = parser.parseCourseList(json);
        assertThat(courses).isEmpty();
    }

    @Test
    void parseJsonWithNullFields() {
        String json = """
            {
              "channelList": [
                {
                  "key": 123,
                  "content": {
                    "course": {
                      "data": [
                        {
                          "id": 999,
                          "name": "测试课程",
                          "teacherfactor": null,
                          "schools": null
                        }
                      ]
                    }
                  }
                }
              ]
            }
            """;
        List<Course> courses = parser.parseCourseList(json);

        assertThat(courses).hasSize(1);
        Course course = courses.get(0);
        assertThat(course.courseId()).isEqualTo("999");
        assertThat(course.courseName()).isEqualTo("测试课程");
        assertThat(course.teacherName()).isEmpty();
        assertThat(course.schoolName()).isEmpty();
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
