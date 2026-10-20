package com.weida.bot.wechatilinkbot.infrastructure.learning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weida.bot.wechatilinkbot.infrastructure.learning.CourseJsonParser.CourseDetail;
import com.weida.bot.wechatilinkbot.model.Course;
import com.weida.bot.wechatilinkbot.model.CourseTask;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 学习通客户端。
 * 负责登录、获取课程列表和任务列表。
 */
public class ChaoxingClient {

    private static final Logger log = LoggerFactory.getLogger(ChaoxingClient.class);
    private static final int MAX_QR_DIMENSION = 2048;
    private static final Pattern TASK_IDENTIFIER_PATTERN = Pattern.compile("[A-Za-z0-9_+=-]{1,256}");
    private static final Pattern ENC_QUERY_PATTERN = Pattern.compile("[?&]enc=([a-f0-9]{32})");
    private static final List<String> WORK_ENC_PAGE_BASES = List.of(
            "https://mooc1-2.chaoxing.com/mooc-ans/visit/stucoursemiddle",
            "https://mooc1-1.chaoxing.com/mooc-ans/visit/stucoursemiddle",
            "https://mooc1-1.chaoxing.com/visit/stucoursemiddle",
            "https://mooc2-ans.chaoxing.com/mooc2-ans/mycourse/studentcourse",
            "https://mooc1.chaoxing.com/mooc-ans/visit/stucoursemiddle");

    private final ChaoxingProperties properties;
    private final CourseJsonParser courseJsonParser;
    private final TaskListParser taskListParser;
    private final ObjectMapper objectMapper;
    private final ChaoxingHttpGateway httpGateway;
    private final Clock clock;
    private boolean authenticated;
    private PendingLogin pendingLogin;

    public ChaoxingClient(
            ChaoxingProperties properties,
            CourseJsonParser courseJsonParser,
            TaskListParser taskListParser,
            ObjectMapper objectMapper,
            ChaoxingHttpGateway httpGateway) {
        this(properties, courseJsonParser, taskListParser, objectMapper, httpGateway, Clock.systemUTC());
    }

    ChaoxingClient(
            ChaoxingProperties properties,
            CourseJsonParser courseJsonParser,
            TaskListParser taskListParser,
            ObjectMapper objectMapper,
            ChaoxingHttpGateway httpGateway,
            Clock clock) {
        this.properties = properties;
        this.courseJsonParser = courseJsonParser;
        this.taskListParser = taskListParser;
        this.objectMapper = objectMapper;
        this.httpGateway = httpGateway;
        this.clock = clock;
        this.authenticated = properties.getCookie() != null && !properties.getCookie().isBlank();
        log.info("chaoxing_client_init enabled={} authenticated={}",
                properties.isEnabled(), authenticated);
    }

    /**
     * 检查客户端是否可用。
     */
    public synchronized boolean isAvailable() {
        return properties.isEnabled() && authenticated;
    }

    /** Starts a new short-lived QR challenge and clears any previous account session. */
    public synchronized ChaoxingLoginChallenge startQrLogin() {
        if (!properties.isEnabled()) {
            throw new ChaoxingLoginException("学习通功能未启用。");
        }
        httpGateway.clearCookies();
        authenticated = false;
        pendingLogin = null;
        properties.setCookie(null);
        properties.setUid(null);
        properties.setEnc(null);
        properties.setStuenc(null);

        URI passportUri = requireHttpsUri(properties.getPassportUrl());
        String loginHtml = httpGateway.getText(passportUri, null);
        Document document = Jsoup.parse(loginHtml, passportUri.toString());
        Element qrImage = document.selectFirst("#quickCode[src]");
        String uuid = valueOf(document, "#uuid[value]");
        String enc = valueOf(document, "#enc[value]");
        if (qrImage == null || uuid.isBlank() || enc.isBlank()) {
            throw new ChaoxingLoginException("学习通登录页未提供可用二维码，请稍后重试。");
        }

        URI qrUri = passportUri.resolve(qrImage.attr("src"));
        byte[] qrCodePng = normalizeQrImage(httpGateway.getImage(qrUri, passportUri));
        Instant expiresAt = clock.instant().plus(properties.getLoginChallengeTimeout());
        pendingLogin = new PendingLogin(
                uuid,
                enc,
                valueOf(document, "#doubleFactorLogin[value]"),
                valueOf(document, "#forbidotherlogin[value]"),
                passportUri,
                expiresAt);
        log.info("chaoxing_qr_login_started expiresInSeconds={}",
                properties.getLoginChallengeTimeout().toSeconds());
        return new ChaoxingLoginChallenge(qrCodePng, expiresAt);
    }

    /** Checks the pending QR challenge without exposing its identifiers or cookies. */
    public synchronized ChaoxingLoginStatus refreshLoginStatus() {
        if (!properties.isEnabled()) {
            return ChaoxingLoginStatus.UNAVAILABLE;
        }
        if (authenticated) {
            return ChaoxingLoginStatus.AUTHENTICATED;
        }
        if (pendingLogin == null) {
            return ChaoxingLoginStatus.NOT_STARTED;
        }
        if (!clock.instant().isBefore(pendingLogin.expiresAt())) {
            pendingLogin = null;
            return ChaoxingLoginStatus.EXPIRED;
        }

        URI statusUri = pendingLogin.passportUri().resolve("/getauthstatus/v2");
        String response = httpGateway.postForm(statusUri, pendingLogin.passportUri(), Map.of(
                "enc", pendingLogin.enc(),
                "uuid", pendingLogin.uuid(),
                "doubleFactorLogin", pendingLogin.doubleFactorLogin(),
                "forbidotherlogin", pendingLogin.forbidOtherLogin()));
        try {
            JsonNode status = objectMapper.readTree(response);
            if (status.path("status").asBoolean(false)) {
                authenticated = true;
                pendingLogin = null;
                String responseUid = status.path("uid").asText("").strip();
                if (!responseUid.isBlank()) {
                    properties.setUid(responseUid);
                } else {
                    httpGateway.cookieValue("_uid").ifPresent(properties::setUid);
                }
                // 访问主站页面补全 cookie（uf、_d、vc3 等）
                enrichCookiesAfterLogin();
                log.info("chaoxing_qr_login_authenticated uidPresent={} cookieNames={}",
                        properties.getUid() != null && !properties.getUid().isBlank(),
                        httpGateway.allCookieNames());
                return ChaoxingLoginStatus.AUTHENTICATED;
            }
            int type = status.path("type").asInt(0);
            if (type == 4) {
                return ChaoxingLoginStatus.WAITING_CONFIRMATION;
            }
            if (type == 6 || type == 7) {
                pendingLogin = null;
                return ChaoxingLoginStatus.REJECTED;
            }
            return ChaoxingLoginStatus.WAITING_SCAN;
        } catch (Exception exception) {
            throw new ChaoxingLoginException("学习通返回了无法识别的登录状态。", exception);
        }
    }

    /**
     * 获取课程列表。
     *
     * @return 课程列表
     */
    public List<Course> fetchCourses() {
        if (!isAvailable()) {
            log.warn("chaoxing_client_not_available");
            throw new ChaoxingLoginException("尚未登录学习通，请先扫码登录。");
        }

        try {
            String json = fetchCourseListJson();
            JsonNode channelList = requireCourseListPayload(json);
            if (channelList.isEmpty()) {
                return Collections.emptyList();
            }
            List<Course> courses = LearningDeduplicator.deduplicateCourses(
                    courseJsonParser.parseCourseList(json));
            if (courses.isEmpty()) {
                throw new ChaoxingLoginException("学习通课程数据解析失败，请稍后重试。");
            }
            return courses;
        } catch (ChaoxingLoginException exception) {
            log.warn("chaoxing_fetch_courses_failed code=request_or_authentication_failed");
            throw exception;
        } catch (Exception exception) {
            log.error("chaoxing_fetch_courses_failed code=parse_failed", exception);
            throw new ChaoxingLoginException("学习通课程数据解析失败。", exception);
        }
    }

    /**
     * 获取课程详细信息（包含班级、日期等）。
     *
     * @return 课程详细信息列表
     */
    public List<CourseDetail> fetchCourseDetails() {
        if (!isAvailable()) {
            log.warn("chaoxing_client_not_available");
            throw new ChaoxingLoginException("尚未登录学习通，请先扫码登录。");
        }

        try {
            String json = fetchCourseListJson();
            JsonNode channelList = requireCourseListPayload(json);
            if (channelList.isEmpty()) {
                return Collections.emptyList();
            }
            List<CourseDetail> details = courseJsonParser.parseCourseDetails(json);
            if (details.isEmpty()) {
                throw new ChaoxingLoginException("学习通课程数据解析失败，请稍后重试。");
            }
            return details;
        } catch (ChaoxingLoginException exception) {
            log.warn("chaoxing_fetch_course_details_failed code=request_or_authentication_failed");
            throw exception;
        } catch (Exception exception) {
            log.error("chaoxing_fetch_course_details_failed code=parse_failed", exception);
            throw new ChaoxingLoginException("学习通课程详情解析失败。", exception);
        }
    }

    /**
     * 获取指定课程的任务列表。
     *
     * @param courseId 课程 ID
     * @param classId 班级 ID
     * @param cpi 课程专属 cpi
     * @param enc 加密参数
     * @return 任务列表
     */
    public List<CourseTask> fetchTasks(String courseId, String classId, String cpi, String enc) {
        if (!isAvailable()) {
            log.warn("chaoxing_client_not_available");
            throw new ChaoxingLoginException("尚未登录学习通，请先扫码登录。");
        }

        try {
            String html = fetchTaskListHtml(courseId, classId, cpi, enc);
            if (html == null || html.isBlank()) {
                return Collections.emptyList();
            }
            return LearningDeduplicator.deduplicateTasks(
                    taskListParser.parseTaskList(html, courseId, "chaoxing"));
        } catch (ChaoxingLoginException exception) {
            log.warn("chaoxing_fetch_tasks_failed code=request_or_authentication_failed");
            throw exception;
        } catch (IllegalArgumentException exception) {
            log.warn("chaoxing_fetch_tasks_failed code=invalid_identifier");
            throw new ChaoxingLoginException("学习通任务查询参数无效。", exception);
        } catch (Exception exception) {
            log.error("chaoxing_fetch_tasks_failed code=parse_failed", exception);
            throw new ChaoxingLoginException("学习通任务数据解析失败。", exception);
        }
    }

    /**
     * 获取任务列表 HTML。
     */
    private String fetchTaskListHtml(String courseId, String classId, String cpi, String enc) {
        String workEnc = fetchWorkEncFromCoursePage(courseId, classId, cpi);
        String effectiveEnc = !workEnc.isBlank() ? workEnc
                : ((enc != null && !enc.isBlank()) ? enc
                : (properties.getEnc() != null ? properties.getEnc() : ""));
        String stuenc = properties.getStuenc() != null ? properties.getStuenc() : "";

        log.info("chaoxing_fetch_task_list courseId={} encPresent={} stuencPresent={} workEncPresent={}",
                courseId, !effectiveEnc.isBlank(), !stuenc.isBlank(), !workEnc.isBlank());

        String url = buildTaskListUrl(courseId, classId, cpi, effectiveEnc, stuenc);
        String html = fetchTaskListPage(url, courseId);
        if (isBlockedTaskPage(html)) {
            log.warn("chaoxing_task_page_blocked courseId={} hasLoginForm={} hasNoPermission={} htmlLength={}",
                    courseId, hasLoginForm(html), hasNoPermission(html), html == null ? 0 : html.length());
            throw new ChaoxingLoginException(
                    "学习通课程任务列表当前无法访问（无权限或页面已变化），请稍后重试或重新扫码登录。");
        }
        return html;
    }

    /**
     * 构建学习通作业列表 URL。
     * 作业列表使用课程页面的 workEnc，而不是课程列表 JSON 中的 enc。
     */
    private String buildTaskListUrl(
            String courseId, String classId, String cpi, String enc, String stuenc) {
        StringBuilder urlBuilder = new StringBuilder(properties.getBaseUrl() + "/mooc2/work/list")
                .append("?courseId=").append(taskParameter(courseId, false))
                .append("&classId=").append(taskParameter(classId, false))
                .append("&cpi=").append(taskParameter(cpi, false))
                .append("&ut=s")
                .append("&t=").append(System.currentTimeMillis());
        if (stuenc != null && !stuenc.isBlank()) {
            urlBuilder.append("&stuenc=").append(taskParameter(stuenc, true));
        }
        if (enc != null && !enc.isBlank()) {
            urlBuilder.append("&enc=").append(taskParameter(enc, true));
        }
        return urlBuilder.toString();
    }

    private String fetchTaskListPage(String url, String courseId) {
        String html = httpGateway.getText(
                URI.create(url),
                URI.create(properties.getBaseUrl() + "/mycourse"));
        log.info("chaoxing_fetch_task_list_response courseId={} htmlLength={}",
                courseId, html == null ? 0 : html.length());
        return html;
    }

    private static boolean isBlockedTaskPage(String html) {
        return html == null || html.isBlank() || hasLoginForm(html) || hasNoPermission(html);
    }

    private static boolean hasLoginForm(String html) {
        if (html == null || html.isBlank()) {
            return false;
        }
        Document document = Jsoup.parse(html);
        return document.selectFirst("#quickCode, #phone, form[action*=login]") != null;
    }

    private static boolean hasNoPermission(String html) {
        return html != null && (html.contains("无权限") || html.contains("没有权限"));
    }

    /**
     * 从课程详情页提取作业列表专用的 workEnc。
     * workEnc 与课程列表 JSON 的 enc 不是同一个值，缺少它会导致作业列表返回“无权限”。
     */
    private String fetchWorkEncFromCoursePage(String courseId, String classId, String cpi) {
        for (String base : WORK_ENC_PAGE_BASES) {
            try {
                String url = base
                        + "?courseid=" + taskParameter(courseId, false)
                        + "&clazzid=" + taskParameter(classId, false)
                        + "&vc=1&cpi=" + taskParameter(cpi, false)
                        + "&ut=s&ismooc2=1&v=2";
                log.info("chaoxing_fetch_work_enc_url courseId={} base={}", courseId, base);
                String html = httpGateway.getText(
                        URI.create(url),
                        URI.create(properties.getBaseUrl() + "/mycourse"));
                if (html == null || html.isBlank() || hasLoginForm(html)) {
                    continue;
                }
                Document document = Jsoup.parse(html);
                Element workEncElement = document.selectFirst(
                        "input#workEnc[value], input[name=workEnc][value]");
                if (workEncElement != null) {
                    String value = workEncElement.attr("value").strip();
                    if (!value.isBlank()) {
                        log.info("chaoxing_work_enc_from_page courseId={} available=true", courseId);
                        return value;
                    }
                }
                Element workLink = document.selectFirst("a[title=作业][data-url]");
                if (workLink != null) {
                    String value = encFromQueryString(workLink.attr("data-url"));
                    if (value != null && !value.isBlank()) {
                        log.info("chaoxing_work_enc_from_link courseId={} available=true", courseId);
                        return value;
                    }
                }
                String scriptValue = extractFromScript(html, "workEnc");
                if (scriptValue != null) {
                    log.info("chaoxing_work_enc_from_script courseId={} available=true", courseId);
                    return scriptValue;
                }
                Element oldEncElement = document.selectFirst(
                        "input#oldenc[value], input[name=oldenc][value]");
                if (oldEncElement != null) {
                    String value = oldEncElement.attr("value").strip();
                    if (!value.isBlank()) {
                        log.info("chaoxing_work_enc_from_oldenc courseId={} available=true", courseId);
                        return value;
                    }
                }
                Element encElement = document.selectFirst(
                        "input#enc[value], input[name=enc][value]");
                if (encElement != null) {
                    String value = encElement.attr("value").strip();
                    if (!value.isBlank()) {
                        log.info("chaoxing_work_enc_from_enc courseId={} available=true", courseId);
                        return value;
                    }
                }
                String encScriptValue = extractFromScript(html, "enc");
                if (encScriptValue != null) {
                    log.info("chaoxing_work_enc_from_enc_script courseId={} available=true", courseId);
                    return encScriptValue;
                }
                log.debug("chaoxing_work_enc_not_found_in_page courseId={} htmlLength={}",
                        courseId, html.length());
            } catch (ChaoxingLoginException exception) {
                log.warn("chaoxing_work_enc_page_failed courseId={} base={}",
                        courseId, base, exception);
            }
        }
        return "";
    }

    private static String encFromQueryString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        java.util.regex.Matcher matcher = ENC_QUERY_PATTERN.matcher(value);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * 获取课程列表 JSON。
     * 学习通课程列表页面是 HTML，其中包含 JavaScript 变量存储课程数据。
     * 需要从 HTML 中提取 JSON 数据。
     */
    private String fetchCourseListJson() {
        // 先获取课程列表页面
        String url = properties.getBaseUrl() + "/mycourse";
        String html = httpGateway.getText(URI.create(url), URI.create(properties.getBaseUrl()));
        requireAuthenticatedPage(html);

        // 从 HTML 中提取课程数据 JSON
        String json = extractCourseJsonFromHtml(html);
        if (json == null || json.isBlank()) {
            throw new ChaoxingLoginException("学习通课程页面格式已变化，请稍后重试。");
        }
        return json;
    }

    private JsonNode requireCourseListPayload(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode channelList = root == null ? null : root.get("channelList");
            if (channelList == null || !channelList.isArray()) {
                throw new ChaoxingLoginException("学习通课程数据格式已变化，请稍后重试。");
            }
            return channelList;
        } catch (ChaoxingLoginException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ChaoxingLoginException("学习通课程数据格式已变化，请稍后重试。", exception);
        }
    }

    /**
     * 从 HTML 页面中提取课程 JSON 数据。
     * 学习通页面会在 JavaScript 中存储课程数据。
     */
    private String extractCourseJsonFromHtml(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }

        // 查找 channelList 相关的 JSON 数据
        // 学习通页面通常会在 script 标签或 data 属性中存储数据
        int startIndex = html.indexOf("\"channelList\"");
        if (startIndex == -1) {
            // 尝试其他可能的格式
            startIndex = html.indexOf("channelList");
        }

        if (startIndex == -1) {
            log.warn("chaoxing_no_channel_list_found");
            return null;
        }

        // 向前找到 JSON 对象的开始
        int jsonStart = html.lastIndexOf("{", startIndex);
        if (jsonStart == -1) {
            return null;
        }

        // 向后找到 JSON 对象的结束
        int braceCount = 0;
        int jsonEnd = -1;
        for (int i = jsonStart; i < html.length(); i++) {
            char c = html.charAt(i);
            if (c == '{') braceCount++;
            else if (c == '}') braceCount--;

            if (braceCount == 0) {
                jsonEnd = i + 1;
                break;
            }
        }

        if (jsonEnd == -1) {
            return null;
        }

        return html.substring(jsonStart, jsonEnd);
    }

    private static byte[] normalizeQrImage(byte[] bytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null
                    || image.getWidth() <= 0
                    || image.getHeight() <= 0
                    || image.getWidth() > MAX_QR_DIMENSION
                    || image.getHeight() > MAX_QR_DIMENSION) {
                throw new ChaoxingLoginException("学习通返回的二维码图片无效。");
            }
            try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                if (!ImageIO.write(image, "PNG", output)) {
                    throw new ChaoxingLoginException("学习通二维码无法转换为 PNG 图片。");
                }
                return output.toByteArray();
            }
        } catch (ChaoxingLoginException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ChaoxingLoginException("学习通返回的二维码图片无效。", exception);
        }
    }

    private static String valueOf(Document document, String selector) {
        Element element = document.selectFirst(selector);
        return element == null ? "" : element.attr("value").strip();
    }

    private static String taskParameter(String value, boolean allowEmpty) {
        String normalized = value == null ? "" : value.strip();
        if (normalized.isEmpty() && allowEmpty) {
            return "";
        }
        if (!TASK_IDENTIFIER_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("invalid learning task identifier");
        }
        return URLEncoder.encode(normalized, StandardCharsets.UTF_8);
    }

    private synchronized void enrichCookiesAfterLogin() {
        try {
            URI coursePage = URI.create(properties.getBaseUrl() + "/mycourse");
            String html = httpGateway.getText(coursePage, URI.create(properties.getBaseUrl()));
            if (html == null || html.isBlank()) {
                log.warn("chaoxing_enrich_empty_page");
                return;
            }
            log.info("chaoxing_enrich_page_loaded htmlLength={}", html.length());
            // 从 HTML 链接中提取 enc 和 stuenc
            extractEncFromPageLinks(html);
        } catch (Exception exception) {
            log.warn("chaoxing_cookie_enrich_failed", exception);
        }
    }

    private void extractEncFromPageLinks(String html) {
        // 匹配 enc= 和 stuenc= 在 URL 参数中的值
        java.util.regex.Pattern encPattern = java.util.regex.Pattern.compile(
                "[?&]enc=([a-f0-9]{32})");
        java.util.regex.Pattern stuencPattern = java.util.regex.Pattern.compile(
                "[?&]stuenc=([a-f0-9]{32})");
        java.util.regex.Matcher encMatcher = encPattern.matcher(html);
        if (encMatcher.find()) {
            properties.setEnc(encMatcher.group(1));
            log.info("chaoxing_enc_from_link available=true");
        }
        java.util.regex.Matcher stuencMatcher = stuencPattern.matcher(html);
        if (stuencMatcher.find()) {
            properties.setStuenc(stuencMatcher.group(1));
            log.info("chaoxing_stuenc_from_link available=true");
        }
        if (properties.getEnc() == null || properties.getEnc().isBlank()) {
            httpGateway.cookieValue("xxtenc").ifPresent(value -> properties.setEnc(value.strip()));
        }
        if (properties.getStuenc() == null || properties.getStuenc().isBlank()) {
            httpGateway.cookieValue("wfwEnc").ifPresent(value -> properties.setStuenc(value.strip()));
        }
        if (properties.getEnc() == null || properties.getEnc().isBlank()
                || properties.getStuenc() == null || properties.getStuenc().isBlank()) {
            log.debug("chaoxing_task_parameters_not_found_in_course_page htmlLength={}", html.length());
        }
    }

    private static String extractFromScript(String html, String varName) {
        // 匹配 var enc = "..." 或 enc: "..." 或 enc='...'
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                varName + "\\s*[=:]\\s*[\"']([a-f0-9]{32})[\"']");
        java.util.regex.Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private synchronized void requireAuthenticatedPage(String html) {
        Document document = Jsoup.parse(html == null ? "" : html);
        if (document.selectFirst("#quickCode, #phone, form[action*=login]") != null) {
            authenticated = false;
            throw new ChaoxingLoginException("学习通登录状态已失效，请重新扫码登录。");
        }
    }

    private static URI requireHttpsUri(String value) {
        try {
            URI uri = URI.create(value == null ? "" : value.strip());
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getUserInfo() != null) {
                throw new ChaoxingLoginException("学习通登录地址配置无效，必须是 HTTPS 地址。");
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw new ChaoxingLoginException("学习通登录地址配置无效，必须是 HTTPS 地址。", exception);
        }
    }

    private record PendingLogin(
            String uuid,
            String enc,
            String doubleFactorLogin,
            String forbidOtherLogin,
            URI passportUri,
            Instant expiresAt) {
    }
}
