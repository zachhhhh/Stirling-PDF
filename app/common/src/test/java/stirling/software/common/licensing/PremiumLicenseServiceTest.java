package stirling.software.common.licensing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import stirling.software.common.model.ApplicationProperties;

class PremiumLicenseServiceTest {

    @Test
    void communityLicenseWhenPremiumDisabled() {
        ApplicationProperties properties = new ApplicationProperties();
        properties.getPremium().setEnabled(false);

        PremiumLicenseService service = new PremiumLicenseService(properties);
        PremiumLicenseStatus status = service.getCurrentLicenseStatus();

        assertEquals(PremiumLicenseLevel.COMMUNITY, status.getLevel());
        assertFalse(status.isFeatureEnabled(PremiumFeature.SSO_AUTO_LOGIN));
        assertFalse(status.isLicenseConfigured());
    }

    @Test
    void proLicenseWhenProFeaturesEnabled() {
        ApplicationProperties properties = new ApplicationProperties();
        properties.getPremium().setEnabled(true);
        properties.getPremium().setKey("demo-key");
        properties.getPremium().setMaxUsers(25);
        properties.getPremium().getProFeatures().setDatabase(true);
        properties.getPremium().getProFeatures().setSsoAutoLogin(true);

        PremiumLicenseService service = new PremiumLicenseService(properties);
        PremiumLicenseStatus status = service.getCurrentLicenseStatus();

        assertEquals(PremiumLicenseLevel.PRO, status.getLevel());
        assertTrue(status.isLicenseConfigured());
        assertTrue(status.isLicenseVerified());
        assertEquals(25, status.getMaxUsers());
        assertTrue(status.isFeatureEnabled(PremiumFeature.DATABASE_BACKUP));
        assertTrue(status.isFeatureEnabled(PremiumFeature.SSO_AUTO_LOGIN));
        assertFalse(status.isFeatureEnabled(PremiumFeature.AUDIT_LOGS));
    }

    @Test
    void enterpriseLicenseWhenEnterpriseFlagsPresent() {
        ApplicationProperties properties = new ApplicationProperties();
        properties.getPremium().setEnabled(true);
        properties.getPremium().setKey("demo-key");
        properties.getPremium().getEnterpriseFeatures().getAudit().setEnabled(true);

        PremiumLicenseService service = new PremiumLicenseService(properties);
        PremiumLicenseStatus status = service.getCurrentLicenseStatus();

        assertEquals(PremiumLicenseLevel.ENTERPRISE, status.getLevel());
        assertTrue(status.isFeatureEnabled(PremiumFeature.AUDIT_LOGS));
        assertFalse(status.isFeatureEnabled(PremiumFeature.PERSISTENT_METRICS));
    }
}
