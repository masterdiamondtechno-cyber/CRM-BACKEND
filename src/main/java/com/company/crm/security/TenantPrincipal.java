package com.company.crm.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** User identity plus a workspace selected from active server-verified memberships. */
public record TenantPrincipal(UUID userId, String username, UUID tenantId, String tenantRole)
        implements UserDetails {
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (tenantRole == null || tenantRole.isBlank()) return List.of();
        String authority = switch (tenantRole) {
            case "OWNER", "ADMIN" -> "ROLE_ADMIN";
            case "MANAGER" -> "ROLE_MANAGER";
            case "EMPLOYEE" -> "ROLE_EMPLOYEE";
            default -> throw new IllegalStateException("Unsupported tenant membership role");
        };
        return List.of(new SimpleGrantedAuthority(authority));
    }

    @Override public String getPassword() { return ""; }
    @Override public String getUsername() { return username; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}
