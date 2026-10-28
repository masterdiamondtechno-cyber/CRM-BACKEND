package com.company.crm.audit;
import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.security.core.Authentication;import org.springframework.stereotype.Service;import com.company.crm.security.TenantContext;import java.util.UUID;
@Service public class AuditService{
 private final JdbcTemplate db;public AuditService(JdbcTemplate d){db=d;}
 public void record(Authentication actor,String action,String type,UUID entity){UUID id=db.queryForObject("select id from users where lower(email)=lower(?)",UUID.class,actor.getName());db.update("insert into audit_logs(id,tenant_id,actor_id,action,entity_type,entity_id,details) values(?,?,?,?,?,?, '{}'::jsonb)",UUID.randomUUID(),TenantContext.requireTenantId(),id,action,type,entity);}
}
