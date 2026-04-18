package com.youthfit.policy.infrastructure.persistence;

import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicySortType;
import com.youthfit.policy.domain.model.PolicyStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class PolicySpecification {

    private static final LocalDate FAR_FUTURE = LocalDate.of(9999, 12, 31);

    private PolicySpecification() {
    }

    public static Specification<Policy> withFiltersAndSort(String regionCode, Category category,
                                                            PolicyStatus status, PolicySortType sortType) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (regionCode != null) {
                predicates.add(cb.equal(root.get("regionCode"), regionCode));
            }
            if (category != null) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            applyOrder(root, query, cb, sortType);

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<Policy> withKeywordAndSort(String keyword, PolicySortType sortType) {
        return (root, query, cb) -> {
            String pattern = "%" + keyword.toLowerCase() + "%";
            Predicate match = cb.or(
                    cb.like(cb.lower(root.get("title")), pattern),
                    cb.like(cb.lower(root.get("summary")), pattern)
            );

            applyOrder(root, query, cb, sortType);

            return match;
        };
    }

    private static void applyOrder(Root<Policy> root, jakarta.persistence.criteria.CriteriaQuery<?> query,
                                   CriteriaBuilder cb, PolicySortType sortType) {
        if (sortType == null || query == null) {
            return;
        }
        Class<?> resultType = query.getResultType();
        if (resultType == Long.class || resultType == long.class) {
            return; // count query
        }
        query.orderBy(buildOrders(root, cb, sortType));
    }

    private static List<Order> buildOrders(Root<Policy> root, CriteriaBuilder cb, PolicySortType type) {
        return switch (type) {
            case LATEST -> List.of(cb.desc(root.get("createdAt")));
            case DEADLINE -> List.of(
                    cb.asc(statusWeight(root, cb)),
                    cb.asc(cb.coalesce(root.get("applyEnd"), FAR_FUTURE)),
                    cb.desc(root.get("createdAt"))
            );
            case UPCOMING -> List.of(
                    cb.asc(cb.coalesce(root.get("applyStart"), FAR_FUTURE)),
                    cb.desc(root.get("createdAt"))
            );
        };
    }

    private static Expression<Integer> statusWeight(Root<Policy> root, CriteriaBuilder cb) {
        return cb.<Integer>selectCase()
                .when(cb.equal(root.get("status"), PolicyStatus.OPEN), 0)
                .when(cb.equal(root.get("status"), PolicyStatus.UPCOMING), 1)
                .otherwise(2)
                .as(Integer.class);
    }
}
