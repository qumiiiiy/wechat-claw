package com.weida.bot.wechatilinkbot.channel.web;

import com.weida.bot.wechatilinkbot.agent.AgentException;
import com.weida.bot.wechatilinkbot.capability.MediaGenerationException;
import com.weida.bot.wechatilinkbot.channel.WechatChannelNotReadyException;
import com.weida.bot.wechatilinkbot.channel.WechatSessionNotFoundException;
import com.weida.bot.wechatilinkbot.infrastructure.media.MediaConversionException;
import com.weida.bot.wechatilinkbot.persistence.ConversationPersistenceException;
import jakarta.validation.ConstraintViolationException;
import java.io.IOException;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Converts top-level HTTP failures to stable error codes without leaking internal details.
 *
 * @author ryx
 * @since 2026-07-20
 */
@RestControllerAdvice
public class ChannelExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ChannelExceptionHandler.class);
    private static final URI PROBLEM_TYPE = URI.create("urn:problem:wechat-claw");

    @ExceptionHandler(WechatChannelNotReadyException.class)
    ProblemDetail handleBotNotReady(WechatChannelNotReadyException exception) {
        return problem(
                HttpStatus.CONFLICT,
                "BOT_NOT_READY",
                "微信 Bot 状态异常",
                exception.getMessage());
    }

    @ExceptionHandler(WechatSessionNotFoundException.class)
    ProblemDetail handleBotSessionNotFound(WechatSessionNotFoundException exception) {
        return problem(
                HttpStatus.NOT_FOUND,
                "BOT_SESSION_NOT_FOUND",
                "微信 Bot 会话不存在",
                exception.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, ConstraintViolationException.class})
    ProblemDetail handleBadRequest(RuntimeException exception) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST_PARAMETER",
                "请求参数错误",
                exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        String detail = exception.getBindingResult().getAllErrors().stream()
                .findFirst()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .orElse("请求参数校验失败");
        return problem(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST_PARAMETER",
                "请求参数错误",
                detail);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class,
            MethodArgumentTypeMismatchException.class
    })
    ProblemDetail handleRequestBindingFailure(Exception exception) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST_FORMAT",
                "请求格式错误",
                "请求参数缺失或格式不正确");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ProblemDetail handleUnsupportedMediaType(HttpMediaTypeNotSupportedException exception) {
        return problem(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "UNSUPPORTED_MEDIA_TYPE",
                "媒体类型不支持",
                "请使用接口声明的媒体类型");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail handleMaxUploadSizeExceeded(MaxUploadSizeExceededException exception) {
        return problem(
                HttpStatus.CONTENT_TOO_LARGE,
                "UPLOAD_TOO_LARGE",
                "上传文件过大",
                "上传内容超过服务端限制");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ProblemDetail handleNoResourceFound(NoResourceFoundException exception) {
        return problem(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "请求资源不存在",
                "请求的资源不存在");
    }

    @ExceptionHandler({
            AgentException.class,
            MediaGenerationException.class,
            MediaConversionException.class,
            ConversationPersistenceException.class
    })
    ProblemDetail handleDependencyFailure(RuntimeException exception) {
        LOG.error("agent_dependency_failure", exception);
        return problem(
                HttpStatus.SERVICE_UNAVAILABLE,
                "AGENT_DEPENDENCY_UNAVAILABLE",
                "智能体依赖暂时不可用",
                "请稍后重试");
    }

    @ExceptionHandler(IOException.class)
    ProblemDetail handleIoException(IOException exception) {
        LOG.error("wechat_upstream_io_failed", exception);
        return problem(
                HttpStatus.BAD_GATEWAY,
                "WECHAT_UPSTREAM_FAILURE",
                "微信服务调用失败",
                "上游服务暂时不可用，请稍后重试");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpectedException(Exception exception) {
        LOG.error("unexpected_request_failure", exception);
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR",
                "系统内部错误",
                "请稍后重试");
    }

    private static ProblemDetail problem(
            HttpStatus status,
            String errorCode,
            String title,
            String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(PROBLEM_TYPE);
        problem.setProperty("errorCode", errorCode);
        return problem;
    }
}
