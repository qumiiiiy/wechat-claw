package com.weida.bot.wechatilinkbot.channel.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class ChannelExceptionHandlerTest {

    @Test
    void convertsMissingStaticResourceToStableNotFoundResponse() {
        ChannelExceptionHandler handler = new ChannelExceptionHandler();

        ProblemDetail problem = handler.handleNoResourceFound(
                mock(NoResourceFoundException.class));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getProperties())
                .containsEntry("errorCode", "RESOURCE_NOT_FOUND");
    }
}
