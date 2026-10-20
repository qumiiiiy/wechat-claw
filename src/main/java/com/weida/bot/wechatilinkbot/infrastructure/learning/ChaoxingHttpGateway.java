package com.weida.bot.wechatilinkbot.infrastructure.learning;

import java.io.IOException;
import java.io.InputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.util.List;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Bounded HTTP and in-memory cookie boundary for Chaoxing requests. */
@Component
public class ChaoxingHttpGateway {

    private static final Logger LOG = LoggerFactory.getLogger(ChaoxingHttpGateway.class);

    private static final String USER_AGENT = "WeChatClaw/learning-read-only";
    private static final int MAX_TEXT_BYTES = 2 * 1024 * 1024;
    private static final int MAX_IMAGE_BYTES = 1024 * 1024;
    private static final int MAX_REDIRECTS = 5;

    private final ChaoxingProperties properties;
    private final CookieManager cookieManager;
    private final HttpClient httpClient;

    @Autowired
    public ChaoxingHttpGateway(ChaoxingProperties properties) {
        this.properties = properties;
        this.cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .cookieHandler(cookieManager)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        loadConfiguredCookie();
    }

    ChaoxingHttpGateway(
            ChaoxingProperties properties,
            CookieManager cookieManager,
            HttpClient httpClient) {
        this.properties = properties;
        this.cookieManager = cookieManager;
        this.httpClient = httpClient;
        loadConfiguredCookie();
    }

    public String getText(URI uri, URI referer) {
        byte[] bytes = send("GET", uri, referer, null, "text/html,application/json", MAX_TEXT_BYTES);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public byte[] getImage(URI uri, URI referer) {
        return send("GET", uri, referer, null, "image/png,image/*", MAX_IMAGE_BYTES);
    }

    public String postForm(URI uri, URI referer, Map<String, String> form) {
        String body = form.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
        byte[] bytes = send("POST", uri, referer, body, "application/json", MAX_TEXT_BYTES);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public synchronized void clearCookies() {
        cookieManager.getCookieStore().removeAll();
    }

    public synchronized Optional<String> cookieValue(String name) {
        return cookieManager.getCookieStore().getCookies().stream()
                .filter(cookie -> name.equals(cookie.getName()))
                .map(HttpCookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }

    public synchronized List<String> allCookieNames() {
        return cookieManager.getCookieStore().getCookies().stream()
                .map(HttpCookie::getName)
                .toList();
    }

    private byte[] send(
            String initialMethod,
            URI initialUri,
            URI referer,
            String initialBody,
            String accept,
            int maxBytes) {
        URI uri = requireAllowedHttpsUri(initialUri);
        String method = initialMethod;
        String body = initialBody;
        for (int redirectCount = 0; redirectCount <= MAX_REDIRECTS; redirectCount++) {
            HttpRequest request = buildRequest(method, uri, referer, body, accept);
            try {
                HttpResponse<InputStream> response = httpClient.send(
                        request, HttpResponse.BodyHandlers.ofInputStream());
                try (InputStream input = response.body()) {
                    if (isRedirect(response.statusCode())) {
                        if (redirectCount == MAX_REDIRECTS) {
                            throw new ChaoxingLoginException("学习通响应重定向次数过多。");
                        }
                        String location = response.headers().firstValue("Location")
                                .orElseThrow(() -> new ChaoxingLoginException("学习通返回了无效重定向。"));
                        uri = requireAllowedHttpsUri(uri.resolve(location));
                        if (response.statusCode() == 303
                                || ((response.statusCode() == 301 || response.statusCode() == 302)
                                && "POST".equals(method))) {
                            method = "GET";
                            body = null;
                        }
                        continue;
                    }
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        throw new ChaoxingLoginException(
                                "学习通服务暂时不可用（HTTP " + response.statusCode() + "）。");
                    }
                    return readBounded(input, maxBytes);
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new ChaoxingLoginException("学习通请求已中断。", exception);
            } catch (IOException exception) {
                throw new ChaoxingLoginException("无法连接学习通服务。", exception);
            }
        }
        throw new ChaoxingLoginException("学习通请求失败。");
    }

    private HttpRequest buildRequest(
            String method,
            URI uri,
            URI referer,
            String body,
            String accept) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(properties.getRequestTimeout())
                .header("Accept", accept)
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .header("User-Agent", USER_AGENT);
        if (referer != null) {
            builder.header("Referer", requireAllowedHttpsUri(referer).toString());
        }
        if ("POST".equals(method)) {
            builder.header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
        } else {
            builder.GET();
        }
        return builder.build();
    }

    private URI requireAllowedHttpsUri(URI uri) {
        if (uri == null
                || !"https".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || !isAllowedHost(uri.getHost())) {
            LOG.warn("chaoxing_uri_rejected uri={} host={} scheme={}",
                    uri, uri == null ? "null" : uri.getHost(),
                    uri == null ? "null" : uri.getScheme());
            throw new ChaoxingLoginException("学习通地址未通过安全校验。");
        }
        return uri;
    }

    private boolean isAllowedHost(String host) {
        String normalized = host.toLowerCase();
        return normalized.equals(hostOf(properties.getPassportUrl()))
                || normalized.equals(hostOf(properties.getBaseUrl()))
                || normalized.equals("i.mooc.chaoxing.com")
                || normalized.equals("mooc1-1.chaoxing.com")
                || normalized.equals("mooc1-2.chaoxing.com")
                || normalized.equals("mooc2-ans.chaoxing.com")
                || normalized.endsWith(".passport2.chaoxing.com");
    }

    private static String hostOf(String value) {
        try {
            URI uri = URI.create(value == null ? "" : value.strip());
            return uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        } catch (IllegalArgumentException exception) {
            return "";
        }
    }

    private static byte[] readBounded(InputStream input, int maxBytes) throws IOException {
        byte[] bytes = input.readNBytes(maxBytes + 1);
        if (bytes.length > maxBytes) {
            throw new ChaoxingLoginException("学习通响应超过安全大小限制。");
        }
        return bytes;
    }

    private static boolean isRedirect(int statusCode) {
        return statusCode >= 300 && statusCode < 400;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private void loadConfiguredCookie() {
        String configuredCookie = properties.getCookie();
        if (configuredCookie == null || configuredCookie.isBlank()) {
            return;
        }
        URI baseUri = URI.create(properties.getBaseUrl());
        for (String pair : configuredCookie.split(";")) {
            String normalized = pair.strip();
            int separator = normalized.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            HttpCookie cookie = new HttpCookie(
                    normalized.substring(0, separator).strip(),
                    normalized.substring(separator + 1).strip());
            cookie.setDomain(baseUri.getHost());
            cookie.setPath("/");
            cookie.setVersion(0);
            cookieManager.getCookieStore().add(baseUri, cookie);
        }
    }
}
