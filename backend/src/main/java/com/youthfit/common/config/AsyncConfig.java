package com.youthfit.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 가이드/적합도 룰/RAG 인덱싱 LLM 후속 처리용 비동기 실행기.
 *
 * - 풀 크기: core 2 / max 4
 * - 큐 깊이: 100
 * - 거절 정책: CallerRunsPolicy (큐가 차면 publisher 스레드가 직접 실행하여 자연 throttle)
 * - 셧다운: 진행 중 작업 60초 대기 후 강제 종료
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "llmExecutor")
    public ThreadPoolTaskExecutor llmExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(2);
        exec.setMaxPoolSize(4);
        exec.setQueueCapacity(100);
        exec.setThreadNamePrefix("llm-");
        exec.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        exec.setWaitForTasksToCompleteOnShutdown(true);
        exec.setAwaitTerminationSeconds(60);
        exec.initialize();
        return exec;
    }
}
