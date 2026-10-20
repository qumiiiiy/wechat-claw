package com.weida.bot.wechatilinkbot.agent.intent;

import com.weida.bot.wechatilinkbot.model.ConversationMessage;
import java.text.Normalizer;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 学习通课程查询意图检测器。
 * 检测用户是否想要查询课程、作业或任务。
 */
@Component
public class LearningIntentDetector {

    private static final Pattern COURSE_PATTERN = Pattern.compile(
            "(?:课程|课表|上课|选课|我的课|(?:查|查看|查询).{0,12}(?:课程|课表|课)|有什么课)");

    private static final Pattern TASK_PATTERN = Pattern.compile(
            "(?:作业|任务|未完成|没做|没交|截止|deadline)");

    private static final Pattern LEARNING_TASK_LIST_PATTERN = Pattern.compile(
            "(?:查看|查|查询|列出|列举|获取|看看)\\s*"
                    + "(?:所有|全部)?\\s*(?:未完成|没做|没交)?\\s*(?:的\\s*)?"
                    + "(?:课程\\s*)?任务(?:列表)?");

    private static final Pattern DETAIL_PATTERN = Pattern.compile(
            "(?:课程详情|详细信息|班级|任课教师|学生人数|第[一二三四五六七八九十0-9]+[门个]?)");

    private static final Pattern LEARNING_PATTERN = Pattern.compile(
            "(?:学习通|超星|chaoxing|xuexitong)");

    private static final Pattern COURSE_REFERENCE_PATTERN = Pattern.compile(
            "(?<![A-Za-z0-9])C[1-9][0-9]{0,2}(?![A-Za-z0-9])",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern OCS_STUDY_PATTERN = Pattern.compile(
            "(?:ocs|网课|刷课|看课|听课|课程视频|视频章节|视频任务|播放.{0,8}课|学习.{0,8}课程)");

    private static final Pattern OCS_REFERENCE_PATTERN = Pattern.compile(
            "(?:ocs|网课|学习会话|OS-[0-9a-f]{8}-[0-9a-f-]{27,36})",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern OCS_STATUS_PATTERN = Pattern.compile(
            "(?:进度|状态|笔记|记录|到哪|怎么样|情况)");

    private static final Pattern OCS_START_PATTERN = Pattern.compile(
            "(?:(?:开始|继续|帮我|用).{0,12}(?:ocs|网课|刷课|看课|听课)"
                    + "|(?:开始|继续|帮我|用|重温|播放|刷).{0,12}(?:课程|视频|课)"
                    + "|(?:看|听|学习).{0,8}(?:网课|课程视频|视频任务)"
                    + "|(?:ocs|网课|课程视频|视频章节|视频任务).{0,12}"
                    + "(?:开始|继续|播放|看|听|刷|学习|记录|重温)"
                    + "|(?:查看|查找|列出|有哪些|有什么).{0,12}"
                    + "(?:课程视频|视频章节|视频任务))");

    private static final Pattern READ_ONLY_COURSE_PATTERN = Pattern.compile(
            "(?:查(?:询|看)?课程|查看课程|课程列表|课程详情)");

    private static final Pattern LEARNING_CONTINUATION_PATTERN = Pattern.compile(
            "(?:第[一二三四五六七八九十0-9]+[门个]|这门课|这个课程|上一门|下一门|"
                    + "详情|详细|任务|作业|未完成|继续|下一步|开始|重温|播放)");

    private static final Pattern OCS_CONTINUATION_PATTERN = Pattern.compile(
            "^(?=.{1,24}$).*(?:开始|继续|播放|重温|接着看|继续看|开始看|继续学习|开始学习)"
                    + ".*[吧。！! ]*$");

    private static final Pattern NATURAL_COURSE_START_PATTERN = Pattern.compile(
            "(?:第[一二三四五六七八九十百0-9]+(?:门|个)?(?:课|课程)?|这门(?:课|课程)?|"
                    + "这个(?:课|课程)?|上一门|下一门).{0,8}(?:开始|继续|播放|看|听|学习|重温|刷)"
                    + "|(?:开始|继续|播放|看|听|学习|重温|刷).{0,8}(?:第[一二三四五六七八九十百0-9]+"
                    + "(?:门|个)?(?:课|课程)?|这门(?:课|课程)?|这个(?:课|课程)?|上一门|下一门)");

    private static final Pattern LEARNING_CONTEXT_PATTERN = Pattern.compile(
            "(?:学习通|超星|课程列表|课程详情|课程引用|登录二维码|查课程|作业|任务)");

    private static final Pattern OCS_CONTEXT_PATTERN = Pattern.compile(
            "(?:ocs|学习会话|网课|刷课|看课|听课|课程视频|视频章节|视频任务)",
            Pattern.CASE_INSENSITIVE);

    /**
     * 检测是否包含课程查询意图。
     *
     * @param text 用户文本
     * @return 是否有课程查询意图
     */
    public boolean hasCourseQuery(String text) {
        String normalized = normalize(text);
        return COURSE_PATTERN.matcher(normalized).find()
                || LEARNING_PATTERN.matcher(normalized).find();
    }

    /**
     * 检测是否包含任务查询意图。
     *
     * @param text 用户文本
     * @return 是否有任务查询意图
     */
    public boolean hasTaskQuery(String text) {
        String normalized = normalize(text);
        return TASK_PATTERN.matcher(normalized).find();
    }

    /**
     * 检测是否包含学习通相关意图。
     *
     * @param text 用户文本
     * @return 是否有学习通相关意图
     */
    public boolean hasLearningQuery(String text) {
        String normalized = normalize(text);
        boolean platformMentioned = LEARNING_PATTERN.matcher(normalized).find();
        boolean courseQuery = COURSE_PATTERN.matcher(normalized).find();
        boolean detailQuery = DETAIL_PATTERN.matcher(normalized).find();
        boolean courseReference = COURSE_REFERENCE_PATTERN.matcher(normalized).find();
        boolean ocsStudyRequested = OCS_STUDY_PATTERN.matcher(normalized).find();
        boolean learningTaskListQuery = LEARNING_TASK_LIST_PATTERN.matcher(normalized).find();
        return platformMentioned || courseQuery || detailQuery || courseReference
                || ocsStudyRequested || learningTaskListQuery;
    }

    /** Recognizes a short course follow-up only when recent history is learning-related. */
    public boolean hasContinuationIntent(
            List<ConversationMessage> history,
            String currentText) {
        String normalized = normalize(currentText);
        return LEARNING_CONTINUATION_PATTERN.matcher(normalized).find()
                && latestUserMessageMatches(history, LEARNING_CONTEXT_PATTERN);
    }

    public OcsStudyOperation detectOcsOperation(String text) {
        return detectOcsOperation(text, false);
    }

    public OcsStudyOperation detectOcsOperation(String text, boolean allowNaturalReference) {
        String normalized = normalize(text);
        if (READ_ONLY_COURSE_PATTERN.matcher(normalized).find()) {
            return OcsStudyOperation.NONE;
        }
        if (OCS_REFERENCE_PATTERN.matcher(normalized).find()
                && OCS_STATUS_PATTERN.matcher(normalized).find()) {
            return OcsStudyOperation.STATUS;
        }
        if (allowNaturalReference && OCS_STATUS_PATTERN.matcher(normalized).find()) {
            return OcsStudyOperation.STATUS;
        }
        if (OCS_START_PATTERN.matcher(normalized).find()) {
            return OcsStudyOperation.START;
        }
        return OcsStudyOperation.NONE;
    }

    /** Recognizes a course ordinal followed by an explicit OCS start action. */
    public boolean hasNaturalCourseStartIntent(String text) {
        return NATURAL_COURSE_START_PATTERN.matcher(normalize(text)).find();
    }

    /** Recognizes short OCS continuations such as “继续” after an explicit OCS turn. */
    public OcsStudyOperation detectOcsOperation(
            List<ConversationMessage> history,
            String text) {
        OcsStudyOperation explicit = detectOcsOperation(text);
        if (explicit != OcsStudyOperation.NONE) {
            return explicit;
        }
        String normalized = normalize(text);
        return OCS_CONTINUATION_PATTERN.matcher(normalized).matches()
                && latestUserMessageMatches(history, OCS_CONTEXT_PATTERN)
                ? OcsStudyOperation.START
                : OcsStudyOperation.NONE;
    }

    private static boolean latestUserMessageMatches(
            List<ConversationMessage> history,
            Pattern contextPattern) {
        if (history == null || history.isEmpty()) {
            return false;
        }
        for (int index = history.size() - 1; index >= 0; index--) {
            ConversationMessage message = history.get(index);
            if (message == null || !"user".equals(message.role())) {
                continue;
            }
            return contextPattern.matcher(normalize(message.content())).find();
        }
        return false;
    }

    private static String normalize(String text) {
        return text == null
                ? ""
                : Normalizer.normalize(text.strip(), Normalizer.Form.NFKC);
    }
}
