package com.company.crm.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);

    @Query(value = """
            select u.* from users u
            where exists (
                select 1 from tenant_memberships m
                where m.user_id=u.id and m.tenant_id=:tenantId
                  and m.membership_status=case when :active is false then 'SUSPENDED' else 'ACTIVE' end
                  and (:role is null or m.role=:role)
            )
              and (:q = '' or lower(u.full_name) like lower(concat('%',:q,'%'))
                   or lower(u.email) like lower(concat('%',:q,'%')))
            """,
            countQuery = """
            select count(*) from users u
            where exists (
                select 1 from tenant_memberships m
                where m.user_id=u.id and m.tenant_id=:tenantId
                  and m.membership_status=case when :active is false then 'SUSPENDED' else 'ACTIVE' end
                  and (:role is null or m.role=:role)
            )
              and (:q = '' or lower(u.full_name) like lower(concat('%',:q,'%'))
                   or lower(u.email) like lower(concat('%',:q,'%')))
            """,
            nativeQuery = true)
    Page<User> search(@Param("q") String q, @Param("role") String role,
                      @Param("active") Boolean active, @Param("tenantId") UUID tenantId,
                      Pageable pageable);
}

