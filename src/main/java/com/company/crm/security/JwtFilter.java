package com.company.crm.security;

import com.company.crm.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
public class JwtFilter extends OncePerRequestFilter {
    private static final String WORKSPACE_LIST_PATH = "/api/v1/workspaces/mine";
    private final JwtService jwt;
    private final UserRepository users;
    private final JdbcTemplate jdbc;

    public JwtFilter(JwtService jwt, UserRepository users, JdbcTemplate jdbc) {
        this.jwt = jwt;
        this.users = users;
        this.jdbc = jdbc;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                var user = users.findByEmailIgnoreCase(jwt.subject(header.substring(7)))
                        .filter(candidate -> candidate.isActive() && candidate.isEmailVerified()).orElse(null);
                if (user != null) {
                    List<Membership> memberships = jdbc.query("""
                            select m.tenant_id, m.role
                            from tenant_memberships m
                            join tenants t on t.id=m.tenant_id
                            where m.user_id=? and m.membership_status='ACTIVE' and t.status='ACTIVE'
                            order by t.created_at, t.id
                            """, (rs, rowNum) -> new Membership(
                            rs.getObject("tenant_id", UUID.class), rs.getString("role")), user.getId());

                    String workspaceHeader = request.getHeader("X-Workspace-Id");
                    Membership selected = null;
                    if (workspaceHeader != null && !workspaceHeader.isBlank()) {
                        UUID requestedId;
                        try { requestedId = UUID.fromString(workspaceHeader); }
                        catch (IllegalArgumentException invalidId) {
                            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"status\":\"error\",\"message\":\"Invalid workspace selection\"}");
                            return;
                        }
                        selected = memberships.stream()
                                .filter(m -> m.tenantId().equals(requestedId))
                                .findFirst()
                                .orElse(null);
                        if (selected == null) {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"status\":\"error\",\"message\":\"Workspace membership is not active\"}");
                            return;
                        }
                    } else if (memberships.size() == 1) {
                        selected = memberships.get(0);
                    } else if (!memberships.isEmpty() && request.getRequestURI().equals(WORKSPACE_LIST_PATH)) {
                        TenantPrincipal principal = new TenantPrincipal(user.getId(), user.getEmail(), null, null);
                        SecurityContextHolder.getContext().setAuthentication(
                                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
                    } else if (!memberships.isEmpty()) {
                        response.setStatus(428);
                        response.setContentType("application/json");
                        response.getWriter().write("{\"status\":\"error\",\"message\":\"Select an active workspace\"}");
                        return;
                    }

                    if (selected != null) {
                        TenantContext.set(selected.tenantId());
                        TenantPrincipal principal = new TenantPrincipal(
                                user.getId(), user.getEmail(), selected.tenantId(), selected.role());
                        SecurityContextHolder.getContext().setAuthentication(
                                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
                    }
                }
            } catch (RuntimeException ignored) {
                TenantContext.clear();
                SecurityContextHolder.clearContext();
            }
        }
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private record Membership(UUID tenantId, String role) {}
}
