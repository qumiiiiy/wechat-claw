package com.weida.bot.wechatilinkbot.capability;

import com.weida.bot.wechatilinkbot.model.PendingItem;
import com.weida.bot.wechatilinkbot.model.PendingItemSource;
import com.weida.bot.wechatilinkbot.model.StudentTodo;
import com.weida.bot.wechatilinkbot.model.StudentTodoStatus;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Adapts existing personal todos into the unified {@link PendingItem} view.
 *
 * <p>Write operations still go through {@link StudentTodoCapability} directly;
 * this adapter is read-only and only provides items for display.</p>
 */
@Component
public class PersonalTodoPendingAdapter implements PendingItemAdapter {

    private static final Comparator<StudentTodo> STABLE_SORT =
            Comparator.comparing(StudentTodo::dueTime).thenComparing(StudentTodo::id);

    private final StudentTodoCapability capability;

    public PersonalTodoPendingAdapter(StudentTodoCapability capability) {
        this.capability = capability;
    }

    @Override
    public PendingItemSource source() {
        return PendingItemSource.PERSONAL;
    }

    @Override
    public List<PendingItem> fetchItems(String conversationId) {
        return capability.listTodos(conversationId).stream()
                .filter(todo -> todo.status() == StudentTodoStatus.PENDING)
                .sorted(STABLE_SORT)
                .map(this::toPendingItem)
                .toList();
    }

    @Override
    public boolean supportsModify() {
        return true;
    }

    private PendingItem toPendingItem(StudentTodo todo) {
        return new PendingItem(
                0, todo.id(), todo.title(),
                PendingItemSource.PERSONAL, todo.status(), todo.dueTime(),
                null);
    }
}