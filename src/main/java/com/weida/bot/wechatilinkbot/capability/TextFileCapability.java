package com.weida.bot.wechatilinkbot.capability;

import com.weida.bot.wechatilinkbot.model.GeneratedMedia;
import com.weida.bot.wechatilinkbot.model.GeneratedMediaKind;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Creates bounded UTF-8 files entirely in memory.
 */
@Component
public class TextFileCapability {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "adoc", "bat", "c", "cfg", "cmake", "cmd", "conf", "cpp", "css",
            "csv", "dockerfile", "env", "fish", "gitignore", "go", "gradle",
            "graphql", "h", "html", "ini", "java", "js", "json", "json5",
            "jsonl", "jsx", "less", "log", "lua", "makefile", "md", "properties",
            "proto", "ps1", "py", "r", "rst", "rs", "sass", "scss", "sh",
            "sql", "svelte", "tex", "toml", "ts", "tsv", "tsx", "txt", "vue",
            "xml", "yaml", "yml", "zsh");
    private static final Pattern SAFE_FILE_NAME =
            Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}._-]*");
    private static final Pattern WINDOWS_DEVICE_NAME = Pattern.compile(
            "(?i)(?:CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?");

    private final TextFileProperties properties;

    public TextFileCapability(TextFileProperties properties) {
        this.properties = properties;
    }

    public boolean available() {
        return properties.isEnabled();
    }

    public GeneratedMedia create(String rawFileName, String content) {
        String fileName = validateFileName(rawFileName);
        byte[] contentBytes = validateContent(content);
        return new GeneratedMedia(
                GeneratedMediaKind.FILE,
                contentBytes,
                fileName,
                0L,
                "");
    }

    private String validateFileName(String rawFileName) {
        if (rawFileName == null) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        String fileName = Normalizer.normalize(rawFileName.strip(), Normalizer.Form.NFKC);
        if (fileName.isBlank() || fileName.length() > properties.getMaxFileNameCharacters()) {
            throw new IllegalArgumentException("文件名为空或超过长度限制");
        }
        if (!SAFE_FILE_NAME.matcher(fileName).matches()
                || fileName.contains("..")
                || WINDOWS_DEVICE_NAME.matcher(fileName).matches()) {
            throw new IllegalArgumentException("文件名不安全，不能包含路径、控制字符或保留名称");
        }
        int extensionIndex = fileName.lastIndexOf('.');
        if (extensionIndex <= 0 || extensionIndex == fileName.length() - 1) {
            throw new IllegalArgumentException("文件名必须包含受支持的扩展名");
        }
        String extension = fileName.substring(extensionIndex + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("不支持的文件扩展名：" + extension);
        }
        return fileName;
    }

    private byte[] validateContent(String content) {
        if (content == null
                || content.isEmpty()
                || content.length() > properties.getMaxContentCharacters()) {
            throw new IllegalArgumentException("文件内容为空或超过字符数限制");
        }
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
        if (contentBytes.length > properties.getMaxContentBytes()) {
            throw new IllegalArgumentException("文件内容超过字节数限制");
        }
        return contentBytes;
    }
}
