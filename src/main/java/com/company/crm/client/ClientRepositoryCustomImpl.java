package com.company.crm.client;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
public class ClientRepositoryCustomImpl implements ClientRepositoryCustom {
    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Page<Client> search(UUID tenantId, boolean all, String email, String search,
                               ClientStatus status, UUID owner, Instant from, Instant to, Pageable pageable) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Client> query = builder.createQuery(Client.class);
        Root<Client> client = query.from(Client.class);
        client.fetch("owner", JoinType.INNER);
        query.select(client).where(filters(builder, client, tenantId, all, email, search, status, owner, from, to));
        List<Order> ordering = new ArrayList<>();
        for (Sort.Order item : pageable.getSort()) {
            Path<?> field = client.get(item.getProperty());
            ordering.add(item.isAscending() ? builder.asc(field) : builder.desc(field));
        }
        if (!ordering.isEmpty()) query.orderBy(ordering);

        CriteriaQuery<Long> countQuery = builder.createQuery(Long.class);
        Root<Client> countRoot = countQuery.from(Client.class);
        countQuery.select(builder.count(countRoot))
                .where(filters(builder, countRoot, tenantId, all, email, search, status, owner, from, to));
        long total = entityManager.createQuery(countQuery).getSingleResult();
        if (pageable.getOffset() > Integer.MAX_VALUE) {
            return new PageImpl<>(List.of(), pageable, total);
        }
        TypedQuery<Client> resultQuery = entityManager.createQuery(query);
        resultQuery.setFirstResult((int) pageable.getOffset());
        resultQuery.setMaxResults(pageable.getPageSize());
        return new PageImpl<>(resultQuery.getResultList(), pageable, total);
    }

    private Predicate[] filters(CriteriaBuilder builder, Root<Client> client, UUID tenantId,
                                boolean all, String email, String search, ClientStatus status,
                                UUID owner, Instant from, Instant to) {
        List<Predicate> filters = new ArrayList<>();
        filters.add(builder.equal(client.get("tenantId"), tenantId));
        filters.add(builder.isNull(client.get("archivedAt")));
        if (!all) {
            filters.add(builder.equal(builder.lower(client.get("owner").get("email")), email.toLowerCase()));
        }
        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.trim().toLowerCase() + "%";
            filters.add(builder.or(
                    builder.like(builder.lower(client.<String>get("companyName")), pattern),
                    builder.like(builder.lower(builder.coalesce(client.<String>get("email"), "")), pattern),
                    builder.like(builder.lower(builder.coalesce(client.<String>get("phone"), "")), pattern)));
        }
        if (status != null) filters.add(builder.equal(client.get("status"), status));
        if (owner != null) filters.add(builder.equal(client.get("owner").get("id"), owner));
        if (from != null) filters.add(builder.greaterThanOrEqualTo(client.<Instant>get("createdAt"), from));
        if (to != null) filters.add(builder.lessThan(client.<Instant>get("createdAt"), to));
        return filters.toArray(Predicate[]::new);
    }
}
