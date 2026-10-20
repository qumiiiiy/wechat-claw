package com.weida.bot.wechatilinkbot.channel.ilink;

import com.github.wechat.ilink.sdk.core.model.MessageItem;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Parses exact, side-effecting conversation commands without involving a model.
 *
 * @author ryx
 * @since 2026-07-21
 */
@Component
public class ConversationCommand {

    private static final Pattern CLEAR_COMMAND_PATTERN = Pattern.compile(
            "^(?:/clear|清[空除](?:对话)?(?:历史|记录|记忆)|忘记之前的对话)[！!。.]?$");

    /** Returns whether the message is exactly one supported clear-memory command. */
    public boolean isClearCommand(List<MessageItem> itemList) {
        if (itemList == null || itemList.size() != 1) {
            return false;
        }
        MessageItem item = itemList.getFirst();
        if (item == null || item.getText_item() == null) {
            return false;
        }
        String text = item.getText_item().getText();
        return text != null && CLEAR_COMMAND_PATTERN.matcher(text.strip()).matches();
    }
}
