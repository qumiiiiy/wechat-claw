package com.weida.bot.wechatilinkbot.tool.input;

import com.weida.bot.wechatilinkbot.model.TodoFilter;
import com.weida.bot.wechatilinkbot.tool.ToolParameter;

/** Input for querying student todos by dimension. */
public record TodoQueryInput(
        @ToolParameter(description = "查询维度：ALL（全部）/ TODAY（今天）/ TOMORROW（明天）/ "
                + "WEEK（本周）/ NEXT_WEEK（下周）/ MONTH（本月）/ OVERDUE（逾期）/ "
                + "COURSE_ONLY（学习通）/ EMAIL_ONLY（邮箱）")
        TodoFilter filter,

        @ToolParameter(description = "起始时间（含），例如 2026-07-01、2026-07、7月1日；"
                + "与 to 一起过滤截止时间范围", required = false)
        String from,

        @ToolParameter(description = "结束时间（不含），例如 2026-08-01；与 from 一起过滤截止时间范围",
                required = false)
        String to,

        @ToolParameter(description = "来源过滤：ALL / PERSONAL / EMAIL / COURSE；"
                + "不填时按 filter 决定", required = false)
        String source) {

    public TodoQueryInput(TodoFilter filter) {
        this(filter, null, null, null);
    }

    public TodoQueryInput {
        from = from != null ? from.strip() : "";
        to = to != null ? to.strip() : "";
        source = source != null ? source.strip() : "";
    }
}
