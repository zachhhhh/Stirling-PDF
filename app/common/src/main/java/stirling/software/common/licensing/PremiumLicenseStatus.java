package stirling.software.common.licensing;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PremiumLicenseStatus {

    private final PremiumLicenseLevel level;
    private final boolean licenseConfigured;
    private final boolean licenseVerified;
    private final int maxUsers;
    private final Instant expiresAt;
    private final Set<PremiumFeature> enabledFeatures;

    public boolean isFeatureEnabled(PremiumFeature feature) {
        return enabledFeatures != null && enabledFeatures.contains(feature);
    }

    public static PremiumLicenseStatus communityFallback() {
        return PremiumLicenseStatus.builder()
                .level(PremiumLicenseLevel.COMMUNITY)
                .licenseConfigured(false)
                .licenseVerified(false)
                .maxUsers(0)
                .expiresAt(null)
                .enabledFeatures(Collections.unmodifiableSet(EnumSet.noneOf(PremiumFeature.class)))
                .build();
    }
}
