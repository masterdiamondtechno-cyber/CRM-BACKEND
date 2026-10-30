package com.company.crm.contact;

import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ContactRepository extends JpaRepository<Contact, UUID> {
  @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "client")
  @Query(
      "select c from Contact c where c.tenantId=:tenantId and c.archivedAt is null and"
          + " c.client.id=:client and (:all=true or lower(c.client.owner.email)=lower(:email))")
  Page<Contact> byClient(
      @Param("tenantId") UUID tenantId,
      @Param("client") UUID client,
      @Param("all") boolean all,
      @Param("email") String email,
      Pageable p);

  @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "client")
  @Query(
      "select c from Contact c where c.tenantId=:tenantId and c.archivedAt is null and (:all=true"
          + " or lower(c.client.owner.email)=lower(:email)) and (:q = '' or lower(c.fullName) like"
          + " lower(concat('%',:q,'%')) or lower(coalesce(c.email,'')) like"
          + " lower(concat('%',:q,'%')))")
  Page<Contact> search(
      @Param("tenantId") UUID tenantId,
      @Param("all") boolean all,
      @Param("email") String email,
      @Param("q") String q,
      Pageable p);

  @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "client")
  @Query(
      "select c from Contact c where c.tenantId=:tenantId and c.id=:id and c.archivedAt is null and"
          + " (:all=true or lower(c.client.owner.email)=lower(:email))")
  Optional<Contact> visible(
      @Param("tenantId") UUID tenantId,
      @Param("id") UUID id,
      @Param("all") boolean all,
      @Param("email") String email);
}
