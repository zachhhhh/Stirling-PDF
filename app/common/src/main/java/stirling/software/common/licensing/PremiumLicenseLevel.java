package stirling.software.common.licensing;

public enum PremiumLicenseLevel {
    COMMUNITY,
    PRO,
    ENTERPRISE;

    public boolean atLeast(PremiumLicenseLevel required) {
        return this.ordinal() >= required.ordinal();
    }
}
