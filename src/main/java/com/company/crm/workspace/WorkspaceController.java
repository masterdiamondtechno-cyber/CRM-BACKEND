package com.company.crm.workspace;

import com.company.crm.common.ApiResponse;
import com.company.crm.security.TenantPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces")
public class WorkspaceController {
    private final JdbcTemplate jdbc;

    public WorkspaceController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record WorkspaceView(UUID id, String name, String slug, String role) {}

    @GetMapping("/mine")
    public ApiResponse<List<WorkspaceView>> mine(@AuthenticationPrincipal TenantPrincipal principal) {
        List<WorkspaceView> workspaces = jdbc.query("""
                select t.id, t.name, t.slug, m.role
                from tenant_memberships m
                join tenants t on t.id=m.tenant_id
                where m.user_id=? and m.membership_status='ACTIVE' and t.status='ACTIVE'
                order by t.created_at, t.id
                """, (rs, row) -> new WorkspaceView(
                rs.getObject("id", UUID.class), rs.getString("name"),
                rs.getString("slug"), rs.getString("role")), principal.userId());
        return ApiResponse.ok("Workspaces", workspaces);
    }
}
