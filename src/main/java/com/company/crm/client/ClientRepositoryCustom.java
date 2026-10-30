package com.company.crm.client;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ClientRepositoryCustom {
  Page<Client> search(
      UUID tenantId,
      boolean all,
      String email,
      String search,
      ClientStatus status,
      UUID owner,
      Instant from,
      Instant to,
      Pageable pageable);
}
