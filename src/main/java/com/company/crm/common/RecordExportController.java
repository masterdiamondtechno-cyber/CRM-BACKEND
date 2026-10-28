package com.company.crm.common;

import com.company.crm.lead.LeadStatus;
import com.company.crm.security.TenantContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
public class RecordExportController {
    private static final int MAX_ROWS = 10_000;
    private final JdbcTemplate jdbc;

    public RecordExportController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping(value = "/api/v1/contacts/export", produces = "text/csv")
    public ResponseEntity<byte[]> contacts(@RequestParam(required = false) String search, Authentication auth) {
        boolean all = canSeeAll(auth);
        String query = search == null ? "" : search;
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select c.full_name,c.job_title,c.email,c.phone,c.is_primary,c.notes,
                       cl.company_name as client_name,u.full_name as client_owner
                from contacts c join clients cl on cl.id=c.client_id and cl.tenant_id=c.tenant_id
                join users u on u.id=cl.owner_id
                where c.tenant_id=? and c.archived_at is null and cl.archived_at is null
                  and (? or lower(u.email)=lower(?))
                  and (? = '' or lower(c.full_name) like lower('%' || ? || '%')
                       or lower(coalesce(c.email,'')) like lower('%' || ? || '%'))
                order by c.full_name,c.id limit ?
                """, TenantContext.requireTenantId(), all, auth.getName(), query, query, query, MAX_ROWS + 1);
        return csv("crm-contacts.csv", List.of("Name", "Job title", "Email", "Phone", "Primary", "Notes", "Client", "Client owner"), rows,
                List.of("full_name", "job_title", "email", "phone", "is_primary", "notes", "client_name", "client_owner"));
    }

    @GetMapping(value = "/api/v1/leads/export", produces = "text/csv")
    public ResponseEntity<byte[]> leads(@RequestParam(required = false) String search,
                                        @RequestParam(required = false) LeadStatus status,
                                        Authentication auth) {
        boolean all = canSeeAll(auth);
        StringBuilder sql = new StringBuilder("""
                select l.title,l.description,l.source,l.status,l.estimated_value,l.final_value,
                       l.expected_close_date,u.full_name as assignee,cl.company_name as client_name
                from leads l join users u on u.id=l.assigned_to
                left join clients cl on cl.id=l.client_id and cl.tenant_id=l.tenant_id
                where l.tenant_id=? and l.archived_at is null and (? or lower(u.email)=lower(?))
                """);
        ArrayList<Object> args = new ArrayList<>(List.of(TenantContext.requireTenantId(), all, auth.getName()));
        if (status != null) { sql.append(" and l.status=?"); args.add(status.name()); }
        if (search != null && !search.isBlank()) {
            sql.append(" and lower(l.title) like lower(?)"); args.add("%" + search + "%");
        }
        sql.append(" order by l.created_at desc,l.id limit ?"); args.add(MAX_ROWS + 1);
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        return csv("crm-leads.csv", List.of("Title", "Description", "Source", "Status", "Estimated value", "Final value", "Expected close", "Assignee", "Client"), rows,
                List.of("title", "description", "source", "status", "estimated_value", "final_value", "expected_close_date", "assignee", "client_name"));
    }

    private boolean canSeeAll(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_OWNER")
                || a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_MANAGER"));
    }

    private ResponseEntity<byte[]> csv(String filename, List<String> headers, List<Map<String, Object>> rows, List<String> fields) {
        if (rows.size() > MAX_ROWS) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Export exceeds the 10,000 row limit; narrow your search.");
        StringBuilder body = new StringBuilder("\uFEFF");
        body.append(headers.stream().map(CrmCsv::field).collect(Collectors.joining(","))).append("\r\n");
        for (Map<String, Object> row : rows) {
            body.append(fields.stream().map(key -> CrmCsv.field(row.get(key))).collect(Collectors.joining(","))).append("\r\n");
        }
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(body.toString().getBytes(StandardCharsets.UTF_8));
    }


}

