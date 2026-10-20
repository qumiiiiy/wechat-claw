package com.weida.bot.wechatilinkbot.infrastructure.learning;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SyntheticLearningPlatformTest {

    private static Platform platform;
    private static HttpClient client;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @BeforeAll
    static void startPlatform() throws IOException {
        platform = new Platform();
        platform.start();
        client = HttpClient.newHttpClient();
    }

    @AfterAll
    static void stopPlatform() {
        if (platform != null) {
            platform.stop();
        }
    }

    @Test
    void platformStartsAndStops() {
        assertThat(platform.getPort()).isGreaterThan(0);
        assertThat(platform.getBaseUrl()).contains("localhost");
    }

    @Test
    void courseListPageReturnsHtml() throws Exception {
        HttpResponse<String> response = get("/");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("高等数学");
        assertThat(response.body()).contains("大学英语");
        assertThat(response.body()).contains("计算机网络");
    }

    @Test
    void mycoursePageReturnsHtml() throws Exception {
        HttpResponse<String> response = get("/mycourse");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("课程");
    }

    @Test
    void loginWaitingPageReturnsHtml() throws Exception {
        HttpResponse<String> response = get("/login");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("扫码登录");
    }

    @Test
    void loginCompletePageReturnsHtml() throws Exception {
        HttpResponse<String> response = get("/login/complete");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("登录成功");
    }

    @Test
    void courseDetailPageReturnsHtml() throws Exception {
        HttpResponse<String> response = get("/course/100001");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("合成课程");
    }

    @Test
    void chapterPageReturnsHtml() throws Exception {
        HttpResponse<String> response = get("/chapter/1");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("绪论");
    }

    @Test
    void chapterIframePageReturnsHtml() throws Exception {
        HttpResponse<String> response = get("/chapter/2/iframe");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("数据链路层");
    }

    @Test
    void singleChoiceQuestionPage() throws Exception {
        HttpResponse<String> response = get("/question/single");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("单选题");
        assertThat(response.body()).contains("OSI");
    }

    @Test
    void multipleChoiceQuestionPage() throws Exception {
        HttpResponse<String> response = get("/question/multiple");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("多选题");
    }

    @Test
    void trueFalseQuestionPage() throws Exception {
        HttpResponse<String> response = get("/question/truefalse");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("判断题");
    }

    @Test
    void fillBlankQuestionPage() throws Exception {
        HttpResponse<String> response = get("/question/fillblank");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("填空题");
    }

    @Test
    void unknownTypeQuestionPage() throws Exception {
        HttpResponse<String> response = get("/question/unknown");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("连线题");
    }

    @Test
    void asyncLoadedQuestionPage() throws Exception {
        HttpResponse<String> response = get("/question/async");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("子网掩码");
    }

    @Test
    void emptyStemQuestionPage() throws Exception {
        HttpResponse<String> response = get("/question/empty-stem");
        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    void duplicateOptionsQuestionPage() throws Exception {
        HttpResponse<String> response = get("/question/dup-options");
        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    void noIdQuestionPage() throws Exception {
        HttpResponse<String> response = get("/question/no-id");
        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    void domChangedQuestionPage() throws Exception {
        HttpResponse<String> response = get("/question/dom-changed");
        assertThat(response.statusCode()).isEqualTo(200);
    }

    // --- 答案动作测试 ---

    @Test
    void selectActionCorrectAnswer() throws Exception {
        platform.clearAnswerRecords();
        String body = OBJECT_MAPPER.writeValueAsString(Map.of(
                "action", "SELECT",
                "questionFingerprint", "sha256:SC-001",
                "selectedOptions", new String[]{"C"}));

        HttpResponse<String> response = post("/api/answer", body);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("CORRECT");
    }

    @Test
    void selectActionWrongAnswer() throws Exception {
        platform.clearAnswerRecords();
        String body = OBJECT_MAPPER.writeValueAsString(Map.of(
                "action", "SELECT",
                "questionFingerprint", "sha256:SC-001",
                "selectedOptions", new String[]{"A"}));

        HttpResponse<String> response = post("/api/answer", body);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("WRONG");
    }

    @Test
    void fillActionCorrectAnswer() throws Exception {
        platform.clearAnswerRecords();
        String body = OBJECT_MAPPER.writeValueAsString(Map.of(
                "action", "FILL",
                "questionFingerprint", "sha256:FB-001",
                "fillText", "6 表示"));

        HttpResponse<String> response = post("/api/answer", body);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("CORRECT");
    }

    @Test
    void fillActionWrongAnswer() throws Exception {
        platform.clearAnswerRecords();
        String body = OBJECT_MAPPER.writeValueAsString(Map.of(
                "action", "FILL",
                "questionFingerprint", "sha256:FB-001",
                "fillText", "错误答案"));

        HttpResponse<String> response = post("/api/answer", body);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("WRONG");
    }

    @Test
    void continueActionReturnsSubmitted() throws Exception {
        platform.clearAnswerRecords();
        String body = OBJECT_MAPPER.writeValueAsString(Map.of(
                "action", "CONTINUE",
                "questionFingerprint", "sha256:VIDEO-001"));

        HttpResponse<String> response = post("/api/answer", body);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("SUBMITTED");
    }

    @Test
    void unknownActionReturnsBlocked() throws Exception {
        platform.clearAnswerRecords();
        String body = OBJECT_MAPPER.writeValueAsString(Map.of(
                "action", "SCRIPT_INJECT",
                "questionFingerprint", "sha256:xxx"));

        HttpResponse<String> response = post("/api/answer", body);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("BLOCKED");
    }

    @Test
    void answerRecordsAreTracked() throws Exception {
        platform.clearAnswerRecords();
        String body = OBJECT_MAPPER.writeValueAsString(Map.of(
                "action", "SELECT",
                "questionFingerprint", "sha256:TRACK-001",
                "selectedOptions", new String[]{"B"}));

        post("/api/answer", body);
        assertThat(platform.getAnswerRecords()).hasSize(1);
        assertThat(platform.getAnswerRecords().get(0).action()).isEqualTo("SELECT");
    }

    @Test
    void answerEndpointRejectsGet() throws Exception {
        HttpResponse<String> response = get("/api/answer");
        assertThat(response.statusCode()).isEqualTo(405);
    }

    @Test
    void answerEndpointRejectsInvalidJson() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(platform.getBaseUrl() + "/api/answer"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("not-json"))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(400);
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(platform.getBaseUrl() + path))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(platform.getBaseUrl() + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * 本机合成学习平台。
     * 使用 JDK 内置 HttpServer 提供课程列表、登录、章节和五类互动题页面。
     * 仅用于测试，运行在 localhost 随机端口。
     */
    static class Platform {

        private static final ObjectMapper MAPPER = new ObjectMapper();

        private HttpServer server;
        private int port;
        private final List<AnswerRecord> answerRecords = new ArrayList<>();

        void start() throws IOException {
            server = HttpServer.create(new InetSocketAddress(0), 0);
            port = server.getAddress().getPort();

            server.createContext("/", this::handleCourseList);
            server.createContext("/mycourse", this::handleCourseList);
            server.createContext("/login", this::handleLoginWaiting);
            server.createContext("/login/complete", this::handleLoginComplete);
            server.createContext("/course", this::handleCourse);
            server.createContext("/chapter", this::handleChapter);
            server.createContext("/question", this::handleQuestion);
            server.createContext("/api/answer", this::handleAnswer);

            server.setExecutor(Executors.newFixedThreadPool(2));
            server.start();
        }

        void stop() {
            if (server != null) {
                server.stop(0);
            }
        }

        int getPort() {
            return port;
        }

        String getBaseUrl() {
            return "http://localhost:" + port;
        }

        List<AnswerRecord> getAnswerRecords() {
            return List.copyOf(answerRecords);
        }

        void clearAnswerRecords() {
            answerRecords.clear();
        }

        private void handleCourseList(HttpExchange exchange) throws IOException {
            sendHtml(exchange, 200, loadFixture("course-list.html"));
        }

        private void handleLoginWaiting(HttpExchange exchange) throws IOException {
            sendHtml(exchange, 200, loadFixture("login-waiting.html"));
        }

        private void handleLoginComplete(HttpExchange exchange) throws IOException {
            sendHtml(exchange, 200, loadFixture("login-complete.html"));
        }

        private void handleCourse(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String html = """
                    <!DOCTYPE html>
                    <html>
                    <head><title>课程详情</title></head>
                    <body>
                    <div class="course-detail">
                        <h1>合成课程 %s</h1>
                        <div class="chapter-list">
                            <a href="/chapter/1">第一章 绪论</a>
                            <a href="/chapter/2">第二章 数据链路层</a>
                            <a href="/chapter/3">第三章 网络层</a>
                        </div>
                    </div>
                    </body>
                    </html>
                    """.formatted(extractId(path));
            sendHtml(exchange, 200, html);
        }

        private void handleChapter(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String id = extractId(path);

            if (path.endsWith("/iframe")) {
                sendHtml(exchange, 200, loadFixture("chapter-iframe-content.html"));
                return;
            }

            String fixtureName = switch (id) {
                case "1" -> "chapter-with-video.html";
                case "2" -> "chapter-iframe.html";
                default -> "chapter-with-video.html";
            };
            sendHtml(exchange, 200, loadFixture(fixtureName));
        }

        private void handleQuestion(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String type = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : "";

            String fixtureName = switch (type) {
                case "single" -> "question-single-choice.html";
                case "multiple" -> "question-multiple-choice.html";
                case "truefalse" -> "question-true-false.html";
                case "fillblank" -> "question-fill-blank.html";
                case "unknown" -> "question-unknown-type.html";
                case "async" -> "question-async-loaded.html";
                case "empty-stem" -> "question-empty-stem.html";
                case "dup-options" -> "question-duplicate-options.html";
                case "no-id" -> "question-no-stable-id.html";
                case "dom-changed" -> "question-dom-changed.html";
                default -> "question-unknown-type.html";
            };

            sendHtml(exchange, 200, loadFixture(fixtureName));
        }

        private void handleAnswer(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, Map.of("error", "Method not allowed"));
                return;
            }

            String body;
            try (InputStream is = exchange.getRequestBody()) {
                body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }

            com.fasterxml.jackson.databind.JsonNode node;
            try {
                node = MAPPER.readTree(body);
            } catch (Exception e) {
                sendJson(exchange, 400, Map.of("error", "Invalid JSON"));
                return;
            }

            String action = node.has("action") ? node.get("action").asText("") : "";
            String fingerprint = node.has("questionFingerprint") ? node.get("questionFingerprint").asText("") : "";

            AnswerRecord record;
            Map<String, Object> response;

            switch (action) {
                case "SELECT" -> {
                    List<String> selected = new ArrayList<>();
                    if (node.has("selectedOptions")) {
                        for (com.fasterxml.jackson.databind.JsonNode opt : node.get("selectedOptions")) {
                            selected.add(opt.asText());
                        }
                    }
                    boolean correct = isCorrectAnswer(fingerprint, selected);
                    record = new AnswerRecord(action, fingerprint, selected, "", correct ? "CORRECT" : "WRONG");
                    response = Map.of(
                            "result", correct ? "CORRECT" : "WRONG",
                            "message", correct ? "答案正确" : "答案错误");
                }
                case "FILL" -> {
                    String fillText = node.has("fillText") ? node.get("fillText").asText("") : "";
                    boolean correct = isCorrectFill(fingerprint, fillText);
                    record = new AnswerRecord(action, fingerprint, List.of(), fillText, correct ? "CORRECT" : "WRONG");
                    response = Map.of(
                            "result", correct ? "CORRECT" : "WRONG",
                            "message", correct ? "填写正确" : "填写错误");
                }
                case "CONTINUE" -> {
                    record = new AnswerRecord(action, fingerprint, List.of(), "", "SUBMITTED");
                    response = Map.of("result", "SUBMITTED", "message", "已继续");
                }
                default -> {
                    record = new AnswerRecord(action, fingerprint, List.of(), "", "BLOCKED");
                    response = Map.of("result", "BLOCKED", "message", "不支持的动作类型: " + action);
                }
            }

            answerRecords.add(record);
            sendJson(exchange, 200, response);
        }

        private boolean isCorrectAnswer(String fingerprint, List<String> selected) {
            if (fingerprint.contains("SC") || selected.size() == 1) {
                return selected.contains("C");
            }
            return selected.contains("A") && selected.contains("B") && selected.contains("E") && selected.size() == 3;
        }

        private boolean isCorrectFill(String fingerprint, String fillText) {
            return fillText.contains("6") || fillText.contains("表示");
        }

        private String extractId(String path) {
            String[] parts = path.split("/");
            return parts.length > 0 ? parts[parts.length - 1] : "";
        }

        private void sendHtml(HttpExchange exchange, int status, String html) throws IOException {
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }

        private void sendJson(HttpExchange exchange, int status, Map<String, Object> json) throws IOException {
            byte[] bytes = MAPPER.writeValueAsBytes(json);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }

        private String loadFixture(String name) {
            try (InputStream is = getClass().getClassLoader().getResourceAsStream("learning/fixtures/" + name)) {
                if (is == null) {
                    return "<html><body><p>Fixture not found: " + name + "</p></body></html>";
                }
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                return "<html><body><p>Error loading fixture: " + name + "</p></body></html>";
            }
        }

        record AnswerRecord(
                String action,
                String fingerprint,
                List<String> selectedOptions,
                String fillText,
                String result) {
        }
    }
}
