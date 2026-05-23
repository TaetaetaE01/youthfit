package com.youthfit.admin.infrastructure.aop;

import com.youthfit.admin.domain.model.ScheduledTaskRun;
import com.youthfit.admin.domain.repository.ScheduledTaskRunRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * {@link ScheduledTaskRunRecorder} 의 영속화 도우미 서비스.
 *
 * <p>AOP 어드바이저 안에서 직접 {@code @Transactional} 을 선언하면
 * Spring 이 어드바이저 빈 자체에 프록시를 씌우지 않아 트랜잭션이 적용되지 않는다.
 * 이 서비스를 별도 빈으로 분리함으로써 Spring AOP 프록시가 정상 동작하고,
 * {@code REQUIRES_NEW} 전파 레벨을 통해 스케줄러 본 트랜잭션과 독립된
 * 커밋/롤백 경계를 보장한다.</p>
 *
 * <p>호출 시점은 스케줄러 메서드 완료 후 단 한 번이며,
 * {@code started_at}, {@code finished_at}, {@code success}, {@code error_message} 를
 * 한 번의 INSERT 로 기록한다.</p>
 */
@Service
@RequiredArgsConstructor
class ScheduledTaskRunRecordingService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTaskRunRecordingService.class);

    private final ScheduledTaskRunRepository repository;

    /**
     * 스케줄러 실행 결과를 단일 INSERT 로 기록한다.
     *
     * <p>{@code REQUIRES_NEW} 로 항상 새 트랜잭션을 시작하므로
     * 스케줄러가 예외로 롤백되더라도 실패 기록은 독립적으로 커밋된다.</p>
     *
     * @param taskName    스케줄러 메서드 식별자 (ClassName.methodName)
     * @param startedAt   메서드 진입 시각 (어드바이저가 캡처)
     * @param finishedAt  메서드 종료 시각
     * @param success     정상 종료 여부
     * @param errorMessage 예외 메시지 (success == true 면 null)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String taskName, Instant startedAt, Instant finishedAt,
                       boolean success, String errorMessage) {
        ScheduledTaskRun run = ScheduledTaskRun.completed(taskName, startedAt, finishedAt, success, errorMessage);
        try {
            repository.save(run);
        } catch (RuntimeException ex) {
            log.warn("Failed to persist ScheduledTaskRun for task {}: {}", taskName, ex.getMessage());
        }
    }
}
