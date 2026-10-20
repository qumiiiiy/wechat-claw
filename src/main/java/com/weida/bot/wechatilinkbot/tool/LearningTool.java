package com.weida.bot.wechatilinkbot.tool;

import static com.weida.bot.wechatilinkbot.tool.ToolNames.LEARNING_QUERY;

import com.weida.bot.wechatilinkbot.capability.LearningCapability;
import com.weida.bot.wechatilinkbot.capability.LearningLoginResult;
import com.weida.bot.wechatilinkbot.tool.input.LearningToolInput;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 学习通课程查询工具。
 * 支持登录、课程列表、课程详情和已知课程的任务列表。
 */
@Order(80)
@Component
public class LearningTool implements AgentTool<LearningToolInput> {

    private final LearningCapability learningCapability;

    public LearningTool(LearningCapability learningCapability) {
        this.learningCapability = learningCapability;
    }

    @Override
    public String name() {
        return LEARNING_QUERY;
    }

    @Override
    public String description() {
        return "查询学习通课程信息和任务，或登录学习通。"
                + "支持以下操作：\n"
                + "- login：生成学习通 App 扫码登录二维码并作为微信图片返回\n"
                + "- courses：获取带 C1、C2 引用的课程列表\n"
                + "- details：获取课程详情，可传 courseName 或 courseReference\n"
                + "- tasks：获取指定课程任务；优先使用 courseName 或 C1 形式的 courseReference，"
                + "也兼容 courseId/classId\n"
                + "- tasks_by_name：根据课程名称获取任务列表，支持包含匹配\n"
                + "- pending：汇总所有课程的未完成或状态未知任务\n"
                + "用户要求登录、换号、重新登录学习通时使用 login。"
                + "不要编造课程和任务信息，查询时必须以工具返回的真实数据为准。";
    }

    @Override
    public Class<LearningToolInput> inputType() {
        return LearningToolInput.class;
    }

    @Override
    public boolean available() {
        return learningCapability.isEnabled();
    }

    @Override
    public boolean requiresExplicitIntent() {
        return true;
    }

    @Override
    public boolean producesMedia() {
        return true;
    }

    @Override
    public boolean returnDirect() {
        return true;
    }

    @Override
    public ToolExecution execute(LearningToolInput input, ToolContext context) {
        String action = input.action();
        String result;

        switch (action) {
            case "login":
                LearningLoginResult loginResult = learningCapability.login();
                return new ToolExecution(loginResult.message(), loginResult.qrCode());
            case "courses":
                result = learningCapability.getCourseList(context.conversationId());
                break;
            case "details":
                result = learningCapability.getCourseDetails(
                        context.conversationId(),
                        courseSelector(input));
                break;
            case "tasks":
                if (!courseSelector(input).isBlank()) {
                    result = learningCapability.getTaskList(courseSelector(input));
                } else if (input.courseId().isEmpty() || input.classId().isEmpty()) {
                    result = "请提供课程名称、C1 形式的课程引用，或 courseId/classId。";
                } else {
                    result = learningCapability.getTaskList(
                            input.courseId(), input.classId(), input.enc());
                }
                break;
            case "tasks_by_name":
                if (input.courseName().isBlank()) {
                    result = "根据课程名称查询任务需要提供 courseName。";
                } else {
                    result = learningCapability.getTaskList(input.courseName());
                }
                break;
            case "pending":
                result = learningCapability.checkAllPendingTasks();
                break;
            default:
                result = "未知操作：" + action
                        + "。支持的操作：login、courses、details、tasks、tasks_by_name、pending";
                break;
        }

        return new ToolExecution(result, null);
    }

    @Override
    public void clearConversation(String conversationId) {
        learningCapability.clearConversation(conversationId);
    }

    private static String courseSelector(LearningToolInput input) {
        return input.courseReference().isBlank()
                ? input.courseName()
                : input.courseReference();
    }
}
