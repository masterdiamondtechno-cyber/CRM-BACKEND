package com.company.crm.settings;

import com.company.crm.common.ApiResponse;
import com.company.crm.security.TenantContext;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/settings/company")
public class CompanySettingsController {
  private final JdbcTemplate db;

  public CompanySettingsController(JdbcTemplate d) {
    db = d;
  }

  @GetMapping
  public ApiResponse<Map<String, Object>> get() {
    return ApiResponse.ok(
        "Company settings",
        db.queryForMap(
            "select"
                + " company_name,email,phone,address,default_currency,time_zone,default_page_size,updated_at"
                + " from company_settings where tenant_id=?",
            TenantContext.requireTenantId()));
  }

  @PatchMapping
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<Map<String, Object>> update(@RequestBody Map<String, Object> r) {
    String name = (String) r.getOrDefault("companyName", "IT Company");
    String email = (String) r.get("email");
    String phone = (String) r.get("phone");
    String address = (String) r.get("address");
    String currency = (String) r.getOrDefault("defaultCurrency", "INR");
    String zone = (String) r.getOrDefault("timeZone", "Asia/Kolkata");
    Integer size =
        r.containsKey("defaultPageSize")
            ? Integer.valueOf(r.get("defaultPageSize").toString())
            : 20;
    if (size < 1 || size > 100)
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.BAD_REQUEST, "defaultPageSize must be 1..100");
    db.update(
        "update company_settings set"
            + " company_name=?,email=?,phone=?,address=?,default_currency=?,time_zone=?,default_page_size=?,updated_at=now()"
            + " where tenant_id=?",
        name,
        email,
        phone,
        address,
        currency,
        zone,
        size,
        TenantContext.requireTenantId());
    return get();
  }
}
