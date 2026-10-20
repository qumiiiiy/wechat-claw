package com.weida.bot.wechatilinkbot.infrastructure.learning;

import com.weida.bot.wechatilinkbot.model.Course;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 学习通课程列表解析器。
 * 从 HTML 页面提取课程信息，支持离线解析。
 *
 * 支持两种页面结构：
 * 1. MOOC 平台结构（dl > dd > h3 > a）
 * 2. 合成样本结构（div.course-item）
 */
@Component
public class CourseListParser {

    private static final Pattern COURSE_ID_FROM_URL = Pattern.compile(
            "/mooc-ans/course/portal/([^/\"'&?\\s]+)"
    );

    /**
     * 解析课程列表 HTML，提取课程信息。
     *
     * @param html HTML 内容
     * @return 课程列表，解析失败返回空列表
     */
    public List<Course> parseCourseList(String html) {
        if (html == null || html.isBlank()) {
            return Collections.emptyList();
        }

        try {
            Document doc = Jsoup.parse(html);

            // 尝试 MOOC 平台结构（dl 标签）
            List<Course> courses = parseMoocStructure(doc);
            if (!courses.isEmpty()) {
                return courses;
            }

            // 尝试合成样本结构（div.course-item）
            courses = parseSyntheticStructure(doc);
            if (!courses.isEmpty()) {
                return courses;
            }

            return Collections.emptyList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * 解析 MOOC 平台结构：dl > dd > h3 > a
     */
    private List<Course> parseMoocStructure(Document doc) {
        Elements dlItems = doc.select(".listCon dl, dl");

        if (dlItems.isEmpty()) {
            return Collections.emptyList();
        }

        List<Course> courses = new ArrayList<>();
        for (Element dl : dlItems) {
            Course course = parseMoocCourseItem(dl);
            if (course != null) {
                courses.add(course);
            }
        }
        return Collections.unmodifiableList(courses);
    }

    private Course parseMoocCourseItem(Element dl) {
        // 从 h3 > a 的 title 属性获取课程名称
        Element titleLink = dl.selectFirst("dd h3 a[title]");
        if (titleLink == null) {
            titleLink = dl.selectFirst("dd h3 a");
        }
        if (titleLink == null) {
            return null;
        }

        String courseName = titleLink.attr("title");
        if (courseName == null || courseName.isBlank()) {
            courseName = titleLink.text();
        }
        if (courseName == null || courseName.isBlank()) {
            return null;
        }

        // 从链接提取课程 ID
        String href = titleLink.attr("href");
        String courseId = extractCourseIdFromUrl(href);
        if (courseId == null || courseId.isBlank()) {
            return null;
        }

        // 提取教师和学校（在 p 标签中）
        Elements paragraphs = dl.select("dd p");
        String teacherName = paragraphs.size() > 0 ? paragraphs.get(0).text() : "";
        String schoolName = paragraphs.size() > 1 ? paragraphs.get(1).text() : "";

        return new Course(courseId, courseName, teacherName, schoolName, href);
    }

    /**
     * 解析合成样本结构：div.course-item
     */
    private List<Course> parseSyntheticStructure(Document doc) {
        Elements courseItems = doc.select(".course-item");

        if (courseItems.isEmpty()) {
            return Collections.emptyList();
        }

        List<Course> courses = new ArrayList<>();
        for (Element item : courseItems) {
            Course course = parseSyntheticCourseItem(item);
            if (course != null) {
                courses.add(course);
            }
        }
        return Collections.unmodifiableList(courses);
    }

    private Course parseSyntheticCourseItem(Element item) {
        String courseId = item.attr("data-courseid");
        if (courseId == null || courseId.isBlank()) {
            return null;
        }

        String courseName = extractText(item, ".course-name");
        if (courseName == null || courseName.isBlank()) {
            return null;
        }

        String teacherName = extractText(item, ".course-teacher");
        String schoolName = extractText(item, ".course-school");
        String courseUrl = extractCourseUrl(item);

        return new Course(courseId, courseName, teacherName, schoolName, courseUrl);
    }

    /**
     * 从 URL 提取课程 ID
     * 格式：/mooc-ans/course/portal/{base64_id}
     */
    private String extractCourseIdFromUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        Matcher matcher = COURSE_ID_FROM_URL.matcher(url);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private String extractText(Element parent, String selector) {
        Element element = parent.selectFirst(selector);
        return element != null ? element.text() : null;
    }

    private String extractCourseUrl(Element item) {
        Element link = item.selectFirst("a.course-link, dd h3 a");
        return link != null ? link.attr("href") : null;
    }
}
