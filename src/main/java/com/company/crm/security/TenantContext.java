package com.company.crm.security;

import java.util.UUID;

/** Request-scoped tenant identity populated only after membership verification. */
public final class TenantContext {
    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(UUID tenantId) {
        if (tenantId == null) throw new IllegalArgumentException("tenantId is required");
        CURRENT.set(tenantId);
    }

    public static UUID currentTenantId() { return CURRENT.get(); }

    public static UUID requireTenantId() {
        UUID tenantId = CURRENT.get();
        if (tenantId == null) throw new IllegalStateException("No verified tenant is bound to this request");
        return tenantId;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
