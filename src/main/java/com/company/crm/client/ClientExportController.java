package com.company.crm.client;

import com.company.crm.security.TenantContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RestController
public class ClientExportController {
    private static final Pattern FORMULA_PREFIX = Pattern.compile("^[\\s\\p{Cntrl}]*[=+\\-@]");
    private static final List<String> HEADERS = List.of(
            "Company", "Industry", "Email", "Phone", "Website", "Address",
            "Owner", "Status", "Created at", "Notes");

    private final JdbcTemplate jdbc;

    public ClientExportController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping(value = "/api/v1/clients/export", produces = "text/csv")
    public ResponseEntity<byte[]> export(Authentication authentication) {
        UUID tenantId = TenantContext.requireTenantId();
        boolean canSeeAll = authentication.getAuthorities().stream().anyMatch(authority ->
                authority.getAuthority().equals("ROLE_ADMIN")
                        || authority.getAuthority().equals("ROLE_MANAGER"));
        List<Map<String, Object>> clients = jdbc.queryForList("""
                select c.company_name, c.industry, c.email, c.phone, c.website, c.address,
                       u.full_name owner_name, c.status, c.created_at, c.notes
                from clients c
                join users u on u.id=c.owner_id
                where c.tenant_id=? and c.archived_at is null
                  and (? or lower(u.email)=lower(?))
                order by c.created_at desc, c.id
                """, tenantId, canSeeAll, authentication.getName());

        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append(HEADERS.stream().map(ClientExportController::csvField)
                .collect(Collectors.joining(","))).append("\r\n");
        for (Map<String, Object> row : clients) {
            csv.append(List.of(
                    value(row.get("company_name")), value(row.get("industry")),
                    value(row.get("email")), value(row.get("phone")), value(row.get("website")),
                    value(row.get("address")), value(row.get("owner_name")), value(row.get("status")),
                    value(row.get("created_at")), value(row.get("notes")))
                    .stream().map(ClientExportController::csvField)
                    .collect(Collectors.joining(","))).append("\r\n");
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"crm-clients.csv\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String value(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String csvField(String value) {
        String safe = value;
        if (FORMULA_PREFIX.matcher(safe).find()) {
            safe = "'" + safe;
        }
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }
}
