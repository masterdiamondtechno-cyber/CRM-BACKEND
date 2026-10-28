package com.company.crm.search;

import com.company.crm.common.ApiResponse;
import com.company.crm.security.TenantContext;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/search")
@Validated
public class GlobalSearchController {
    private static final String RESULT_SQL = """
            select entity, id, title, subtitle from (
              select 'clients' entity, c.id, c.company_name title, coalesce(c.industry,'Client') subtitle
                from clients c join users u on u.id=c.owner_id
               where c.tenant_id=? and c.archived_at is null and (? or lower(u.email)=lower(?))
                 and (lower(c.company_name) like ? escape chr(92) or lower(coalesce(c.email,'')) like ? escape chr(92))
              union all
              select 'contacts', c.id, c.full_name, coalesce(c.job_title,'Contact')
                from contacts c join clients cl on cl.tenant_id=c.tenant_id and cl.id=c.client_id
                join users u on u.id=cl.owner_id
               where c.tenant_id=? and c.archived_at is null and cl.archived_at is null
                 and (? or lower(u.email)=lower(?))
                 and (lower(c.full_name) like ? escape chr(92) or lower(coalesce(c.email,'')) like ? escape chr(92))
              union all
              select 'leads', l.id, l.title, l.status
                from leads l join users u on u.id=l.assigned_to
               where l.tenant_id=? and l.archived_at is null and (? or lower(u.email)=lower(?))
                 and (lower(l.title) like ? escape chr(92) or lower(coalesce(l.source,'')) like ? escape chr(92))
              union all
              select 'tasks', t.id, t.title, t.status
                from tasks t join users u on u.id=t.assigned_to join users creator on creator.id=t.created_by
               where t.tenant_id=? and t.archived_at is null and (? or lower(u.email)=lower(?) or lower(creator.email)=lower(?))
                 and (lower(t.title) like ? escape chr(92) or lower(coalesce(t.description,'')) like ? escape chr(92))
              union all
              select 'projects', p.id, p.name, p.status
                from projects p join users u on u.id=p.owner_id
                join clients c on c.tenant_id=p.tenant_id and c.id=p.client_id
               where p.tenant_id=? and p.archived_at is null
                 and (? or lower(u.email)=lower(?) or exists (
                   select 1 from project_members pm join users mu on mu.id=pm.user_id
                    where pm.tenant_id=p.tenant_id and pm.project_id=p.id and lower(mu.email)=lower(?)
                 )) and (lower(p.name) like ? escape chr(92) or lower(coalesce(p.description,'')) like ? escape chr(92) or lower(c.company_name) like ? escape chr(92))
            ) matches order by lower(title), entity limit 50
            """;

    private final JdbcTemplate jdbc;

    public GlobalSearchController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private boolean canSeeAll(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(role ->
                role.getAuthority().equals("ROLE_ADMIN") || role.getAuthority().equals("ROLE_MANAGER"));
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> search(
            @RequestParam @NotBlank @Size(max = 100) String q, Authentication auth) {
        UUID tenantId = TenantContext.requireTenantId();
        String email = auth.getName();
        boolean all = canSeeAll(auth);
        String term = "%" + q.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        List<Map<String, Object>> results = jdbc.queryForList(RESULT_SQL,
                tenantId, all, email, term, term,
                tenantId, all, email, term, term,
                tenantId, all, email, term, term,
                tenantId, all, email, email, term, term,
                tenantId, all, email, email, term, term, term);
        return ApiResponse.ok("Search results", results);
    }
}




