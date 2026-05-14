package com.oriole.wisepen.resource.config;

import com.oriole.wisepen.resource.constant.SearchConstants;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * 搜索模块的延迟任务调度池。
 * <p>
 * 仅用于 {@code SearchAclRecalcConsumer} 与 {@code NoteSnapshotConsumer} 的 3 秒延迟反查，
 * 不承担其他业务负载，与 Spring 默认 TaskScheduler 区隔，避免抢占资源域主流程线程。
 */
@Configuration
public class SearchThreadPoolConfig {

    @Bean
    public ScheduledExecutorService scheduledExecutorService() {
        return Executors.newScheduledThreadPool(SearchConstants.COREPOOLSIZE);
    }
}
