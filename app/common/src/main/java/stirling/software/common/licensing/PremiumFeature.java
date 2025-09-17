package stirling.software.common.licensing;

public enum PremiumFeature {
    SSO_AUTO_LOGIN(PremiumLicenseLevel.PRO),
    DATABASE_BACKUP(PremiumLicenseLevel.PRO),
    CUSTOM_METADATA(PremiumLicenseLevel.PRO),
    GOOGLE_DRIVE(PremiumLicenseLevel.PRO),
    AUDIT_LOGS(PremiumLicenseLevel.ENTERPRISE),
    PERSISTENT_METRICS(PremiumLicenseLevel.ENTERPRISE);

    private final PremiumLicenseLevel minimumLevel;

    PremiumFeature(PremiumLicenseLevel minimumLevel) {
        this.minimumLevel = minimumLevel;
    }

    public PremiumLicenseLevel getMinimumLevel() {
        return minimumLevel;
    }
}
