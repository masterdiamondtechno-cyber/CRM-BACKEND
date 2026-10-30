package com.company.crm.client;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClientRepository extends JpaRepository<Client, UUID>, ClientRepositoryCustom {
  @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "owner")
  @Query(
      "select c from Client c where c.tenantId=:tenantId and c.archivedAt is null and c.id=:id "
          + "and (:all=true or lower(c.owner.email)=lower(:email))")
  Optional<Client> findVisible(
      @Param("tenantId") UUID tenantId,
      @Param("id") UUID id,
      @Param("all") boolean all,
      @Param("email") String email);
}
