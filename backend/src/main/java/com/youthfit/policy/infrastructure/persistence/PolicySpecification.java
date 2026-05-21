package com.youthfit.policy.infrastructure.persistence;

import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyStatus;
import com.youthfit.policy.domain.model.RegionFilter;
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
    private static final String NATIONWIDE_LABEL = "전국";

    private PolicySpecification() {
    }

    public static Specification<Policy> withFilters(RegionFilter regionFilter,
                                                    Category category,
                                                    PolicyStatus status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (regionFilter != null && regionFilter.isActive()) {
                predicates.add(regionPredicate(root, cb, regionFilter));
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

    public static Specification<Policy> withCalendarRange(LocalDate from, LocalDate to,
                                                          RegionFilter regionFilter,
                                                          Category category) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            Path<LocalDate> applyStart = root.get("applyStart");
            Path<LocalDate> applyEnd = root.get("applyEnd");

            // applyStart <= to (null 이면 만족으로 간주)
            Predicate startOk = cb.or(
                    cb.isNull(applyStart),
                    cb.lessThanOrEqualTo(applyStart, to)
            );
            // applyEnd >= from (null 이면 만족으로 간주)
            Predicate endOk = cb.or(
                    cb.isNull(applyEnd),
                    cb.greaterThanOrEqualTo(applyEnd, from)
            );
            predicates.add(startOk);
            predicates.add(endOk);

            // 둘 다 null 인 상시 정책은 제외
            predicates.add(cb.or(
                    cb.isNotNull(applyStart),
                    cb.isNotNull(applyEnd)
            ));

            // applyStart > applyEnd 인 데이터 오류 제외
            predicates.add(cb.or(
                    cb.isNull(applyStart),
                    cb.isNull(applyEnd),
                    cb.lessThanOrEqualTo(applyStart, applyEnd)
            ));

            if (regionFilter != null && regionFilter.isActive()) {
                predicates.add(regionPredicate(root, cb, regionFilter));
            }
            if (category != null) {
                predicates.add(cb.equal(root.get("category"), category));
            }

            if (query != null) {
                Class<?> resultType = query.getResultType();
                if (resultType != Long.class && resultType != long.class) {
                    query.orderBy(
                            cb.asc(cb.coalesce(applyStart, FAR_FUTURE)),
                            cb.desc(cb.coalesce(applyEnd, FAR_PAST))
                    );
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<Policy> alwaysOpen(RegionFilter regionFilter, Category category) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.isNull(root.get("applyStart")));
            predicates.add(cb.isNull(root.get("applyEnd")));

            if (regionFilter != null && regionFilter.isActive()) {
                predicates.add(regionPredicate(root, cb, regionFilter));
            }
            if (category != null) {
                predicates.add(cb.equal(root.get("category"), category));
            }

            if (query != null) {
                Class<?> resultType = query.getResultType();
                if (resultType != Long.class && resultType != long.class) {
                    query.orderBy(cb.desc(root.get("createdAt")));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static Predicate regionPredicate(Root<Policy> root, CriteriaBuilder cb, RegionFilter f) {
        Path<String> regionCode = root.get("regionCode");
        // 콤마 패딩된 CSV 표현: ',' || region_codes || ','
        Expression<String> paddedCsv = cb.concat(cb.concat(",", root.<String>get("regionCodes")), ",");

        if (f.isNationwideOnly()) {
            return cb.equal(regionCode, NATIONWIDE_LABEL);
        }

        List<Predicate> ors = new ArrayList<>();
        // 전국 정책은 어떤 지역 필터에서도 항상 포함
        ors.add(cb.equal(regionCode, NATIONWIDE_LABEL));

        for (String sigungu : f.sigunguCodes()) {
            // 정확 매칭 (단일 대표 코드)
            ors.add(cb.equal(regionCode, sigungu));
            // CSV 안 멤버십 (콤마 패딩으로 false-positive 방지)
            ors.add(cb.like(paddedCsv, "%," + sigungu + ",%"));
        }
        for (String sido : f.sidoCodes()) {
            // 정확 매칭 — 시·도 자체 코드 (예: regionCode = "11")
            ors.add(cb.equal(regionCode, sido));
            // 5자리 행정코드의 prefix (예: "11680" 은 "11" 로 시작)
            ors.add(cb.like(regionCode, sido + "___"));
            // CSV 안 시·도 코드 정확 매칭 — ",11," 형태
            ors.add(cb.like(paddedCsv, "%," + sido + ",%"));
            // CSV 안 시·도 prefix 5자리 — ",11xxx," 형태. SQL '_' 와이드카드로 정확히 5자리만.
            ors.add(cb.like(paddedCsv, "%," + sido + "___,%"));
        }
        return cb.or(ors.toArray(new Predicate[0]));
    }

    private static void applyOrder(Root<Policy> root, CriteriaQuery<?> query,
                                   CriteriaBuilder cb, PolicyStatus status) {
        if (query == null) return;
        Class<?> resultType = query.getResultType();
        if (resultType == Long.class || resultType == long.class) return;
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
