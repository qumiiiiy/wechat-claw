package com.weida.bot.wechatilinkbot.capability;

import com.weida.bot.wechatilinkbot.model.PendingItem;
import com.weida.bot.wechatilinkbot.model.PendingItemSource;
import java.util.List;

/**
 * Port for one source of pending items plugged into the unified todo view.
 *
 * <p>Each adapter provides read-only access to items from its domain
 * (personal todos, email, learning platform). The {@code PendingItemService}
 * aggregates items from all adapters and assigns stable sequence numbers.</p>
 */
public interface PendingItemAdapter {

    /** Which source this adapter represents. */
    PendingItemSource source();

    /** Returns all pending items from this source for the given conversation. */
    List<PendingItem> fetchItems(String conversationId);

    /** Whether items from this source can be modified by the user. */
    boolean supportsModify();
}