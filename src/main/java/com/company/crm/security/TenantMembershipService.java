package com.company.crm.security;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TenantMembershipService {
  private final JdbcTemplate jdbc;

  public TenantMembershipService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public boolean isActiveMember(UUID tenantId, UUID userId) {
    Integer count =
        jdbc.queryForObject(
            "select count(*) from tenant_memberships where tenant_id=? and user_id=? and"
                + " membership_status='ACTIVE'",
            Integer.class,
            tenantId,
            userId);
    return count != null && count == 1;
  }

  public void requireActiveMember(UUID userId) {
    if (!isActiveMember(TenantContext.requireTenantId(), userId)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "User is not an active member of this workspace");
    }
  }

  public void addInitialMembership(UUID userId, String role) {
    jdbc.update(
        """
        insert into tenant_memberships(tenant_id,user_id,role,membership_status)
        values(?,?,?,'ACTIVE')
        on conflict (tenant_id,user_id) do update
          set role=excluded.role, membership_status='ACTIVE'
        """,
        TenantContext.requireTenantId(),
        userId,
        role);
  }

  public void addBootstrapMembership(UUID userId) {
    jdbc.update(
        """
        insert into tenant_memberships(tenant_id,user_id,role,membership_status)
        values(?,?,case when exists (
            select 1 from tenant_memberships where tenant_id=? and role='OWNER'
        ) then 'ADMIN' else 'OWNER' end,'ACTIVE')
        on conflict (tenant_id,user_id) do nothing
        """,
        TenantDefaults.INITIAL_TENANT_ID,
        userId,
        TenantDefaults.INITIAL_TENANT_ID);
  }
}
