package com.weida.bot.wechatilinkbot.tool;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Provider-neutral metadata used to build a tool input JSON Schema.
 */
@Target(ElementType.RECORD_COMPONENT)
@Retention(RetentionPolicy.RUNTIME)
public @interface ToolParameter {

    String description();

    boolean required() default true;
}
