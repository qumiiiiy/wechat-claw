package com.weida.bot.wechatilinkbot.tool;

import static com.weida.bot.wechatilinkbot.tool.ToolNames.GENERATE_TEXT_FILE;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.weida.bot.wechatilinkbot.capability.TextFileCapability;
import com.weida.bot.wechatilinkbot.model.GeneratedMedia;
import com.weida.bot.wechatilinkbot.tool.input.TextFileGenerationToolInput;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Creates a bounded in-memory text file for channel delivery.
 */
@Order(40)
@Component
public class TextFileGenerationTool implements AgentTool<TextFileGenerationToolInput> {

    private final TextFileCapability textFileCapability;
    private final ObjectMapper objectMapper;

    public TextFileGenerationTool(
            TextFileCapability textFileCapability,
            ObjectMapper objectMapper) {
        this.textFileCapability = textFileCapability;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return GENERATE_TEXT_FILE;
    }

    @Override
    public String description() {
        return "仅当用户明确要求生成可下载的文件时调用。"
                + "支持的类型包括：文本(txt)、Markdown(md)、代码(java/py/js/ts/go/rs/c/cpp/h/lua/r/sh)、"
                + "前端(jsx/tsx/vue/svelte/css/scss/less/html)、"
                + "配置(json/xml/yaml/yml/toml/ini/conf/env/properties)、"
                + "数据(csv/tsv/jsonl/graphql/sql)、"
                + "文档(rst/tex/adoc)、脚本(bat/cmd/ps1/fish/zsh)等。"
                + "内容必须完整；不支持二进制 Office 文件，也不能读写服务端路径。";
    }

    @Override
    public Class<TextFileGenerationToolInput> inputType() {
        return TextFileGenerationToolInput.class;
    }

    @Override
    public boolean available() {
        return textFileCapability.available();
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
    public ToolExecution execute(
            TextFileGenerationToolInput input,
            ToolContext context) {
        GeneratedMedia media = textFileCapability.create(input.fileName(), input.content());
        ObjectNode result = objectMapper.createObjectNode();
        result.put("status", "ok");
        result.put("media_type", "file");
        result.put("file_name", media.fileName());
        result.put("bytes", media.bytes().length);
        result.put("message", "文件已生成，将作为微信文件发送");
        return new ToolExecution(result.toString(), media);
    }
}
