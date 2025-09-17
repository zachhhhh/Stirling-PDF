package stirling.software.proprietary.service.exception;

public class TenantQuotaExceededException extends RuntimeException {

    private final int limit;
    private final long attempted;

    public TenantQuotaExceededException(int limit, long attempted) {
        super("Tenant operation quota exceeded");
        this.limit = limit;
        this.attempted = attempted;
    }

    public int getLimit() {
        return limit;
    }

    public long getAttempted() {
        return attempted;
    }
}
