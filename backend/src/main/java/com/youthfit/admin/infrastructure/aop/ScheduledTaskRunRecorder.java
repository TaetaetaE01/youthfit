package com.youthfit.admin.infrastructure.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 모든 {@link org.springframework.scheduling.annotation.Scheduled @Scheduled} 메서드의
 * 실행 결과를 {@link com.youthfit.admin.domain.model.ScheduledTaskRun} 테이블에 기록하는
 * AOP 어드바이저.
 *
 * <h2>설계 결정</h2>
 * <ul>
 *   <li><b>단일 INSERT 전략</b>: 시작 시각은 메모리({@code startedAt})에 보관하고,
 *       종료 시점에 {@code started_at · finished_at · success · error_message} 를
 *       한 번의 INSERT 로 기록한다. 이전 방식(start INSERT + finish UPDATE) 대비
 *       DB 왕복이 절반으로 줄어든다.</li>
 *   <li><b>REQUIRES_NEW 트랜잭션 격리</b>: 영속화는 {@link ScheduledTaskRunRecordingService}
 *       에 위임한다. 해당 서비스가 {@code REQUIRES_NEW} 로 독립 트랜잭션을 열기 때문에
 *       스케줄러 본 트랜잭션이 롤백되더라도 실패 기록은 커밋된다.
 *       어드바이저 빈 자체에 {@code @Transactional} 을 선언하면 Spring AOP 프록시가
 *       적용되지 않으므로 별도 서비스 빈(companion-service 패턴)으로 분리했다.</li>
 *   <li><b>스케줄러 보호</b>: DB 저장이 실패해도 스케줄러 본 동작을 막지 않는다.
 *       저장 예외는 {@link ScheduledTaskRunRecordingService} 내부에서 WARN 으로
 *       격리하며, 원래 예외는 항상 재던져(rethrow) 스케줄러의 에러 핸들러를 보존한다.</li>
 * </ul>
 */
@Aspect
@Component
public class ScheduledTaskRunRecorder {

    private final ScheduledTaskRunRecordingService recordingService;

    public ScheduledTaskRunRecorder(ScheduledTaskRunRecordingService recordingService) {
        this.recordingService = recordingService;
    }

    @Around("@annotation(org.springframework.scheduling.annotation.Scheduled)")
    public Object record(ProceedingJoinPoint pjp) throws Throwable {
        String taskName = taskNameOf(pjp);
        Instant startedAt = Instant.now();

        try {
            Object result = pjp.proceed();
            recordingService.record(taskName, startedAt, Instant.now(), true, null);
            return result;
        } catch (Throwable ex) {
            recordingService.record(taskName, startedAt, Instant.now(), false, ex.getMessage());
            throw ex;
        }
    }

    private static String taskNameOf(ProceedingJoinPoint pjp) {
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        return sig.getDeclaringType().getSimpleName() + "." + sig.getName();
    }
}
