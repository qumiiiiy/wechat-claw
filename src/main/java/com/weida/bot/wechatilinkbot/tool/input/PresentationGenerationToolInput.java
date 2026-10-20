package com.weida.bot.wechatilinkbot.tool.input;

import com.weida.bot.wechatilinkbot.tool.ToolParameter;

/** Bounded, whole-deck presentation input; the server owns the output file name and path. */
public record PresentationGenerationToolInput(
        @ToolParameter(description = "演示文稿标题")
        String title,

        @ToolParameter(
                description = "幻灯片 JSON 数组；支持 title、bullets、table、ending 四种版式，图文页需使用已批准媒体",
                required = false)
        String slidesJson) {

    public PresentationGenerationToolInput {
        title = title == null ? "" : title.strip();
        slidesJson = slidesJson == null ? "" : slidesJson.strip();
    }
}
