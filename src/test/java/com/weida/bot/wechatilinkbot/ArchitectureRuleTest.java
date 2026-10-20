package com.weida.bot.wechatilinkbot;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ArchitectureRuleTest {

    private static final Path SOURCE_ROOT = Path.of(
            "src", "main", "java", "com", "weida", "bot", "wechatilinkbot");
    private static final Set<String> MODULE_SET = Set.of(
            "agent",
            "capability",
            "channel",
            "infrastructure",
            "model",
            "persistence",
            "tool");
    private static final Set<String> REMOVED_PACKAGE_SET = Set.of(
            "config",
            "constant",
            "controller",
            "dao",
            "dataobject",
            "dto",
            "enums",
            "exception",
            "lifecycle",
            "manager",
            "service",
            "vo");

    @Test
    void exposesOnlyTheSevenModularMonolithResponsibilities() throws IOException {
        assertThat(javaSources(SOURCE_ROOT).stream()
                .map(SOURCE_ROOT::relativize)
                .filter(path -> path.getNameCount() > 1)
                .map(path -> path.getName(0).toString())
                .distinct())
                .containsExactlyInAnyOrderElementsOf(MODULE_SET)
                .doesNotContainAnyElementsOf(REMOVED_PACKAGE_SET);
    }

    @Test
    void confinesWechatSdkTypesToTheIlinkAdapter() throws IOException {
        for (Path source : javaSources(SOURCE_ROOT)) {
            String normalizedPath = source.toString().replace('\\', '/');
            if (!normalizedPath.contains("/channel/ilink/")) {
                assertThat(Files.readString(source))
                        .as("wechat SDK leaked into %s", source)
                        .doesNotContain("com.github.wechat.ilink");
            }
        }
    }

    @Test
    void confinesDashScopeSdkTypesToInfrastructure() throws IOException {
        for (Path source : javaSources(SOURCE_ROOT)) {
            String normalizedPath = source.toString().replace('\\', '/');
            if (!normalizedPath.contains("/infrastructure/")) {
                assertThat(Files.readString(source))
                        .as("DashScope SDK leaked into %s", source)
                        .doesNotContain("com.alibaba.dashscope");
            }
        }
    }

    @Test
    void usesExactlyOneAiSdk() throws IOException {
        String pom = Files.readString(Path.of("pom.xml"));
        assertThat(pom)
                .contains("<artifactId>dashscope-sdk-java</artifactId>")
                .doesNotContain(
                        "spring-ai",
                        "langchain4j",
                        "spring-ai-alibaba");
        for (Path source : javaSources(SOURCE_ROOT)) {
            assertThat(Files.readString(source))
                    .doesNotContain("org.springframework.ai", "dev.langchain4j");
        }
    }

    @Test
    void learningAutomationUsesTheDefaultBrowserWithoutPlaywright() throws IOException {
        assertThat(Files.readString(Path.of("pom.xml")))
                .doesNotContain("com.microsoft.playwright", "playwright.version");
        for (Path source : javaSources(SOURCE_ROOT)) {
            assertThat(Files.readString(source))
                    .as("Playwright must not reintroduce an isolated learning browser in %s", source)
                    .doesNotContain("com.microsoft.playwright", "Playwright.create");
        }
    }

    @Test
    void enforcesStableModuleDependencyBoundaries() throws IOException {
        assertModuleDoesNotImport("model", internalModulesExcept("model"));
        assertModuleDoesNotImport(
                "persistence",
                ".agent.",
                ".capability.",
                ".channel.",
                ".infrastructure.",
                ".tool.");
        assertModuleDoesNotImport(
                "capability",
                ".agent.",
                ".channel.",
                ".tool.");
        assertCapabilityUsesOnlyApprovedPersistenceBoundaries();
        assertModuleDoesNotImport(
                "tool",
                ".agent.",
                ".channel.",
                ".infrastructure.",
                ".persistence.");
        assertModuleDoesNotImport(
                "agent",
                ".channel.",
                ".infrastructure.");
        assertChannelUsesOnlyInboundLedgerBoundary();
        assertModuleDoesNotImport(
                "infrastructure",
                ".channel.",
                ".persistence.");
        assertInfrastructureUsesOnlyApprovedCapabilityPorts();
    }

    @Test
    void keepsMyBatisSqlInThePersistenceAdapterXml() throws IOException {
        Path mapper = Path.of(
                "src", "main", "resources", "mapper", "ConversationMessageMapper.xml");
        Path inboundLedgerMapper = Path.of(
                "src", "main", "resources", "mapper", "InboundMessageLedgerMapper.xml");
        assertThat(mapper).exists();
        assertThat(inboundLedgerMapper).exists();
        assertThat(Files.readString(mapper))
                .contains("persistence.mybatis.ConversationMessageMapper")
                .contains("<select id=\"listRecent\"")
                .contains("<select id=\"findSummary\"")
                .contains("<insert id=\"upsertSummary\"")
                .contains("<insert id=\"insert\"")
                .doesNotContain("SELECT *");
        assertThat(Files.readString(inboundLedgerMapper))
                .contains("persistence.mybatis.InboundMessageLedgerMapper")
                .contains("<insert id=\"claim\"")
                .contains("<update id=\"complete\"")
                .contains("<update id=\"fail\"")
                .doesNotContain("SELECT *");

        for (Path source : javaSources(SOURCE_ROOT.resolve("persistence"))) {
            assertThat(Files.readString(source))
                    .doesNotContain("@Select", "@Insert", "@Update", "@Delete");
        }
    }

    @Test
    void testClassNamesUseTheSingularTestSuffix() throws IOException {
        for (Path source : javaSources(Path.of("src", "test", "java"))) {
            assertThat(source.getFileName().toString()).endsWith("Test.java");
        }
    }

    @Test
    void requiresTrustedInboundContextForEveryIlinkSdkSend() throws IOException {
        Path channelRoot = SOURCE_ROOT.resolve("channel");
        Path replySender = channelRoot.resolve(Path.of("ilink", "IlinkReplySender.java"));
        Path replyContext = channelRoot.resolve(Path.of("ilink", "IlinkReplyContext.java"));
        Path registry = channelRoot.resolve(Path.of("ilink", "IlinkSessionRegistry.java"));
        Path publicChannel = channelRoot.resolve("WechatChannel.java");
        Path proactiveController = channelRoot.resolve(
                Path.of("web", "WechatMessageController.java"));

        assertThat(replySender).exists();
        assertThat(replyContext).exists();
        assertThat(Files.readString(replySender))
                .contains("IlinkReplyContext")
                .doesNotContain("WeixinMessage sourceMessage");
        assertThat(Files.readString(registry))
                .doesNotContain(
                        "public void sendText(",
                        "public void sendImage(",
                        "public void sendFile(",
                        "public void sendVoice(",
                        "public void sendVideo(");
        assertThat(Files.readString(publicChannel))
                .doesNotContain(
                        "void sendText(",
                        "void sendImage(",
                        "void sendFile(",
                        "void sendVoice(",
                        "void sendVideo(");
        assertThat(proactiveController).doesNotExist();
    }

    private static String[] internalModulesExcept(String allowedModule) {
        return MODULE_SET.stream()
                .filter(module -> !module.equals(allowedModule))
                .map(module -> "." + module + ".")
                .toArray(String[]::new);
    }

    private static void assertModuleDoesNotImport(
            String module,
            String... forbiddenImports) throws IOException {
        for (Path source : javaSources(SOURCE_ROOT.resolve(module))) {
            assertThat(Files.readString(source))
                    .as("forbidden dependency in %s", source)
                    .doesNotContain(forbiddenImports);
        }
    }

    private static void assertCapabilityUsesOnlyApprovedPersistenceBoundaries()
            throws IOException {
        Path desktopTaskSource = SOURCE_ROOT.resolve(
                Path.of("capability", "DesktopTaskCapability.java"));
        Path studentTodoSource = SOURCE_ROOT.resolve(
                Path.of("capability", "StudentTodoCapability.java"));
        for (Path source : javaSources(SOURCE_ROOT.resolve("capability"))) {
            String content = Files.readString(source);
            if (source.equals(desktopTaskSource)) {
                String withoutApprovedImport = content.replace(
                        "import com.weida.bot.wechatilinkbot.persistence.DesktopTaskRepository;",
                        "");
                assertThat(content)
                        .as("desktop task persistence boundary in %s", source)
                        .contains("com.weida.bot.wechatilinkbot.persistence.DesktopTaskRepository")
                        .doesNotContain("com.weida.bot.wechatilinkbot.persistence.mybatis.");
                assertThat(withoutApprovedImport)
                        .as("only DesktopTaskRepository may cross the persistence boundary in %s", source)
                        .doesNotContain(".persistence.");
            } else if (source.equals(studentTodoSource)) {
                String withoutApprovedImport = content.replace(
                        "import com.weida.bot.wechatilinkbot.persistence.StudentTodoMemory;",
                        "");
                assertThat(content)
                        .as("student todo persistence boundary in %s", source)
                        .contains("com.weida.bot.wechatilinkbot.persistence.StudentTodoMemory")
                        .doesNotContain("com.weida.bot.wechatilinkbot.persistence.mybatis.");
                assertThat(withoutApprovedImport)
                        .as("only StudentTodoMemory may cross the persistence boundary in %s", source)
                        .doesNotContain(".persistence.");
            } else {
                assertThat(content)
                        .as("forbidden persistence dependency in %s", source)
                        .doesNotContain(".persistence.");
            }
        }
    }

    private static void assertChannelUsesOnlyInboundLedgerBoundary() throws IOException {
        String inboundLedgerImport =
                "import com.weida.bot.wechatilinkbot.persistence.InboundMessageLedger;";
        String persistenceExceptionImport =
                "import com.weida.bot.wechatilinkbot.persistence.ConversationPersistenceException;";
        for (Path source : javaSources(SOURCE_ROOT.resolve("channel"))) {
            String content = Files.readString(source);
            String withoutApprovedImports = content
                    .replace(inboundLedgerImport, "")
                    .replace(persistenceExceptionImport, "");
            if (content.contains(inboundLedgerImport)) {
                assertThat(content)
                        .as("inbound ledger boundary in %s", source)
                        .contains(inboundLedgerImport);
            }
            assertThat(withoutApprovedImports)
                    .as("channel may only use approved persistence boundaries in %s", source)
                    .doesNotContain(".persistence.");
        }
    }

    private static void assertInfrastructureUsesOnlyApprovedCapabilityPorts()
            throws IOException {
        String approvedImport =
                "import com.weida.bot.wechatilinkbot.capability.EmailActionClassifierPort;";
        String approvedExceptionImport =
                "import com.weida.bot.wechatilinkbot.capability.EmailClassificationUnavailableException;";
        for (Path source : javaSources(SOURCE_ROOT.resolve("infrastructure"))) {
            String content = Files.readString(source);
            String withoutApprovedImport = content
                    .replace(approvedImport, "")
                    .replace(approvedExceptionImport, "");
            assertThat(withoutApprovedImport)
                    .as("infrastructure may only import the approved semantic capability "
                            + "port in %s", source)
                    .doesNotContain(".capability.");
        }
    }

    private static List<Path> javaSources(Path root) throws IOException {
        if (!Files.exists(root)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(root)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();
        }
    }
}
