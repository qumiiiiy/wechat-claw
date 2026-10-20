package com.weida.bot.wechatilinkbot.agent;

import com.weida.bot.wechatilinkbot.capability.EmotionalProfileService;
import com.weida.bot.wechatilinkbot.model.AgentRequest;
import com.weida.bot.wechatilinkbot.model.ConversationMessage;
import com.weida.bot.wechatilinkbot.model.MediaPart;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Builds bounded provider-neutral messages without channel or SDK knowledge.
 */
@Component
public class AgentPromptAssembler {

    private static final DateTimeFormatter CURRENT_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");
    private static final Pattern INTERNAL_MEDIA_SUMMARY = Pattern.compile(
            "(?m)\\R?\\[本轮(?:已发送|包含)[^]\\r\\n]*]");

    private final AgentProperties properties;
    private final EmotionalProfileService emotionalProfileService;
    private final Clock clock;

    @Autowired
    public AgentPromptAssembler(
            AgentProperties properties,
            EmotionalProfileService emotionalProfileService) {
        this(properties, emotionalProfileService, Clock.systemDefaultZone());
    }

    AgentPromptAssembler(
            AgentProperties properties,
            EmotionalProfileService emotionalProfileService,
            Clock clock) {
        this.properties = properties;
        this.emotionalProfileService = emotionalProfileService;
        this.clock = clock;
    }

    public List<ModelMessage> assemble(
            List<ConversationMessage> history,
            AgentRequest request,
            List<MediaPart> mediaParts,
            boolean speechRequested) {
        List<ModelMessage> messages = new ArrayList<>(history.size() + 3);
        messages.add(ModelMessage.system(systemPrompt(request.conversationId())));
        if (speechRequested) {
            messages.add(ModelMessage.system(speechInstruction()));
        }
        for (ConversationMessage turn : history) {
            if (turn == null || turn.content() == null || turn.content().isBlank()) {
                continue;
            }
            if ("user".equals(turn.role())) {
                messages.add(ModelMessage.user(sanitize(turn.content()), List.of()));
            } else if ("assistant".equals(turn.role())) {
                messages.add(ModelMessage.assistant(sanitize(turn.content())));
            } else if (ConversationContextCompactor.SUMMARY_ROLE.equals(turn.role())) {
                messages.add(ModelMessage.user(
                        ConversationContextCompactor.SUMMARY_MARKER + turn.content(),
                        List.of()));
            } else if (ConversationContextCompactor.HISTORY_ROLE.equals(turn.role())) {
                messages.add(ModelMessage.user(
                        ConversationContextCompactor.HISTORY_MARKER + turn.content(),
                        List.of()));
            }
        }
        messages.add(ModelMessage.user(request.prompt(), mediaParts));
        return List.copyOf(messages);
    }

    public String historyPrompt(AgentRequest request) {
        int mediaCount = request.mediaParts().size();
        return request.prompt()
                + (mediaCount == 0
                        ? ""
                        : "\n用户在本轮附带了 " + mediaCount + " 个视觉附件。");
    }

    public String sanitize(String content) {
        return INTERNAL_MEDIA_SUMMARY.matcher(content == null ? "" : content)
                .replaceAll("")
                .strip();
    }

    private String systemPrompt(String conversationId) {
        StringBuilder sb = new StringBuilder();
        String profile = emotionalProfileService.getProfile(conversationId);
        if (!profile.isEmpty()) {
            sb.append("[用户长期画像]\n").append(profile).append("\n\n");
        }
        sb.append(properties.getSystemPrompt().strip());
        sb.append("\n\n【电脑任务规则】只有系统在本轮提供电脑任务工具时才可调用。"
                + "打开网址必须先创建待确认任务；不得把用户确认文字当成执行成功。"
                + "确认、查询和取消必须使用此前工具返回的任务 ID。"
                + "不得声称已打开浏览器，除非确认工具返回真实成功结果。");
        sb.append("\n\n【文档生成工具规则】用户明确要求 Word 或 PDF 文件时才调用 generate_document。"
                + "工具参数必须是单个合法 JSON 对象，不要输出 Markdown 代码块或额外说明文字。"
                + "长正文优先放在 content 字段；content 与 sectionsJson 互斥；换行写成 \\n。"
                + "合法示例：{\"fileName\":\"报告.docx\",\"title\":\"标题\","
                + "\"content\":\"第一段\\n第二段\"}。"
                + "工具返回参数错误时，先按错误信息修正 JSON 再重试，不得声称文件已生成。");
        sb.append("\n\n【本机文件搜索规则】browse_files 的语义理解由你完成：把用户自然语言翻译成"
                + "结构化参数（nameFragments 文件名关键词、fileTypes 文件类型、directoryHint 目录、"
                + "sortCriteria 排序、directoriesOnly 是否只列目录、recentFileReference 是否指代"
                + "最近收到的文件）。nameFragments 最多 5 个，可给出同义或相关词"
                + "（例如“课表”和“课程表”），不要包含路径分隔符；用户说“这个文件”“刚才的文件”"
                + "且最近消息中有明确文件名时，用完整文件名（含扩展名）作为 nameFragments 并设置"
                + "recentFileReference=true；没有明确文件名时先向用户询问文件名或关键词。");
        sb.append("\n\n【待办提醒规则】当用户表达设置提醒、记录待办、查询待办、"
                + "查看截止或完成任务的意图时，必须使用系统提供的待办工具（add_todo / "
                + "list_todos / today_todos / complete_todo），不得用普通文字回复代替。"
                + "具体规则：\n"
                + "- 用户说「提醒我」「帮我记一下」「记住」「别忘了」→ 调用 add_todo\n"
                 + "- 用户说「查待办」「我的待办」「还有什么」「有哪些作业」→ 调用 list_todos\n"
                 + "- 用户说「今天截止」「今日到期」「今天有什么」→ 调用 today_todos\n"
                 + "- 用户说「完成了」「做完了」「交了」「搞定」并给出待办ID → 调用 complete_todo\n"
                 + "- 用户说「取消」「撤销」「不要了」「删掉」并给出待办ID → 调用 cancel_todo\n"
                 + "- 用户说「明天截止」「本周」「逾期」「全部」→ 调用 query_todos\n"
                 + "- 用户没有明确时间范围时，list_todos / query_todos 默认只返回一个月内（含逾期）；"
                 + "明确说「全部待办」时 filter 传 ALL\n"
                 + "- 学习通任务已合并进待办；用户说「过滤/只查/排除某日期前的学习通任务」"
                 + "「7月后的作业」→ 调用 query_todos，filter=COURSE_ONLY，"
                 + "from/to 填日期（过滤7月前用 from=2026-07-01；只留7月前用 to=2026-07-01），"
                 + "不得用普通文字回复代替\n"
                 + "- 用户说「今日要务」「每日总结」「今天要做什么」→ 调用 daily_summary\n"
                + "- add_todo 的 title 只提取任务内容，去除「提醒我」「帮我记一下」等前缀。"
                + "例如「提醒我17:00拿外卖」→ title=\"拿外卖\" dueTimeStr=\"17:00\"；"
                + "「帮我记一下明天10:00开会」→ title=\"开会\" dueTimeStr=\"明天10:00\"\n"
                + "\n【待办时间强制规则】调用 add_todo 前必须先确认用户给出了明确的日期+时刻。"
                + "「下班后」「有空的时候」「尽快」「晚点」「周末」「过两天」「等一会儿」"
                + "等模糊表述不是有效时间，必须反问用户具体什么日期几点，"
                + "例如「好的，具体什么时间提醒你？比如明天18:00」。"
                + "严禁自行猜测、补全或默认为某个时刻。");
        sb.append("\n当前服务端时间：");
        sb.append(ZonedDateTime.now(clock).format(CURRENT_TIME_FORMAT));
        if (emotionalProfileService.isEnabled()) {
            sb.append("\n\n【强制规则】每条回复的最后一整行必须是情绪标记，格式：\n"
                    + "[EMOTION: 情绪词] [MOOD: low|neutral|positive] [CONTEXT: 话题摘要]\n"
                    + "情绪词从以下选择：开心、难过、焦虑、生气、疲惫、兴奋、平静、中性\n"
                    + "MOOD 必须从 low、neutral、positive 三选一：明显负面情绪（难过、焦虑、生气、"
                    + "疲惫、孤独、委屈、压力、烦躁、抑郁、无助、迷茫等）用 low；"
                    + "开心、兴奋、满意等积极情绪用 positive；其余用 neutral。\n"
                    + "话题摘要用一句话概括用户本轮提到的关键信息，没有则填 无\n"
                    + "示例：[EMOTION: 疲惫] [MOOD: low] [CONTEXT: 加班改了一整天代码]\n"
                    + "该行发送前会被系统自动移除，你直接输出，不要加引号、说明或代码块。");
        }
        return sb.toString();
    }

    private static String speechInstruction() {
        return "本轮用户希望收到语音。先正常完成问题求解和必要查询，"
                + "最终只输出适合直接朗读的正文；不要说明将要或已经生成、转换或发送语音。";
    }
}
