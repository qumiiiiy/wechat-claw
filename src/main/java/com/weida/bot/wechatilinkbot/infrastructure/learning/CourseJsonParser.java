package com.weida.bot.wechatilinkbot.infrastructure.learning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weida.bot.wechatilinkbot.model.Course;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 学习通课程列表 JSON 解析器。
 * 解析学习通 API 返回的 JSON 数据。
 */
@Component
public class CourseJsonParser {

    private static final Logger log = LoggerFactory.getLogger(CourseJsonParser.class);
    private final ObjectMapper objectMapper;

    public CourseJsonParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 解析课程列表 JSON，提取课程信息。
     *
     * @param json JSON 内容（API 响应）
     * @return 课程列表，解析失败返回空列表
     */
    public List<Course> parseCourseList(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }

        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode channelList = root.get("channelList");

            if (channelList == null || !channelList.isArray() || channelList.isEmpty()) {
                return Collections.emptyList();
            }

            List<Course> courses = new ArrayList<>();
            for (JsonNode channel : channelList) {
                Course course = parseChannelItem(channel);
                if (course != null) {
                    courses.add(course);
                }
            }
            return Collections.unmodifiableList(courses);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private Course parseChannelItem(JsonNode channel) {
        try {
            JsonNode content = channel.get("content");
            if (content == null) {
                return null;
            }

            JsonNode courseData = content.at("/course/data/0");
            if (courseData == null || courseData.isMissingNode()) {
                return null;
            }

            // 课程 ID
            JsonNode idNode = courseData.get("id");
            if (idNode == null || idNode.isNull()) {
                return null;
            }
            String courseId = textValue(idNode);
            if (courseId.isBlank()) {
                return null;
            }

            // 课程名称
            JsonNode nameNode = courseData.get("name");
            if (nameNode == null || nameNode.isNull() || nameNode.asText().isBlank()) {
                return null;
            }
            String courseName = nameNode.asText();

            // 教师
            JsonNode teacherNode = courseData.get("teacherfactor");
            String teacherName = (teacherNode != null && !teacherNode.isNull())
                    ? teacherNode.asText() : "";

            // 学校
            JsonNode schoolNode = courseData.get("schools");
            String schoolName = (schoolNode != null && !schoolNode.isNull())
                    ? schoolNode.asText() : "";

            // 课程封面图
            JsonNode imageNode = courseData.get("imageurl");
            String courseUrl = (imageNode != null && !imageNode.isNull())
                    ? imageNode.asText() : "";

            return new Course(courseId, courseName, teacherName, schoolName, courseUrl);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 解析课程详细信息（包含班级、日期等）。
     *
     * @param json JSON 内容
     * @return 课程详细信息列表
     */
    public List<CourseDetail> parseCourseDetails(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }

        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode channelList = root.get("channelList");

            if (channelList == null || !channelList.isArray() || channelList.isEmpty()) {
                return Collections.emptyList();
            }

            List<CourseDetail> details = new ArrayList<>();
            for (JsonNode channel : channelList) {
                CourseDetail detail = parseChannelDetail(channel);
                if (detail != null) {
                    details.add(detail);
                }
            }
            return Collections.unmodifiableList(details);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private CourseDetail parseChannelDetail(JsonNode channel) {
        try {
            JsonNode content = channel.get("content");
            if (content == null) {
                return null;
            }

            JsonNode courseData = content.at("/course/data/0");
            if (courseData == null || courseData.isMissingNode()) {
                return null;
            }

            // 班级 ID
            JsonNode keyNode = channel.get("key");
            String classId = textValue(keyNode);

            // 课程任务接口使用课程条目自己的 cpi，不能用登录账号 uid 代替。
            JsonNode cpiNode = channel.get("cpi");
            if (cpiNode == null || cpiNode.isNull() || cpiNode.asText().isBlank()) {
                cpiNode = content.get("cpi");
            }
            String cpi = textValue(cpiNode);

            JsonNode encNode = channel.get("enc");
            if (encNode == null || encNode.isNull() || textValue(encNode).isBlank()) {
                encNode = content.get("enc");
            }
            if (encNode == null || encNode.isNull() || textValue(encNode).isBlank()) {
                encNode = courseData.get("enc");
            }
            String enc = textValue(encNode);

            // 课程 ID
            JsonNode idNode = courseData.get("id");
            String courseId = textValue(idNode);
            if (enc.isBlank()) {
                List<String> channelKeys = new ArrayList<>();
                channel.fieldNames().forEachRemaining(channelKeys::add);
                List<String> contentKeys = new ArrayList<>();
                content.fieldNames().forEachRemaining(contentKeys::add);
                List<String> courseDataKeys = new ArrayList<>();
                courseData.fieldNames().forEachRemaining(courseDataKeys::add);
                log.debug("chaoxing_course_task_parameter_deferred courseId={} channelKeys={} "
                                + "contentKeys={} courseDataKeys={}",
                        courseId, channelKeys, contentKeys, courseDataKeys);
            }

            // 课程名称
            JsonNode nameNode = courseData.get("name");
            String courseName = (nameNode != null) ? nameNode.asText() : "";
            if (courseId.isBlank() || courseName.isBlank()) {
                return null;
            }

            // 班级名称
            JsonNode classNameNode = content.get("name");
            String className = (classNameNode != null) ? classNameNode.asText() : "";

            // 教师
            JsonNode teacherNode = courseData.get("teacherfactor");
            String teacher = (teacherNode != null) ? teacherNode.asText() : "";

            // 学校
            JsonNode schoolNode = courseData.get("schools");
            String school = (schoolNode != null) ? schoolNode.asText() : "";

            // 学生人数
            JsonNode studentCountNode = content.get("studentcount");
            int studentCount = (studentCountNode != null) ? studentCountNode.asInt() : 0;

            // 开始日期
            JsonNode beginDateNode = content.get("beginDate");
            String beginDate = (beginDateNode != null) ? beginDateNode.asText() : "";

            // 结束日期
            JsonNode endDateNode = content.get("endDate");
            String endDate = (endDateNode != null) ? endDateNode.asText() : "";

            // 状态
            JsonNode stateNode = content.get("state");
            int state = (stateNode != null) ? stateNode.asInt() : 0;

            // 封面图
            JsonNode imageNode = courseData.get("imageurl");
            String imageUrl = (imageNode != null) ? imageNode.asText() : "";

            return new CourseDetail(
                    classId,
                    courseId,
                    cpi,
                    courseName,
                    className,
                    teacher,
                    school,
                    studentCount,
                    beginDate,
                    endDate,
                    state,
                    imageUrl,
                    enc
            );
        } catch (Exception e) {
            return null;
        }
    }

    private static String textValue(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return "";
        }
        return node.asText("").strip();
    }

    /**
     * 课程详细信息（包含班级、日期等）。
     */
    public record CourseDetail(
            String classId,
            String courseId,
            String cpi,
            String courseName,
            String className,
            String teacher,
            String school,
            int studentCount,
            String beginDate,
            String endDate,
            int state,
            String imageUrl,
            String enc
    ) {
        public CourseDetail(
                String classId,
                String courseId,
                String cpi,
                String courseName,
                String className,
                String teacher,
                String school,
                int studentCount,
                String beginDate,
                String endDate,
                int state,
                String imageUrl) {
            this(classId, courseId, cpi, courseName, className, teacher, school,
                    studentCount, beginDate, endDate, state, imageUrl, "");
        }

        public CourseDetail {
            classId = classId != null ? classId : "";
            courseId = courseId != null ? courseId : "";
            cpi = cpi != null ? cpi : "";
            courseName = courseName != null ? courseName : "";
            className = className != null ? className : "";
            teacher = teacher != null ? teacher : "";
            school = school != null ? school : "";
            beginDate = beginDate != null ? beginDate : "";
            endDate = endDate != null ? endDate : "";
            imageUrl = imageUrl != null ? imageUrl : "";
            enc = enc != null ? enc : "";
        }
    }
}
