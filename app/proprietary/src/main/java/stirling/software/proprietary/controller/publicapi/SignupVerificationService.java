package stirling.software.proprietary.controller.publicapi;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import stirling.software.common.model.ApplicationProperties;
import stirling.software.common.model.ApplicationProperties.Mail;
import stirling.software.proprietary.model.SignupVerificationToken;
import stirling.software.proprietary.model.Tenant;
import stirling.software.proprietary.model.dto.SignupRequest;
import stirling.software.proprietary.repository.SignupVerificationTokenRepository;

@Slf4j
@Component
@RequiredArgsConstructor
public class SignupVerificationService {

    private static final URI RESEND_ENDPOINT = URI.create("https://api.resend.com/emails");
    private static final Duration TOKEN_TTL = Duration.ofHours(24);

    private final ApplicationProperties applicationProperties;
    private final ObjectMapper objectMapper;
    private final SignupVerificationTokenRepository tokenRepository;
    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @Transactional
    public void enqueueVerification(SignupRequest request, Tenant tenant) {
        if (request == null || tenant == null) {
            return;
        }

        Mail mail = applicationProperties.getMail();
        if (mail == null
                || !mail.isEnabled()
                || !StringUtils.hasText(mail.getResendApiKey())
                || !StringUtils.hasText(mail.getFrom())) {
            log.info(
                    "Email disabled; skipping verification for tenant '{}' ({})",
                    tenant.getSlug(),
                    request.getAdminEmail());
            return;
        }

        purgeExpiredTokens();

        String token = UUID.randomUUID().toString();
        SignupVerificationToken verificationToken = new SignupVerificationToken();
        verificationToken.setToken(token);
        verificationToken.setTenantSlug(tenant.getSlug());
        verificationToken.setAdminEmail(request.getAdminEmail());
        verificationToken.setExpiresAt(Instant.now().plus(TOKEN_TTL));
        tokenRepository.save(verificationToken);

        try {
            sendEmail(mail, request, tenant, token);
        } catch (Exception ex) {
            log.warn(
                    "Failed sending verification email to {} for tenant {}: {}",
                    request.getAdminEmail(),
                    tenant.getSlug(),
                    ex.getMessage());
        }
    }

    @Transactional
    public VerificationResult verifyToken(String token) {
        purgeExpiredTokens();
        if (!StringUtils.hasText(token)) {
            return VerificationResult.error("Token is required");
        }
        String trimmedToken = token.trim();
        SignupVerificationToken stored = tokenRepository.findById(trimmedToken).orElse(null);
        if (stored == null) {
            return VerificationResult.error("Token not found or already used");
        }
        if (stored.getExpiresAt().isBefore(Instant.now())) {
            tokenRepository.deleteById(trimmedToken);
            return VerificationResult.error("Token has expired");
        }
        tokenRepository.deleteById(trimmedToken);
        return VerificationResult.success(stored.getTenantSlug(), stored.getAdminEmail());
    }

    private void sendEmail(Mail mail, SignupRequest request, Tenant tenant, String token)
            throws IOException, InterruptedException {
        String from = mail.getFrom();
        if (StringUtils.hasText(mail.getFromName())) {
            from = mail.getFromName() + " <" + from + ">";
        }

        String verificationLink = buildVerificationLink(mail, token);

        Map<String, Object> payload = new HashMap<>();
        payload.put("from", from);
        payload.put("to", List.of(request.getAdminEmail()));
        payload.put("subject", "Confirm your Stirling-PDF tenant");
        payload.put("html", buildHtmlBody(request, tenant, verificationLink));

        String json = toJson(payload);

        HttpRequest httpRequest =
                HttpRequest.newBuilder(RESEND_ENDPOINT)
                        .timeout(Duration.ofSeconds(10))
                        .header("Authorization", "Bearer " + mail.getResendApiKey())
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();

        HttpResponse<String> response =
                httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IOException(
                    "Resend API returned status " + response.statusCode() + ": " + response.body());
        }
        log.info(
                "Verification email dispatched via Resend for tenant '{}' to {}",
                tenant.getSlug(),
                request.getAdminEmail());
    }

    private String toJson(Map<String, Object> payload) throws JsonProcessingException {
        return objectMapper.writeValueAsString(payload);
    }

    private String buildHtmlBody(SignupRequest request, Tenant tenant, String verificationLink) {
        StringBuilder builder = new StringBuilder();
        builder.append("<h2>Welcome to Stirling-PDF</h2>");
        builder.append("<p>Hi ").append(request.getAdminEmail()).append(",</p>");
        builder.append("<p>Your tenant <strong>")
                .append(tenant.getDisplayName())
                .append("</strong> (slug: <code>")
                .append(tenant.getSlug())
                .append("</code>) has been created.</p>");
        if (verificationLink != null) {
            builder.append(
                            "<p>Please confirm your email address to activate the 14-day trial: "
                                    + "<a href=\"")
                    .append(verificationLink)
                    .append("\">Confirm email</a></p>");
        } else {
            builder.append("<p>Login with your admin account to start your 14-day trial.</p>");
        }
        builder.append("<p>If you did not request this, please ignore this message.</p>");
        builder.append("<p>— The Stirling-PDF team</p>");
        return builder.toString();
    }

    private String buildVerificationLink(Mail mail, String token) {
        if (!StringUtils.hasText(mail.getVerificationBaseUrl())) {
            return null;
        }
        String base = mail.getVerificationBaseUrl();
        if (!base.endsWith("/")) {
            base += "/";
        }
        return base + "verify?token=" + token;
    }

    private void purgeExpiredTokens() {
        Instant cutoff = Instant.now();
        tokenRepository.deleteExpired(cutoff);
    }

    public record VerificationResult(
            boolean success, String tenantSlug, String adminEmail, String error) {
        private static VerificationResult success(String slug, String email) {
            return new VerificationResult(true, slug, email, null);
        }

        private static VerificationResult error(String message) {
            return new VerificationResult(false, null, null, message);
        }
    }
}
