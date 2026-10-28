package com.company.crm.client;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.UUID;

public interface ClientRepositoryCustom {
    Page<Client> search(UUID tenantId, boolean all, String email, String search,
                        ClientStatus status, UUID owner, Instant from, Instant to, Pageable pageable);
}
