package com.weida.bot.wechatilinkbot.infrastructure.learning;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChaoxingHttpGatewayTest {

    @Test
    void rejectsNonHttpsRequestBeforeNetworkAccess() {
        ChaoxingHttpGateway gateway = new ChaoxingHttpGateway(new ChaoxingProperties());

        assertThatThrownBy(() -> gateway.getText(
                URI.create("http://passport2.chaoxing.com/login"), null))
                .isInstanceOf(ChaoxingLoginException.class)
                .hasMessageContaining("安全校验");
    }

    @Test
    void rejectsRedirectOutsideLearningHostAllowlist() throws Exception {
        ChaoxingProperties properties = new ChaoxingProperties();
        HttpClient httpClient = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(302);
        when(response.headers()).thenReturn(HttpHeaders.of(
                Map.of("Location", List.of("https://synthetic.invalid/capture")),
                (name, value) -> true));
        when(response.body()).thenReturn(InputStream.nullInputStream());
        when(httpClient.send(
                any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<InputStream>>any()))
                .thenReturn(response);
        ChaoxingHttpGateway gateway = new ChaoxingHttpGateway(
                properties,
                new CookieManager(null, CookiePolicy.ACCEPT_ALL),
                httpClient);

        assertThatThrownBy(() -> gateway.getText(
                URI.create("https://passport2.chaoxing.com/login"), null))
                .isInstanceOf(ChaoxingLoginException.class)
                .hasMessageContaining("安全校验");
    }

    @Test
    void allowsLearningSubdomainsUsedForWorkEncExtraction() throws Exception {
        ChaoxingProperties properties = new ChaoxingProperties();
        HttpClient httpClient = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.headers()).thenReturn(HttpHeaders.of(Map.of(), (name, value) -> true));
        when(response.body()).thenReturn(new ByteArrayInputStream(
                "<html>workEnc page</html>".getBytes(StandardCharsets.UTF_8)));
        when(httpClient.send(
                any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<InputStream>>any()))
                .thenReturn(response);
        ChaoxingHttpGateway gateway = new ChaoxingHttpGateway(
                properties,
                new CookieManager(null, CookiePolicy.ACCEPT_ALL),
                httpClient);

        assertThat(gateway.getText(
                URI.create("https://mooc1-2.chaoxing.com/mooc-ans/visit/stucoursemiddle"
                        + "?courseid=1&clazzid=2&vc=1&cpi=3&ismooc2=1&v=2"),
                null)).contains("workEnc page");
    }
}
