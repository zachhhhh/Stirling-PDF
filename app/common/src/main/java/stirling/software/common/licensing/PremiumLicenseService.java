package stirling.software.common.licensing;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;

import stirling.software.common.model.ApplicationProperties;
import stirling.software.common.model.ApplicationProperties.Premium;
import stirling.software.common.model.ApplicationProperties.Premium.EnterpriseFeatures;
import stirling.software.common.model.ApplicationProperties.Premium.EnterpriseFeatures.Audit;
import stirling.software.common.model.ApplicationProperties.Premium.EnterpriseFeatures.PersistentMetrics;
import stirling.software.common.model.ApplicationProperties.Premium.ProFeatures;
import stirling.software.common.model.ApplicationProperties.Premium.ProFeatures.CustomMetadata;
import stirling.software.common.model.ApplicationProperties.Premium.ProFeatures.GoogleDrive;

@Component
@RequiredArgsConstructor
public class PremiumLicenseService {

    private final ApplicationProperties applicationProperties;

    public PremiumLicenseStatus getCurrentLicenseStatus() {
        Premium premium = applicationProperties.getPremium();
        if (premium == null || !premium.isEnabled()) {
            return PremiumLicenseStatus.communityFallback();
        }

        PremiumLicenseLevel level = resolveLevel(premium);
        Set<PremiumFeature> features = resolveFeatures(premium);
        boolean licenseVerified = isLikelyVerified(premium);
        int maxUsers = Math.max(0, premium.getMaxUsers());

        return PremiumLicenseStatus.builder()
                .level(level)
                .licenseConfigured(true)
                .licenseVerified(licenseVerified)
                .maxUsers(maxUsers)
                .expiresAt(resolveExpiry())
                .enabledFeatures(Collections.unmodifiableSet(features))
                .build();
    }

    public PremiumLicenseLevel getCurrentLevel() {
        return getCurrentLicenseStatus().getLevel();
    }

    public boolean isFeatureEnabled(PremiumFeature feature) {
        PremiumLicenseStatus status = getCurrentLicenseStatus();
        if (!status.getLevel().atLeast(feature.getMinimumLevel())) {
            return false;
        }
        return status.isFeatureEnabled(feature);
    }

    private PremiumLicenseLevel resolveLevel(Premium premium) {
        EnterpriseFeatures enterprise = premium.getEnterpriseFeatures();
        if (enterprise != null) {
            Audit audit = enterprise.getAudit();
            PersistentMetrics metrics = enterprise.getPersistentMetrics();
            if ((audit != null && audit.isEnabled()) || (metrics != null && metrics.isEnabled())) {
                return PremiumLicenseLevel.ENTERPRISE;
            }
        }

        return PremiumLicenseLevel.PRO;
    }

    private Set<PremiumFeature> resolveFeatures(Premium premium) {
        EnumSet<PremiumFeature> features = EnumSet.noneOf(PremiumFeature.class);
        ProFeatures pro = premium.getProFeatures();
        if (pro != null) {
            if (pro.isSsoAutoLogin()) {
                features.add(PremiumFeature.SSO_AUTO_LOGIN);
            }
            if (pro.isDatabase()) {
                features.add(PremiumFeature.DATABASE_BACKUP);
            }
            CustomMetadata customMetadata = pro.getCustomMetadata();
            if (customMetadata != null) {
                if (customMetadata.isAutoUpdateMetadata()
                        || StringUtils.hasText(customMetadata.getAuthor())
                        || StringUtils.hasText(customMetadata.getCreator())
                        || StringUtils.hasText(customMetadata.getProducer())) {
                    features.add(PremiumFeature.CUSTOM_METADATA);
                }
            }
            GoogleDrive googleDrive = pro.getGoogleDrive();
            if (googleDrive != null && googleDrive.isEnabled()) {
                features.add(PremiumFeature.GOOGLE_DRIVE);
            }
        }

        EnterpriseFeatures enterprise = premium.getEnterpriseFeatures();
        if (enterprise != null) {
            Audit audit = enterprise.getAudit();
            if (audit != null && audit.isEnabled()) {
                features.add(PremiumFeature.AUDIT_LOGS);
            }
            PersistentMetrics metrics = enterprise.getPersistentMetrics();
            if (metrics != null && metrics.isEnabled()) {
                features.add(PremiumFeature.PERSISTENT_METRICS);
            }
        }

        return features;
    }

    private boolean isLikelyVerified(Premium premium) {
        return premium.isEnabled() && StringUtils.hasText(premium.getKey());
    }

    private Instant resolveExpiry() {
        // License expiry data will be populated once backend verification propagates metadata.
        return null;
    }
}
