package com.youthfit.admin.infrastructure.persistence;

import com.youthfit.admin.application.port.PolicyIntakeStatsReader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * 어드민 대시보드 신호 평가용 정책 테이블 read-only 집계 쿼리.
 *
 * <p>도메인 {@code PolicyRepository} 인터페이스에 cross-cutting 집계를 추가하면
 * 다른 모듈에 새 책임이 새기 때문에, 어드민 모듈 내부에 한정한 read-only
 * repository 로 분리한다. 트랜잭션은 호출하는 application 레이어에 위임한다.</p>
 */
@Repository
public class DashboardPolicyQueryRepository implements PolicyIntakeStatsReader {

    private final JdbcTemplate jdbc;

    public DashboardPolicyQueryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * {@code [from, to)} 구간에 생성된 정책 수를 반환한다.
     *
     * <p>{@code java.sql.Timestamp.from(Instant)} 를 사용해 JDBC 드라이버에
     * UTC 시점을 그대로 전달한다. 타임존 추론을 드라이버에 맡기지 않는다.</p>
     */
    @Override
    public long countCreatedBetween(Instant from, Instant to) {
        Long n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM policy WHERE created_at >= ? AND created_at < ?",
                Long.class,
                Timestamp.from(from), Timestamp.from(to));
        return n == null ? 0L : n;
    }
}
