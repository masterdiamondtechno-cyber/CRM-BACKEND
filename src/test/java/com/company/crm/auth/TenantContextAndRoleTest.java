package com.company.crm.auth;

import static org.junit.jupiter.api.Assertions.*;

import com.company.crm.security.TenantContext;
import com.company.crm.security.TenantPrincipal;
import com.company.crm.workspace.InvitationController;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TenantContextAndRoleTest {
  @AfterEach
  void clearTenantContext() {
    TenantContext.clear();
  }

  @Test
  void tenantContextIsExplicitAndClearedBetweenRequests() {
    UUID tenantId = UUID.randomUUID();
    assertThrows(IllegalStateException.class, TenantContext::requireTenantId);
    TenantContext.set(tenantId);
    assertEquals(tenantId, TenantContext.requireTenantId());
    TenantContext.clear();
    assertThrows(IllegalStateException.class, TenantContext::requireTenantId);
  }

  @Test
  void tenantMembershipRoleDefinesRequestAuthorities() {
    UUID userId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    assertEquals(
        "ROLE_ADMIN",
        authority(new TenantPrincipal(userId, "owner@example.test", tenantId, "OWNER")));
    assertEquals(
        "ROLE_MANAGER",
        authority(new TenantPrincipal(userId, "manager@example.test", tenantId, "MANAGER")));
    assertEquals(
        "ROLE_EMPLOYEE",
        authority(new TenantPrincipal(userId, "employee@example.test", tenantId, "EMPLOYEE")));
  }

  @Test
  void workspaceListingPrincipalHasNoTenantAuthority() {
    TenantPrincipal principal =
        new TenantPrincipal(UUID.randomUUID(), "user@example.test", null, null);
    assertTrue(principal.getAuthorities().isEmpty());
  }

  @Test
  void invitationTokenStorageUsesAStableSha256Digest() {
    String token = "high-entropy-one-time-token";
    String first = InvitationController.hash(token);
    String second = InvitationController.hash(token);
    assertEquals(64, first.length());
    assertTrue(first.matches("[0-9a-f]{64}"));
    assertEquals(first, second);
    assertNotEquals(token, first);
  }

  private String authority(TenantPrincipal principal) {
    return principal.getAuthorities().iterator().next().getAuthority();
  }
}
