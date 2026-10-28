package com.company.crm.lead;import org.springframework.data.domain.*;import org.springframework.data.jpa.repository.*;import org.springframework.data.repository.query.Param;import java.util.*;
public interface LeadRepository extends JpaRepository<Lead,UUID>{
 @org.springframework.data.jpa.repository.EntityGraph(attributePaths={"assignedTo","client"}) @Query("select l from Lead l where l.tenantId=:tenantId and l.archivedAt is null and (:all=true or lower(l.assignedTo.email)=lower(:email)) and (:status is null or l.status=:status) and (:q = '' or lower(l.title) like lower(concat('%',:q,'%')))")Page<Lead> search(@Param("tenantId")UUID tenantId,@Param("all")boolean all,@Param("email")String email,@Param("status")LeadStatus status,@Param("q")String q,Pageable p);
 @org.springframework.data.jpa.repository.EntityGraph(attributePaths={"assignedTo","client"}) @Query("select l from Lead l where l.tenantId=:tenantId and l.id=:id and l.archivedAt is null and (:all=true or lower(l.assignedTo.email)=lower(:email))")Optional<Lead> visible(@Param("tenantId")UUID tenantId,@Param("id")UUID id,@Param("all")boolean all,@Param("email")String email);
}


