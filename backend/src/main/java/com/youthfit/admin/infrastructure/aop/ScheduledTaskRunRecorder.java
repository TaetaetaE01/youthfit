package com.youthfit.admin.infrastructure.aop;

import com.youthfit.admin.domain.model.ScheduledTaskRun;
import com.youthfit.admin.domain.repository.ScheduledTaskRunRepository;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 모든 {@link org.springframework.scheduling.annotation.Scheduled @Scheduled} 메서드의
 * 실행 라이프사이클을 {@link ScheduledTaskRun} 테이블에 기록하는 AOP 어드바이저.
 *
 * <p>스케줄러 호출이 진입하면 시작 row 를 insert 하고, 정상 종료 시 success,
 * 예외 발생 시 failure 로 동일 row 를 update 한다. DB 저장 자체가 실패해도
 * 스케줄러 본 동작을 막지 않도록 모든 저장은 try/catch + WARN 으로 격리한다.
 * 원래 예외는 항상 그대로 재던져(rethrow) 스케줄러의 에러 핸들러/재시도 동작을
 * 보존한다.</p>
 */
@Aspect
@Component
@RequiredArgsConstructor
public class ScheduledTaskRunRecorder {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTaskRunRecorder.class);

    private final ScheduledTaskRunRepository repository;

    @Around("@annotation(org.springframework.scheduling.annotation.Scheduled)")
    public Object record(ProceedingJoinPoint pjp) throws Throwable {
        String taskName = taskNameOf(pjp);
        Instant startedAt = Instant.now();

        ScheduledTaskRun run = safeSave(ScheduledTaskRun.started(taskName, startedAt), taskName, "start");

        try {
            Object result = pjp.proceed();
            run.markSucceeded(Instant.now());
            safeSave(run, taskName, "success");
            return result;
        } catch (Throwable ex) {
            run.markFailed(Instant.now(), ex.getMessage());
            safeSave(run, taskName, "failure");
            throw ex;
        }
    }

    private ScheduledTaskRun safeSave(ScheduledTaskRun run, String taskName, String phase) {
        try {
            return repository.save(run);
        } catch (RuntimeException ex) {
            log.warn("Failed to record {} of scheduled task {}: {}", phase, taskName, ex.getMessage());
            return run;
        }
    }

    private static String taskNameOf(ProceedingJoinPoint pjp) {
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        return sig.getDeclaringType().getSimpleName() + "." + sig.getName();
    }
}
