package stirling.software.SPDF;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

import io.github.pixee.security.SystemCommand;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import lombok.extern.slf4j.Slf4j;

import stirling.software.SPDF.UI.WebBrowser;
import stirling.software.common.configuration.AppConfig;
import stirling.software.common.configuration.ConfigInitializer;
import stirling.software.common.configuration.InstallationPathConfig;
import stirling.software.common.util.UrlUtils;

@Slf4j
@EnableScheduling
@SpringBootApplication(
        scanBasePackages = {
            "stirling.software.SPDF",
            "stirling.software.common",
            "stirling.software.proprietary"
        })
public class SPDFApplication {

    private static String serverPortStatic;
    private static String baseUrlStatic;
    private static String contextPathStatic;

    private final AppConfig appConfig;
    private final Environment env;
    private final WebBrowser webBrowser;

    public SPDFApplication(
            AppConfig appConfig,
            Environment env,
            @Autowired(required = false) WebBrowser webBrowser) {
        this.appConfig = appConfig;
        this.env = env;
        this.webBrowser = webBrowser;
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        SpringApplication app = new SpringApplication(SPDFApplication.class);

        Properties props = new Properties();

        boolean desktopMode = isDesktopMode();

        if (!desktopMode) {
            String configuredPort = System.getProperty("server.port");
            if (configuredPort == null || configuredPort.isBlank()) {
                String envPort = System.getenv("PORT");
                if (envPort != null && !envPort.isBlank()) {
                    props.put("server.port", envPort);
                    System.setProperty("server.port", envPort);
                    log.info("PORT environment variable detected. Binding server to {}", envPort);
                }
            }
        }

        if (desktopMode) {
            System.setProperty("java.awt.headless", "false");
            app.setHeadless(false);
            props.put("java.awt.headless", "false");
            props.put("spring.main.web-application-type", "servlet");

            int desiredPort = 8080;
            String port = UrlUtils.findAvailablePort(desiredPort);
            props.put("server.port", port);
            System.setProperty("server.port", port);
            log.info("Desktop UI mode: Using port {}", port);
        }

        app.setAdditionalProfiles(getActiveProfile(args));

        ConfigInitializer initializer = new ConfigInitializer();
        try {
            initializer.ensureConfigExists();
        } catch (IOException | URISyntaxException e) {
            log.error("Error initialising configuration", e);
        }
        Map<String, String> propertyFiles = new HashMap<>();

        // External config files
        Path settingsPath = Paths.get(InstallationPathConfig.getSettingsPath());
        log.info("Settings file: {}", settingsPath.toString());
        if (Files.exists(settingsPath)) {
            propertyFiles.put(
                    "spring.config.additional-location", "file:" + settingsPath.toString());
        } else {
            log.warn("External configuration file '{}' does not exist.", settingsPath.toString());
        }

        Path customSettingsPath = Paths.get(InstallationPathConfig.getCustomSettingsPath());
        log.info("Custom settings file: {}", customSettingsPath.toString());
        if (Files.exists(customSettingsPath)) {
            String existingLocation =
                    propertyFiles.getOrDefault("spring.config.additional-location", "");
            if (!existingLocation.isEmpty()) {
                existingLocation += ",";
            }
            propertyFiles.put(
                    "spring.config.additional-location",
                    existingLocation + "file:" + customSettingsPath.toString());
        } else {
            log.warn(
                    "Custom configuration file '{}' does not exist.",
                    customSettingsPath.toString());
        }
        Properties finalProps = new Properties();

        if (!propertyFiles.isEmpty()) {
            finalProps.putAll(
                    Collections.singletonMap(
                            "spring.config.additional-location",
                            propertyFiles.get("spring.config.additional-location")));
        }

        if (!props.isEmpty()) {
            finalProps.putAll(props);
        }
        app.setDefaultProperties(finalProps);

        app.run(args);

        // Ensure directories are created
        try {
            Files.createDirectories(Path.of(InstallationPathConfig.getTemplatesPath()));
            Files.createDirectories(Path.of(InstallationPathConfig.getStaticPath()));
        } catch (IOException e) {
            log.error("Error creating directories: {}", e.getMessage());
        }

        printStartupLogs();
    }

    @PostConstruct
    public void init() {
        String baseUrl = appConfig.getBaseUrl();
        String contextPath = appConfig.getContextPath();
        String serverPort = appConfig.getServerPort();
        baseUrlStatic = baseUrl;
        contextPathStatic = contextPath;
        serverPortStatic = serverPort;
        String url = baseUrl + ":" + getStaticPort() + contextPath;

        if (webBrowser != null && isDesktopMode()) {
            webBrowser.initWebUI(url);
        } else {
            String browserOpenEnv = env.getProperty("BROWSER_OPEN");
            boolean browserOpen = browserOpenEnv != null && "true".equalsIgnoreCase(browserOpenEnv);
            if (browserOpen) {
                try {
                    String os = System.getProperty("os.name").toLowerCase();
                    Runtime rt = Runtime.getRuntime();

                    if (os.contains("win")) {
                        // For Windows
                        SystemCommand.runCommand(rt, "rundll32 url.dll,FileProtocolHandler " + url);
                    } else if (os.contains("mac")) {
                        SystemCommand.runCommand(rt, "open " + url);
                    } else if (os.contains("nix") || os.contains("nux")) {
                        SystemCommand.runCommand(rt, "xdg-open " + url);
                    }
                } catch (IOException e) {
                    log.error("Error opening browser: {}", e.getMessage());
                }
            }
        }
    }

    public static void setServerPortStatic(String port) {
        if ("auto".equalsIgnoreCase(port)) {
            // Use Spring Boot's automatic port assignment (server.port=0)
            SPDFApplication.serverPortStatic =
                    "0"; // This will let Spring Boot assign an available port
        } else {
            SPDFApplication.serverPortStatic = port;
        }
    }

    @PreDestroy
    public void cleanup() {
        if (webBrowser != null) {
            webBrowser.cleanup();
        }
    }

    private static void printStartupLogs() {
        log.info("Stirling-PDF Started.");
        String url = baseUrlStatic + ":" + getStaticPort() + contextPathStatic;
        log.info("Navigate to {}", url);
    }

    private static boolean isDesktopMode() {
        String propertyValue = System.getProperty("STIRLING_PDF_DESKTOP_UI");
        if (propertyValue == null || propertyValue.isBlank()) {
            propertyValue = System.getenv().getOrDefault("STIRLING_PDF_DESKTOP_UI", "false");
        }
        return Boolean.parseBoolean(propertyValue);
    }

    private static String[] getActiveProfile(String[] args) {
        String[] profiles = extractProfilesFromArgs(args);
        if (profiles == null) {
            profiles = extractProfilesFromSystemProperty();
        }
        if (profiles == null) {
            profiles = extractProfilesFromEnvironment();
        }

        if (profiles == null || profiles.length == 0) {
            if (isSecurityAvailable()) {
                log.info("Additional features detected on classpath; enabling security profile");
                profiles = new String[] {"security"};
            } else {
                log.info("Without additional features in jar");
                profiles = new String[] {"default"};
            }
        }

        return normalizeProfiles(profiles);
    }

    private static String[] extractProfilesFromArgs(String[] args) {
        if (args == null) {
            return null;
        }
        return Arrays.stream(args)
                .filter(arg -> arg.startsWith("--spring.profiles.active="))
                .findFirst()
                .map(arg -> arg.substring(arg.indexOf('=') + 1).split(","))
                .orElse(null);
    }

    private static String[] extractProfilesFromSystemProperty() {
        String systemProfile = System.getProperty("spring.profiles.active");
        if (systemProfile == null || systemProfile.isBlank()) {
            return null;
        }
        return systemProfile.split(",");
    }

    private static String[] extractProfilesFromEnvironment() {
        String envProfile = System.getenv("SPRING_PROFILES_ACTIVE");
        if (envProfile == null || envProfile.isBlank()) {
            return null;
        }
        return envProfile.split(",");
    }

    private static String[] normalizeProfiles(String[] profiles) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String profile : profiles) {
            if (profile == null) {
                continue;
            }
            String trimmed = profile.trim();
            if (!trimmed.isEmpty()) {
                normalized.add(trimmed);
            }
        }

        if (normalized.isEmpty()) {
            return new String[] {"default"};
        }

        if (shouldEnableSecurity(normalized)) {
            log.info("Enabling 'security' profile alongside: {}", String.join(",", normalized));
            normalized.add("security");
        }

        return normalized.toArray(new String[0]);
    }

    private static boolean shouldEnableSecurity(Set<String> existingProfiles) {
        if (existingProfiles.stream().anyMatch("security"::equals)) {
            return false;
        }
        if (!isSecurityAvailable()) {
            return false;
        }
        if (isSecurityExplicitlyDisabled()) {
            log.info(
                    "Security features detected but DISABLE_ADDITIONAL_FEATURES is true;"
                            + " not activating 'security' profile");
            return false;
        }
        return true;
    }

    private static boolean isSecurityAvailable() {
        return isClassPresent(
                "stirling.software.proprietary.security.configuration.SecurityConfiguration");
    }

    private static boolean isSecurityExplicitlyDisabled() {
        String envFlag = System.getenv("DISABLE_ADDITIONAL_FEATURES");
        String sysProp = System.getProperty("DISABLE_ADDITIONAL_FEATURES");
        return "true".equalsIgnoreCase(envFlag) || "true".equalsIgnoreCase(sysProp);
    }

    private static boolean isClassPresent(String className) {
        try {
            Class.forName(className, false, SPDFApplication.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static String getStaticBaseUrl() {
        return baseUrlStatic;
    }

    public static String getStaticPort() {
        return serverPortStatic;
    }

    public static String getStaticContextPath() {
        return contextPathStatic;
    }
}
