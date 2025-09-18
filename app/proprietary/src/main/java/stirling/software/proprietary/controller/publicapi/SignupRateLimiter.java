package stirling.software.proprietary.controller.publicapi;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;

import stirling.software.proprietary.model.SignupThrottleEntry;
import stirling.software.proprietary.repository.SignupThrottleEntryRepository;

@Slf4j
@Component
public class SignupRateLimiter {

    private final Duration window;
    private final SignupThrottleEntryRepository repository;

    public SignupRateLimiter(
            @Value("${saas.signup.rate-limit-window:PT30S}") Duration window,
            SignupThrottleEntryRepository repository) {
        this.window = window.isNegative() || window.isZero() ? Duration.ofSeconds(30) : window;
        this.repository = repository;
    }

    @Transactional
    public boolean tryAcquire(String key) {
        if (key == null || key.isBlank()) {
            key = "unknown";
        }
        Instant now = Instant.now();
        String normalizedKey = normalizeKey(key);

        return repository
                .findByClientKeyForUpdate(normalizedKey)
                .map(
                        entry -> {
                            if (Duration.between(entry.getLastAttempt(), now).compareTo(window)
                                    >= 0) {
                                entry.setLastAttempt(now);
                                repository.save(entry);
                                return true;
                            }
                            return false;
                        })
                .orElseGet(
                        () -> {
                            SignupThrottleEntry entry = new SignupThrottleEntry();
                            entry.setClientKey(normalizedKey);
                            entry.setLastAttempt(now);
                            repository.save(entry);
                            return true;
                        });
    }

    public void cleanup() {
        Instant cutoff = Instant.now().minus(window);
        int purged = repository.deleteOlderThan(cutoff);
        if (purged > 0 && log.isDebugEnabled()) {
            log.debug("Purged {} stale signup throttle entries", purged);
        }
    }

    private String normalizeKey(String key) {
        String normalized = key.trim().toLowerCase();
        if (normalized.length() > 120) {
            normalized = normalized.substring(0, 120);
        }
        return normalized;
    }
}
