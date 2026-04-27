package com.youthfit.policy.infrastructure.persistence;

import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class PolicySpecification {

    private static final LocalDate FAR_FUTURE = LocalDate.of(9999, 12, 31);
    private static final LocalDate FAR_PAST = LocalDate.of(1, 1, 1);

    private PolicySpecification() {
    }

    public static Specification<Policy> withFilters(String regionCode, Category category, PolicyStatus status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (regionCode != null) {
                predicates.add(cb.equal(root.get("regionCode"), regionCode));
            }
            if (category != null) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            if (status != null) {
                predicates.add(cb.equal(effectiveStatusExpr(root, cb), status.name()));
            }

            applyOrder(root, query, cb, status);

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<Policy> withKeyword(String keyword, PolicyStatus status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            String pattern = "%" + keyword.toLowerCase() + "%";
            predicates.add(cb.or(
                    cb.like(cb.lower(root.get("title")), pattern),
                    cb.like(cb.lower(root.get("summary")), pattern)
            ));
            if (status != null) {
                predicates.add(cb.equal(effectiveStatusExpr(root, cb), status.name()));
            }

            applyOrder(root, query, cb, status);

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static void applyOrder(Root<Policy> root, CriteriaQuery<?> query,
                                   CriteriaBuilder cb, PolicyStatus status) {
        if (query == null) {
            return;
        }
        Class<?> resultType = query.getResultType();
        if (resultType == Long.class || resultType == long.class) {
            return; // count query
        }
        query.orderBy(buildOrders(root, cb, status));
    }

    private static List<Order> buildOrders(Root<Policy> root, CriteriaBuilder cb, PolicyStatus status) {
        if (status == null) {
            return List.of(cb.desc(root.get("createdAt")));
        }
        return switch (status) {
            case OPEN -> List.of(
                    cb.asc(cb.coalesce(root.get("applyEnd"), FAR_FUTURE)),
                    cb.desc(root.get("createdAt"))
            );
            case UPCOMING -> List.of(
                    cb.asc(cb.coalesce(root.get("applyStart"), FAR_FUTURE)),
                    cb.desc(root.get("createdAt"))
            );
            case CLOSED -> List.of(
                    cb.desc(cb.coalesce(root.get("applyEnd"), FAR_PAST)),
                    cb.desc(root.get("createdAt"))
            );
        };
    }

    /**
     * 정책의 신청기간(applyStart/applyEnd)과 기준연도(referenceYear)로부터 effective status를 도출하는 SQL CASE 식.
     * frontend/src/lib/policyStatus.ts:23 의 getEffectiveStatus 와 동일한 우선순위.
     */
    private static Expression<String> effectiveStatusExpr(Root<Policy> root, CriteriaBuilder cb) {
        LocalDate today = LocalDate.now();
        int currentYear = today.getYear();

        Path<LocalDate> applyStart = root.get("applyStart");
        Path<LocalDate> applyEnd = root.get("applyEnd");
        Path<Integer> referenceYear = root.get("referenceYear");

        return cb.<String>selectCase()
                .when(cb.and(cb.isNotNull(applyEnd), cb.lessThan(applyEnd, today)),
                        PolicyStatus.CLOSED.name())
                .when(cb.and(cb.isNotNull(applyStart), cb.greaterThan(applyStart, today)),
                        PolicyStatus.UPCOMING.name())
                .when(cb.and(cb.isNotNull(applyStart), cb.isNotNull(applyEnd)),
                        PolicyStatus.OPEN.name())
                .when(cb.and(cb.isNotNull(referenceYear), cb.lessThan(referenceYear, currentYear)),
                        PolicyStatus.CLOSED.name())
                .when(cb.and(cb.isNotNull(referenceYear), cb.equal(referenceYear, currentYear)),
                        PolicyStatus.OPEN.name())
                .otherwise(PolicyStatus.UPCOMING.name())
                .as(String.class);
    }
}
