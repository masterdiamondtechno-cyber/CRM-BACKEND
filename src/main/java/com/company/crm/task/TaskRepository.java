package com.company.crm.task;

import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<Task, UUID> {
  @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"assignedTo", "client"})
  @Query(
      "select t from Task t where t.tenantId=:tenantId and t.archivedAt is null and (:all=true or"
          + " lower(t.assignedTo.email)=lower(:email) or lower(t.createdBy.email)=lower(:email))"
          + " and (:status is null or t.status=:status) and (:priority is null or"
          + " t.priority=:priority) and (:assignee is null or t.assignedTo.id=:assignee) and"
          + " (:hasDueFrom=false or t.dueAt>=:dueFrom) and (:hasDueTo=false or t.dueAt<:dueTo) and"
          + " (:overdue=false or (t.dueAt<:now and t.status in :openStatuses)) and (:q = '' or"
          + " lower(t.title) like lower(concat('%',:q,'%')) or lower(coalesce(t.description,''))"
          + " like lower(concat('%',:q,'%')))")
  Page<Task> search(
      @Param("tenantId") UUID tenantId,
      @Param("all") boolean all,
      @Param("email") String email,
      @Param("status") TaskStatus status,
      @Param("priority") TaskPriority priority,
      @Param("assignee") UUID assignee,
      @Param("hasDueFrom") boolean hasDueFrom,
      @Param("dueFrom") Instant dueFrom,
      @Param("hasDueTo") boolean hasDueTo,
      @Param("dueTo") Instant dueTo,
      @Param("overdue") boolean overdue,
      @Param("now") Instant now,
      @Param("openStatuses") Collection<TaskStatus> openStatuses,
      @Param("q") String q,
      Pageable p);

  @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"assignedTo", "client"})
  @Query(
      "select t from Task t where t.tenantId=:tenantId and t.id=:id and t.archivedAt is null and"
          + " (:all=true or lower(t.assignedTo.email)=lower(:email) or"
          + " lower(t.createdBy.email)=lower(:email))")
  Optional<Task> visible(
      @Param("tenantId") UUID tenantId,
      @Param("id") UUID id,
      @Param("all") boolean all,
      @Param("email") String email);
}
