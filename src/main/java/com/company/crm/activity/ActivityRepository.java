package com.company.crm.activity;import org.springframework.data.domain.*;import org.springframework.data.jpa.repository.*;import org.springframework.data.repository.query.Param;import java.util.*;
public interface ActivityRepository extends JpaRepository<Activity,UUID>{
 @org.springframework.data.jpa.repository.EntityGraph(attributePaths={"client","user"}) @Query("select x from Activity x where x.tenantId=:tenantId and x.client.id=:client and (:all=true or lower(x.client.owner.email)=lower(:email))")Page<Activity> byClient(@Param("tenantId")UUID tenantId,@Param("client")UUID client,@Param("all")boolean all,@Param("email")String email,Pageable p);
 @org.springframework.data.jpa.repository.EntityGraph(attributePaths={"client","user"}) @Query("select x from Activity x where x.tenantId=:tenantId and (:all=true or lower(x.client.owner.email)=lower(:email)) and (:type is null or x.type=:type) and (:q = '' or lower(x.subject) like lower(concat('%' ,:q,'%')))") Page<Activity> search(@Param("tenantId")UUID tenantId,@Param("all")boolean all,@Param("email")String email,@Param("type")ActivityType type,@Param("q")String q,Pageable p);
 @org.springframework.data.jpa.repository.EntityGraph(attributePaths={"client","user"}) @Query("select x from Activity x where x.tenantId=:tenantId and x.id=:id and (:all=true or lower(x.client.owner.email)=lower(:email))")Optional<Activity> visible(@Param("tenantId")UUID tenantId,@Param("id")UUID id,@Param("all")boolean all,@Param("email")String email);
}



