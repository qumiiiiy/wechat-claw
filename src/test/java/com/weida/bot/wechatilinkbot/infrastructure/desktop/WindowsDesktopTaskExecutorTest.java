package com.weida.bot.wechatilinkbot.infrastructure.desktop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import org.junit.jupiter.api.Test;

class WindowsDesktopTaskExecutorTest {

    @Test
    void reportsTheBrowserGatewayAvailability() {
        DesktopBrowserGateway gateway = mock(DesktopBrowserGateway.class);
        when(gateway.available()).thenReturn(true);

        assertThat(new WindowsDesktopTaskExecutor(gateway).available()).isTrue();
    }

    @Test
    void delegatesOnlyTheValidatedUriToTheBrowserGateway() {
        DesktopBrowserGateway gateway = mock(DesktopBrowserGateway.class);
        WindowsDesktopTaskExecutor executor = new WindowsDesktopTaskExecutor(gateway);
        URI url = URI.create("https://example.edu.cn/course");

        assertThat(executor.openUrl(url).summary()).contains("example.edu.cn");
        verify(gateway).open(url);
    }
}
