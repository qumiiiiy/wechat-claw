package com.weida.bot.wechatilinkbot.infrastructure.learning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weida.bot.wechatilinkbot.model.CourseTask;
import com.weida.bot.wechatilinkbot.model.CourseTask.CompletionStatus;
import com.weida.bot.wechatilinkbot.model.CourseTask.TaskType;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import javax.imageio.ImageIO;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChaoxingClientTest {

    private static final URI PASSPORT_URI = URI.create("https://passport2.chaoxing.com/login");
    private static final URI QR_URI = URI.create(
            "https://passport2.chaoxing.com/createqr?uuid=synthetic-uuid&fid=-1");
    private static final URI STATUS_URI = URI.create(
            "https://passport2.chaoxing.com/getauthstatus/v2");
    private static final Instant NOW = Instant.parse("2026-08-04T12:00:00Z");

    private final ChaoxingProperties properties = new ChaoxingProperties();
    private final CourseJsonParser courseJsonParser = mock(CourseJsonParser.class);
    private final TaskListParser taskListParser = mock(TaskListParser.class);
    private final ChaoxingHttpGateway httpGateway = mock(ChaoxingHttpGateway.class);
    private ChaoxingClient client;

    @BeforeEach
    void setUp() {
        properties.setEnabled(true);
        properties.setPassportUrl(PASSPORT_URI.toString());
        client = new ChaoxingClient(
                properties,
                courseJsonParser,
                taskListParser,
                new ObjectMapper(),
                httpGateway,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void startsQrLoginAndKeepsChallengeOnlyInMemory() throws Exception {
        byte[] png = pngBytes();
        stubLoginPage(png);

        ChaoxingLoginChallenge challenge = client.startQrLogin();

        assertThat(ImageIO.read(new java.io.ByteArrayInputStream(challenge.qrCodePng())))
                .isNotNull();
        assertThat(challenge.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(3)));
        assertThat(properties.getCookie()).isNull();
        assertThat(client.isAvailable()).isFalse();
        verify(httpGateway).clearCookies();
        verify(httpGateway).getImage(QR_URI, PASSPORT_URI);
    }

    @Test
    void configuredExternalCookieReusesSessionAfterProcessRestart() {
        properties.setCookie("_uid=synthetic-user; xxtenc=synthetic-enc");
        ChaoxingClient restartedClient = new ChaoxingClient(
                properties,
                courseJsonParser,
                taskListParser,
                new ObjectMapper(),
                httpGateway,
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(restartedClient.isAvailable()).isTrue();
        assertThat(restartedClient.refreshLoginStatus())
                .isEqualTo(ChaoxingLoginStatus.AUTHENTICATED);
    }

    @Test
    void authenticatesSharedSessionAfterAppConfirmation() throws Exception {
        stubLoginPage(pngBytes());
        client.startQrLogin();
        when(httpGateway.postForm(STATUS_URI, PASSPORT_URI, java.util.Map.of(
                "enc", "synthetic-enc",
                "uuid", "synthetic-uuid",
                "doubleFactorLogin", "0",
                "forbidotherlogin", "0")))
                .thenReturn("{\"status\":true,\"uid\":\"synthetic-user\"}");

        assertThat(client.refreshLoginStatus()).isEqualTo(ChaoxingLoginStatus.AUTHENTICATED);
        assertThat(client.isAvailable()).isTrue();
        assertThat(properties.getUid()).isEqualTo("synthetic-user");
    }

    @Test
    void keepsTaskParameterFromCookieWhenCoursePageHasNoQueryLink() throws Exception {
        stubLoginPage(pngBytes());
        client.startQrLogin();
        URI courseUri = URI.create("https://mooc1.chaoxing.com/mycourse");
        when(httpGateway.postForm(STATUS_URI, PASSPORT_URI, java.util.Map.of(
                "enc", "synthetic-enc",
                "uuid", "synthetic-uuid",
                "doubleFactorLogin", "0",
                "forbidotherlogin", "0")))
                .thenReturn("{\"status\":true,\"uid\":\"synthetic-user\"}");
        when(httpGateway.getText(courseUri, URI.create("https://mooc1.chaoxing.com")))
                .thenReturn("<html>课程页没有 enc 链接</html>");
        when(httpGateway.cookieValue("xxtenc"))
                .thenReturn(java.util.Optional.of("0123456789abcdef0123456789abcdef"));

        assertThat(client.refreshLoginStatus()).isEqualTo(ChaoxingLoginStatus.AUTHENTICATED);

        assertThat(properties.getEnc()).isEqualTo("0123456789abcdef0123456789abcdef");
    }

    @Test
    void distinguishesScanFromMobileConfirmation() throws Exception {
        stubLoginPage(pngBytes());
        client.startQrLogin();
        when(httpGateway.postForm(eq(STATUS_URI), eq(PASSPORT_URI), anyMap()))
                .thenReturn("{\"status\":false,\"type\":4}");

        assertThat(client.refreshLoginStatus())
                .isEqualTo(ChaoxingLoginStatus.WAITING_CONFIRMATION);
        assertThat(client.isAvailable()).isFalse();
    }

    @Test
    void expiresChallengeWithoutCallingStatusEndpoint() throws Exception {
        Clock expiringClock = mock(Clock.class);
        when(expiringClock.instant()).thenReturn(NOW, NOW.plus(Duration.ofMinutes(4)));
        client = new ChaoxingClient(
                properties,
                courseJsonParser,
                taskListParser,
                new ObjectMapper(),
                httpGateway,
                expiringClock);
        stubLoginPage(pngBytes());
        client.startQrLogin();

        assertThat(client.refreshLoginStatus()).isEqualTo(ChaoxingLoginStatus.EXPIRED);
        verify(httpGateway, never()).postForm(eq(STATUS_URI), eq(PASSPORT_URI), anyMap());
    }

    @Test
    void rejectsLoginPageWithoutQrChallenge() {
        when(httpGateway.getText(PASSPORT_URI, null))
                .thenReturn("<html><body>synthetic changed page</body></html>");

        assertThatThrownBy(client::startQrLogin)
                .isInstanceOf(ChaoxingLoginException.class)
                .hasMessageContaining("未提供可用二维码");
    }

    @Test
    void rejectsNonImageQrResponse() {
        stubLoginPage(new byte[]{1, 2, 3});

        assertThatThrownBy(client::startQrLogin)
                .isInstanceOf(ChaoxingLoginException.class)
                .hasMessageContaining("二维码图片无效");
    }

    @Test
    void reportsExpiredAuthenticationInsteadOfEmptyCourseList() {
        properties.setCookie("synthetic=session");
        client = new ChaoxingClient(
                properties,
                courseJsonParser,
                taskListParser,
                new ObjectMapper(),
                httpGateway,
                Clock.fixed(NOW, ZoneOffset.UTC));
        URI courseUri = URI.create("https://mooc1.chaoxing.com/mycourse");
        when(httpGateway.getText(
                courseUri,
                URI.create("https://mooc1.chaoxing.com")))
                .thenReturn("<html><body><img id=\"quickCode\"></body></html>");

        assertThatThrownBy(client::fetchCourseDetails)
                .isInstanceOf(ChaoxingLoginException.class)
                .hasMessageContaining("登录状态已失效");
        assertThat(client.isAvailable()).isFalse();
    }

    @Test
    void reportsChangedCoursePageInsteadOfReturningEmptyCourseList() {
        properties.setCookie("synthetic=session");
        client = new ChaoxingClient(
                properties,
                courseJsonParser,
                taskListParser,
                new ObjectMapper(),
                httpGateway,
                Clock.fixed(NOW, ZoneOffset.UTC));
        URI courseUri = URI.create("https://mooc1.chaoxing.com/mycourse");
        when(httpGateway.getText(
                courseUri,
                URI.create("https://mooc1.chaoxing.com")))
                .thenReturn("<html><body>课程页面结构已变化</body></html>");

        assertThatThrownBy(client::fetchCourseDetails)
                .isInstanceOf(ChaoxingLoginException.class)
                .hasMessageContaining("课程页面格式已变化");
    }

    @Test
    void reportsMalformedCoursePayloadInsteadOfReturningEmptyCourseList() {
        properties.setCookie("synthetic=session");
        client = new ChaoxingClient(
                properties,
                courseJsonParser,
                taskListParser,
                new ObjectMapper(),
                httpGateway,
                Clock.fixed(NOW, ZoneOffset.UTC));
        URI courseUri = URI.create("https://mooc1.chaoxing.com/mycourse");
        when(httpGateway.getText(
                courseUri,
                URI.create("https://mooc1.chaoxing.com")))
                .thenReturn("<script>var data = {\"channelList\":[};</script>");

        assertThatThrownBy(client::fetchCourseDetails)
                .isInstanceOf(ChaoxingLoginException.class)
                .hasMessageContaining("课程数据格式已变化");
    }

    @Test
    void acceptsExplicitlyEmptyCoursePayload() {
        properties.setCookie("synthetic=session");
        client = new ChaoxingClient(
                properties,
                courseJsonParser,
                taskListParser,
                new ObjectMapper(),
                httpGateway,
                Clock.fixed(NOW, ZoneOffset.UTC));
        URI courseUri = URI.create("https://mooc1.chaoxing.com/mycourse");
        when(httpGateway.getText(
                courseUri,
                URI.create("https://mooc1.chaoxing.com")))
                .thenReturn("<script>var data = {\"channelList\":[]};</script>");

        assertThat(client.fetchCourseDetails()).isEmpty();
    }

    @Test
    void rejectsUnsafeTaskIdentifierWithoutRequestingUpstream() {
        properties.setCookie("synthetic=session");
        client = new ChaoxingClient(
                properties,
                courseJsonParser,
                taskListParser,
                new ObjectMapper(),
                httpGateway,
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> client.fetchTasks("../course", "class", "user", ""))
                .isInstanceOf(ChaoxingLoginException.class)
                .hasMessageContaining("任务查询参数无效");
        verify(httpGateway, never()).getText(
                org.mockito.ArgumentMatchers.any(URI.class),
                org.mockito.ArgumentMatchers.any(URI.class));
    }

    @Test
    void usesWorkEncFromCoursePageForTaskList() {
        properties.setCookie("synthetic=session");
        client = new ChaoxingClient(
                properties,
                courseJsonParser,
                taskListParser,
                new ObjectMapper(),
                httpGateway,
                Clock.fixed(NOW, ZoneOffset.UTC));
        URI referer = URI.create("https://mooc1.chaoxing.com/mycourse");
        CourseTask expected = new CourseTask(
                "work-1", "第三章作业", TaskType.HOMEWORK, CompletionStatus.PENDING,
                null, "course-1", "chaoxing", "");
        when(taskListParser.parseTaskList(anyString(), eq("course-1"), eq("chaoxing")))
                .thenReturn(List.of(expected));
        when(httpGateway.getText(any(URI.class), eq(referer))).thenAnswer(invocation -> {
            URI uri = invocation.getArgument(0);
            if (uri.getPath().contains("stucoursemiddle")
                    || uri.getPath().contains("studentcourse")) {
                return "<html><body><input id=\"workEnc\" "
                        + "value=\"0123456789abcdef0123456789abcdef\"></body></html>";
            }
            if (uri.getPath().contains("/mooc2/work/list")) {
                if (uri.getQuery().contains("enc=0123456789abcdef0123456789abcdef")) {
                    return "<html><body><div class=\"bottomList\"><ul><li>"
                            + "<p class=\"overHidden2\">第三章作业</p>"
                            + "<p class=\"status\">未完成</p></li></ul></div></body></html>";
                }
                return "<html>无权限</html>";
            }
            return "<html></html>";
        });

        List<CourseTask> tasks = client.fetchTasks(
                "course-1", "class-1", "cpi-1", "channel-enc");

        assertThat(tasks).containsExactly(expected);
        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
        verify(httpGateway, atLeast(2)).getText(uriCaptor.capture(), eq(referer));
        assertThat(uriCaptor.getAllValues()).anySatisfy(uri ->
                assertThat(uri.getQuery()).contains("enc=0123456789abcdef0123456789abcdef"));
    }

    @Test
    void fallsBackToPassedEncWhenWorkEncPageIsUnavailable() {
        properties.setCookie("synthetic=session");
        client = new ChaoxingClient(
                properties,
                courseJsonParser,
                taskListParser,
                new ObjectMapper(),
                httpGateway,
                Clock.fixed(NOW, ZoneOffset.UTC));
        URI referer = URI.create("https://mooc1.chaoxing.com/mycourse");
        CourseTask expected = new CourseTask(
                "work-1", "第三章作业", TaskType.HOMEWORK, CompletionStatus.PENDING,
                null, "course-1", "chaoxing", "");
        when(taskListParser.parseTaskList(anyString(), eq("course-1"), eq("chaoxing")))
                .thenReturn(List.of(expected));
        when(httpGateway.getText(any(URI.class), eq(referer))).thenAnswer(invocation -> {
            URI uri = invocation.getArgument(0);
            if (uri.getPath().contains("stucoursemiddle")
                    || uri.getPath().contains("studentcourse")) {
                return "<html><body>workEnc 不在页面中</body></html>";
            }
            if (uri.getPath().contains("/mooc2/work/list")) {
                return "<html><body><div class=\"bottomList\"><ul><li>"
                        + "<p class=\"overHidden2\">第三章作业</p>"
                        + "<p class=\"status\">未完成</p></li></ul></div></body></html>";
            }
            return "<html></html>";
        });

        List<CourseTask> tasks = client.fetchTasks(
                "course-1", "class-1", "cpi-1", "channel-enc");

        assertThat(tasks).containsExactly(expected);
        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
        verify(httpGateway, atLeast(2)).getText(uriCaptor.capture(), eq(referer));
        assertThat(uriCaptor.getAllValues()).anySatisfy(uri ->
                assertThat(uri.getQuery()).contains("enc=channel-enc"));
    }

    @Test
    void reportsBlockedTaskPageInsteadOfEmptyTaskList() {
        properties.setCookie("synthetic=session");
        client = new ChaoxingClient(
                properties,
                courseJsonParser,
                taskListParser,
                new ObjectMapper(),
                httpGateway,
                Clock.fixed(NOW, ZoneOffset.UTC));
        URI referer = URI.create("https://mooc1.chaoxing.com/mycourse");
        when(httpGateway.getText(any(URI.class), eq(referer))).thenAnswer(invocation -> {
            URI uri = invocation.getArgument(0);
            if (uri.getPath().contains("stucoursemiddle")
                    || uri.getPath().contains("studentcourse")) {
                return "<html><body>workEnc 不在页面中</body></html>";
            }
            if (uri.getPath().contains("/mooc2/work/list")) {
                return "<html>无权限</html>";
            }
            return "<html></html>";
        });

        assertThatThrownBy(() -> client.fetchTasks(
                "course-1", "class-1", "cpi-1", "channel-enc"))
                .isInstanceOf(ChaoxingLoginException.class)
                .hasMessageContaining("无权限");
        verify(taskListParser, never()).parseTaskList(anyString(), anyString(), anyString());
    }

    private void stubLoginPage(byte[] png) {
        when(httpGateway.getText(PASSPORT_URI, null)).thenReturn("""
                <html><body>
                  <input id="uuid" value="synthetic-uuid">
                  <input id="enc" value="synthetic-enc">
                  <input id="doubleFactorLogin" value="0">
                  <input id="forbidotherlogin" value="0">
                  <img id="quickCode" src="/createqr?uuid=synthetic-uuid&amp;fid=-1">
                </body></html>
                """);
        when(httpGateway.getImage(QR_URI, PASSPORT_URI)).thenReturn(png);
    }

    private static byte[] pngBytes() throws Exception {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "PNG", output);
            return output.toByteArray();
        }
    }
}
