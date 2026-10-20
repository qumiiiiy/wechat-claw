package com.weida.bot.wechatilinkbot.persistence.mybatis;

import com.weida.bot.wechatilinkbot.model.DesktopTask;
import org.apache.ibatis.annotations.Param;

/** MyBatis mapper for durable user-approved desktop tasks. */
public interface DesktopTaskMapper {

    void insert(DesktopTask task);

    int update(DesktopTask task);

    DesktopTask findById(@Param("taskId") String taskId);
}
