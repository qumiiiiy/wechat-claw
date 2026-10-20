package com.weida.bot.wechatilinkbot.agent.intent;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Detects only explicit todo-related requests; generic chat never exposes todo tools. */
@Component
public class TodoIntentDetector {

    private static final Pattern TODO_ID_PATTERN = Pattern.compile("td-[0-9a-f]{12}");

    private static final Pattern LIST_PATTERN = Pattern.compile(
            "查待办|待办列表|我的待办|还有哪些(?:作业|任务|待办)|有什么(?:作业|任务|待办)|"
                    + "查看待办|查看作业|^全部$|^所有$|^待办$");

    private static final Pattern TODAY_PATTERN = Pattern.compile(
            "今天截止|今日截止|今天到期|今日到期|今天(?:有)?什么|今天(?:要|需要)交"
                    + "|查询今日待办|今天待办|今天的待办|今日待办");

    private static final Pattern ADD_PATTERN = Pattern.compile(
            "提醒我|帮我记(?:一下|住)|记住|别忘了|不要忘了|备忘");

    private static final Pattern SUMMARY_PATTERN = Pattern.compile(
            "今日要务|每日总结|今天(?:要|需要)做(?:什么|啥)|总结待办|汇总待办|待办汇总");

    private static final Pattern QUERY_PATTERN = Pattern.compile(
            "明天截止|明天到期|明天(?:有)?什么|明日截止|明日到期|查询明日"
                    + "|本周截止|本周到期|本周(?:有)?什么|查询本周"
                    + "|查询明日|查询本月|查询下周|查询下周"
                    + "|逾期|已过期|过期(?:的)?待办|全部待办|所有待办"
                    + "|查询全部|查询所有|查询待办");

    private static final Pattern TIME_FILTER_PATTERN = Pattern.compile(
            "(?:过滤|筛选|只查|只留|只要|排除|去掉|忽略|不查|不看).{0,12}"
                    + "(?:\\d{4}年?\\d{1,2}月|\\d{1,2}月\\d{1,2}日?|\\d{4}-\\d{2}(?:-\\d{2})?|"
                    + "前|后|之前|以后)"
                    + "|(?:\\d{4}年?\\d{1,2}月|\\d{1,2}月\\d{1,2}日?|\\d{4}-\\d{2}(?:-\\d{2})?)"
                    + ".{0,12}(?:前|后|之前|以后).{0,8}(?:任务|作业|待办|未完成)");

    private static final Pattern COMPLETE_PATTERN = Pattern.compile(
            "(?:完成|做完了|搞定了|交了|标记完成|已完成)\\s*(?:待办)?\\s*(?:td-[0-9a-f]{12}"
                    + "|第[一二三四五六七八九十0-9]+(?:项|个)?|刚才(?:那个|的待办)?|这个待办)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern POSTPONE_PATTERN = Pattern.compile(
            "推迟|延后|延期|提前|往前调|往后推|改期|改到|改成|换个时间|修改.{0,5}时间"
                    + "|改\\s*(?:第[一二三四五六七八九十0-9]+|[一二三四五六七八九十两]+|[0-9]+)\\s*[个项]?",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern CANCEL_PATTERN = Pattern.compile(
            "(?:取消|撤销|不要了|删掉|删除)\\s*(?:待办)?\\s*td-[0-9a-f]{12}"
                    + "|(?:取消|撤销|不要了|删掉|删除).{0,20}(?:待办|任务|提醒)"
                    + "|(?:取消|撤销|不要了|删掉|删除)\\s*(?:第[一二三四五六七八九十0-9]+"
                    + "(?:项|个)?|刚才(?:那个|的待办)?|这个待办)",
            Pattern.CASE_INSENSITIVE);

    public TodoOperation detect(String text) {
        String normalized = normalize(text);
        if (normalized.isBlank()) {
            return TodoOperation.NONE;
        }
        String[] lines = normalized.split("\\R+");
        TodoOperation detected = TodoOperation.NONE;
        int operationCount = 0;
        for (String line : lines) {
            TodoOperation lineOperation = detectSingle(line);
            if (lineOperation != TodoOperation.NONE) {
                operationCount++;
                detected = lineOperation;
            }
        }
        return operationCount > 1 ? TodoOperation.MULTIPLE : detected;
    }

    private TodoOperation detectSingle(String normalized) {
        if (COMPLETE_PATTERN.matcher(normalized).find()) {
            return TodoOperation.COMPLETE;
        }
        if (CANCEL_PATTERN.matcher(normalized).find()) {
            return TodoOperation.CANCEL;
        }
        if (POSTPONE_PATTERN.matcher(normalized).find()) {
            return TodoOperation.POSTPONE;
        }
        if (SUMMARY_PATTERN.matcher(normalized).find()) {
            return TodoOperation.SUMMARY;
        }
        if (TODAY_PATTERN.matcher(normalized).find()) {
            return TodoOperation.TODAY;
        }
        if (QUERY_PATTERN.matcher(normalized).find()) {
            return TodoOperation.QUERY;
        }
        if (LIST_PATTERN.matcher(normalized).find()) {
            return TodoOperation.LIST;
        }
        if (ADD_PATTERN.matcher(normalized).find()) {
            return TodoOperation.ADD;
        }
        if (TIME_FILTER_PATTERN.matcher(normalized).find()) {
            return TodoOperation.QUERY;
        }
        return TodoOperation.NONE;
    }

    public boolean hasTodoId(String text) {
        return TODO_ID_PATTERN.matcher(normalize(text)).find();
    }

    public boolean hasTimeFilter(String text) {
        return TIME_FILTER_PATTERN.matcher(normalize(text)).find();
    }

    private static String normalize(String text) {
        return text == null
                ? ""
                : Normalizer.normalize(text.strip(), Normalizer.Form.NFKC)
                        .toLowerCase(Locale.ROOT);
    }
}
