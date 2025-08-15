package com.cjree.filelisten.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class ExecutorConfig {

    /**
     * 事件处理线程池
     */
    @Bean(name = "eventExecutor")
    public Executor eventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数 = CPU核心数 * 2
        int corePoolSize = Runtime.getRuntime().availableProcessors() * 2;
        executor.setCorePoolSize(corePoolSize);
        // 最大线程数 = CPU核心数 * 4
        executor.setMaxPoolSize(Runtime.getRuntime().availableProcessors() * 4);
        // 队列容量
        executor.setQueueCapacity(1000);
        // 线程名前缀
        executor.setThreadNamePrefix("event-handler-");
        // 拒绝策略：由提交任务的线程处理
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * 数据库写入线程池
     */
    @Bean(name = "dbWriterExecutor")
    public Executor dbWriterExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 数据库写入单线程即可，避免并发写入压力
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        // 队列容量，缓冲写入任务
        executor.setQueueCapacity(5000);
        executor.setThreadNamePrefix("db-writer-");
        // 拒绝策略：缓冲队列满后阻塞
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}

