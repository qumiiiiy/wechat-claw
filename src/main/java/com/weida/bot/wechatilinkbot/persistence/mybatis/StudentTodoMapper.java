package com.weida.bot.wechatilinkbot.persistence.mybatis;

import com.weida.bot.wechatilinkbot.model.StudentTodo;
import com.weida.bot.wechatilinkbot.model.StudentTodoStatus;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** MyBatis mapper for durable student todo persistence. */
public interface StudentTodoMapper {

    void insert(StudentTodo todo);

    int updateStatus(@Param("id") String id,
                     @Param("conversationId") String conversationId,
                     @Param("previousStatus") StudentTodoStatus previousStatus,
                     @Param("newStatus") StudentTodoStatus newStatus);

    StudentTodo findById(@Param("id") String id, @Param("conversationId") String conversationId);

    List<StudentTodo> listPendingByConversation(@Param("conversationId") String conversationId);

    List<StudentTodo> listAllPending();

    int updateDueTime(@Param("id") String id,
                      @Param("conversationId") String conversationId,
                      @Param("expectedStatus") StudentTodoStatus expectedStatus,
                      @Param("newDueTime") java.time.Instant newDueTime);

    int deleteCompletedOlderThan(@Param("cutoff") java.time.Instant cutoff);
}
