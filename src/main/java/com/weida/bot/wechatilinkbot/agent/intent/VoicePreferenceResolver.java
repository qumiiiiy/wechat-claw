package com.weida.bot.wechatilinkbot.agent.intent;

import com.weida.bot.wechatilinkbot.model.ConversationMessage;
import com.weida.bot.wechatilinkbot.model.VoicePreference;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Resolves explicit voice-switch requests from the current turn and conversation history.
 *
 * @author ryx
 * @since 2026-07-21
 */
@Component
public class VoicePreferenceResolver {

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern VOICE_OUTPUT_REQUEST_PATTERN = Pattern.compile(
            "(?:(?:用|以).{0,24}(?:说|读|念|回复|回答|讲|唱|配音)|"
                    + "(?:音色|声音|男声|女声|童声|男童|女童|旁白|机器人|粤语|"
                    + "陕西话|闽南语).{0,16}(?:说|读|念|回复|回答|讲|唱|配音))");
    private static final List<String> SWITCH_ACTION_MARKER_LIST = List.of(
            "换成", "切换成", "切换为", "改成", "改为", "改用", "换个", "换一个",
            "使用", "请用", "用女声", "用男声", "女声回复", "男声回复", "以后都",
            "我喜欢", "我想听", "我要", "想要", "给我来个", "来个", "换回",
            "恢复", "用孩子的声音", "用儿童声音",
            "用小朋友的声音", "用童声", "童声说", "再试一次", "再试试",
            "重试一次", "重新试", "调成", "调为", "调得", "声音更", "声音再",
            "音色更", "音色再", "说得更", "说得再", "更像", "温柔一点",
            "活泼一点", "甜一点", "更温柔", "更活泼");
    private static final List<VoiceProfile> VOICE_PROFILE_LIST = List.of(
            profile(VoicePreference.DEFAULT,
                    "默认音色", "默认声音", "原来的声音", "原音色", "默认voice"),
            profile(VoicePreference.MALE,
                    "男声", "男生声音", "男性声音", "龙安洋", "longanyang"),
            profile(VoicePreference.FEMALE,
                    "女声", "女生声音", "女性声音", "龙安欢", "longanhuan"),
            profile(VoicePreference.CHILD,
                    "童声", "儿童声音", "孩子的声音", "孩子声音", "小朋友声音",
                    "小朋友的声音", "孩子气", "奶声奶气", "龙呼呼", "longhuhu"),
            profile(VoicePreference.BOY,
                    "男童声", "男童", "男孩声音", "小男孩声音", "小男生声音", "少年音",
                    "少年声音", "龙汪汪", "longwangwang"),
            profile(VoicePreference.GIRL,
                    "女童声", "女童", "女孩声音", "小女孩声音", "小女生声音", "萝莉音"),
            profile(VoicePreference.YOUNG_MALE,
                    "青年男声", "年轻男声", "清爽男声", "阳光男声", "小哥哥声音",
                    "龙安朗", "longanlang"),
            profile(VoicePreference.YOUNG_FEMALE,
                    "青年女声", "年轻女声", "少女音", "小姐姐声音"),
            profile(VoicePreference.MATURE_MALE,
                    "成熟男声", "大叔音", "叔叔声音", "稳重男声", "睿智男声",
                    "龙安智", "longanzhi"),
            profile(VoicePreference.MATURE_FEMALE,
                    "成熟女声", "御姐音", "姐姐声音", "高雅女声", "气质女声",
                    "龙安雅", "longanya"),
            profile(VoicePreference.ELDERLY_MALE,
                    "老年男声", "老人男声", "爷爷声音", "老爷爷声音", "龙老伯",
                    "longlaobo"),
            profile(VoicePreference.ELDERLY_FEMALE,
                    "老年女声", "老人女声", "奶奶声音", "老奶奶声音", "龙老姨",
                    "longlaoyi"),
            profile(VoicePreference.ENERGETIC_FEMALE,
                    "活泼女声", "元气女声", "欢脱女声", "元气少女", "亲和活泼",
                    "活泼一点", "更活泼", "再活泼", "龙安亲", "longanqin"),
            profile(VoicePreference.SWEET_FEMALE,
                    "甜美女声", "甜妹音", "清甜女声", "甜一点", "龙应笑", "longyingxiao"),
            profile(VoicePreference.GENTLE_FEMALE,
                    "温柔女声", "柔美女声", "柔和女声", "温柔一点", "更温柔",
                    "再温柔", "声音柔和", "龙应桃", "longyingtao"),
            profile(VoicePreference.WARM_MALE,
                    "暖男声", "温暖男声", "居家男声", "温柔男声", "龙安昀", "longanyun"),
            profile(VoicePreference.INTELLECTUAL_FEMALE,
                    "知性女声", "优雅女声", "优雅知性", "龙安温", "longanwen"),
            profile(VoicePreference.NARRATOR_MALE,
                    "男旁白", "男声旁白", "男声讲故事", "有声书男声", "龙逸尘",
                    "longyichen"),
            profile(VoicePreference.NARRATOR_FEMALE,
                    "女旁白", "女声旁白", "女声讲故事", "有声书女声", "龙婉君",
                    "longwanjun"),
            profile(VoicePreference.PROFESSIONAL_FEMALE,
                    "专业女声", "干练女声", "播音女声", "新闻女声", "bella",
                    "loongbella"),
            profile(VoicePreference.LIVE_STREAM_FEMALE,
                    "直播女声", "带货女声", "主播女声", "龙安宣", "longanxuan"),
            profile(VoicePreference.ROBOT,
                    "机器人声音", "机器人音", "机械音", "电子音", "龙机器", "longjiqi"),
            profile(VoicePreference.MONKEY_KING,
                    "猴哥声音", "孙悟空声音", "悟空声音", "猴哥音", "龙猴哥", "longhouge"),
            profile(VoicePreference.CANTONESE_MALE,
                    "粤语男声", "广东话男声", "粤语声音", "用粤语", "广东话",
                    "龙安粤", "longanyue"),
            profile(VoicePreference.SHAANXI_MALE,
                    "陕西男声", "陕西话", "陕北话", "龙陕哥", "longshange"),
            profile(VoicePreference.MINNAN_FEMALE,
                    "闽南女声", "闽南语", "闽南话", "龙安闽", "longanmin"));

    public VoicePreference resolve(
            List<ConversationMessage> history,
            String currentPrompt) {
        Optional<VoicePreference> currentPreference = findPreference(currentPrompt);
        if (currentPreference.isPresent()) {
            return currentPreference.get();
        }
        if (history == null || history.isEmpty()) {
            return VoicePreference.DEFAULT;
        }
        for (int index = history.size() - 1; index >= 0; index--) {
            ConversationMessage message = history.get(index);
            if (message != null && "user".equals(message.role())) {
                Optional<VoicePreference> preference =
                        findPreference(message.content());
                if (preference.isPresent()) {
                    return preference.get();
                }
            }
        }
        return VoicePreference.DEFAULT;
    }

    public boolean hasSwitchIntent(String text) {
        return findPreference(text).isPresent();
    }

    private Optional<VoicePreference> findPreference(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String normalized = normalize(text);
        boolean switchRequested = containsAny(normalized, SWITCH_ACTION_MARKER_LIST)
                || VOICE_OUTPUT_REQUEST_PATTERN.matcher(normalized).find();
        if (!switchRequested) {
            return Optional.empty();
        }
        VoiceMatch selectedMatch = VoiceMatch.none();
        for (VoiceProfile profile : VOICE_PROFILE_LIST) {
            selectedMatch = latestMatch(normalized, profile, selectedMatch);
        }
        return Optional.ofNullable(selectedMatch.preference());
    }

    private static VoiceMatch latestMatch(
            String text,
            VoiceProfile profile,
            VoiceMatch selectedMatch) {
        VoiceMatch result = selectedMatch;
        for (String marker : profile.markerList()) {
            int markerIndex = text.lastIndexOf(marker);
            if (markerIndex < 0) {
                continue;
            }
            int markerEndIndex = markerIndex + marker.length();
            boolean laterMarker = markerEndIndex > result.endIndex();
            boolean moreSpecificMarker = markerEndIndex == result.endIndex()
                    && marker.length() > result.markerLength();
            if (laterMarker || moreSpecificMarker) {
                result = new VoiceMatch(
                        markerEndIndex,
                        marker.length(),
                        profile.preference());
            }
        }
        return result;
    }

    private static VoiceProfile profile(
            VoicePreference preference,
            String... markers) {
        return new VoiceProfile(preference, List.of(markers));
    }

    private static boolean containsAny(String text, List<String> markerList) {
        return markerList.stream().anyMatch(text::contains);
    }

    private static String normalize(String text) {
        return WHITESPACE_PATTERN
                .matcher(text.toLowerCase(Locale.ROOT))
                .replaceAll("");
    }

    private record VoiceProfile(
            VoicePreference preference,
            List<String> markerList) {
    }

    private record VoiceMatch(
            int endIndex,
            int markerLength,
            VoicePreference preference) {

        private static VoiceMatch none() {
            return new VoiceMatch(-1, -1, null);
        }
    }
}
