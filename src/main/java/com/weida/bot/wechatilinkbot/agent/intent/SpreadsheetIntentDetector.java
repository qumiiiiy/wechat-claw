package com.weida.bot.wechatilinkbot.agent.intent;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Detects explicit requests to create a downloadable Excel workbook.
 */
@Component
public class SpreadsheetIntentDetector {

    private static final Pattern FORMAT_PATTERN = Pattern.compile(
            "(?:excel|\\.xlsx\\b|xlsx\\b|spreadsheet\\b|workbook\\b|电子表格|工作簿)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CREATION_PATTERN = Pattern.compile(
            "(?:生成|创建|制作|做(?:一份|一个)|整理成|导出(?:成|为)?|"
                    + "转换成|转成|保存为|写成|放到|放入|存入|汇总成|输出为|弄成)"
                    + "|\\b(?:create|generate|make|export|build)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern INFORMATION_QUERY_PATTERN = Pattern.compile(
            "(?:如何|怎么|怎样|什么是|教程|用法|方法|是否支持|能否)"
                    + ".{0,30}(?:excel|xlsx|spreadsheet|workbook|电子表格|工作簿)"
                    + "|\\b(?:how|what|why)\\b.{0,40}"
                    + "(?:excel|xlsx|spreadsheet|workbook)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern NEGATION_PATTERN = Pattern.compile(
            "(?:不要|不用|无需|别).{0,12}"
                    + "(?:生成|创建|制作|导出|excel|xlsx|电子表格|工作簿)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern READ_PATTERN = Pattern.compile(
            "(?:读取|查看|分析|总结|检查|看看|打开).{0,20}"
                    + "(?:这个|这份|刚才|上一个|已选择的|excel|xlsx|电子表格|工作簿)"
                    + "|(?:这个|这份|刚才|上一个|已选择的).{0,20}"
                    + "(?:excel|xlsx|电子表格|工作簿).{0,12}(?:内容|数据|结构|有什么)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern APPEND_PATTERN = Pattern.compile(
            "(?:在|给|往).{0,20}(?:这个|这份|刚才|上一个|excel|xlsx|电子表格|工作簿)"
                    + ".{0,30}(?:新增|增加|追加|添加).{0,12}(?:工作表|sheet|表页)"
                    + "|(?:新增|增加|追加|添加).{0,12}(?:工作表|sheet|表页)"
                    + ".{0,20}(?:这个|这份|刚才|上一个|excel|xlsx|电子表格|工作簿)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /**
     * Returns whether the current turn explicitly asks for a new XLSX file.
     */
    public boolean hasIntent(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = Normalizer.normalize(
                text.strip().toLowerCase(Locale.ROOT),
                Normalizer.Form.NFKC);
        return FORMAT_PATTERN.matcher(normalized).find()
                && CREATION_PATTERN.matcher(normalized).find()
                && !INFORMATION_QUERY_PATTERN.matcher(normalized).find()
                && !NEGATION_PATTERN.matcher(normalized).find();
    }

    public SpreadsheetOperation detect(String text) {
        if (text == null || text.isBlank()) {
            return SpreadsheetOperation.NONE;
        }
        String normalized = Normalizer.normalize(
                text.strip().toLowerCase(Locale.ROOT),
                Normalizer.Form.NFKC);
        if (APPEND_PATTERN.matcher(normalized).find()) {
            return SpreadsheetOperation.APPEND;
        }
        if (READ_PATTERN.matcher(normalized).find()) {
            return SpreadsheetOperation.READ;
        }
        return hasIntent(normalized)
                ? SpreadsheetOperation.GENERATE
                : SpreadsheetOperation.NONE;
    }
}
