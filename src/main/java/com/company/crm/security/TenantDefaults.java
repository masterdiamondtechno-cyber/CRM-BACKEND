package com.company.crm.security;

import java.util.UUID;

/** Compatibility identifier for the single workspace created by migration V5. */
public final class TenantDefaults {
    public static final UUID INITIAL_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private TenantDefaults() {}
}
