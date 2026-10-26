package com.feng.dsagent.classroom.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import tools.jackson.databind.ObjectMapper;

import com.feng.dsagent.classroom.support.ClassroomScriptParser;
import com.feng.dsagent.classroom.support.ClassroomStateMachine;

@Configuration
public class ClassroomConfiguration {

    @Bean
    public ClassroomStateMachine classroomStateMachine() {
        return new ClassroomStateMachine();
    }

    @Bean
    public ClassroomScriptParser classroomScriptParser(ObjectMapper objectMapper) {
        return new ClassroomScriptParser(objectMapper);
    }

    /**
     * Bounded pool for classroom preparation jobs (blocking model + DB work). Daemon threads so a
     * wedged prep does not keep the JVM alive on shutdown; Spring destroys the bean on context close.
     */
    @Bean(name = "classroomPreparationExecutor", destroyMethod = "shutdown")
    public ThreadPoolTaskExecutor classroomPreparationExecutor(
        @Value("${app.classroom.preparation.core-pool-size:2}") int corePoolSize,
        @Value("${app.classroom.preparation.max-pool-size:8}") int maxPoolSize,
        @Value("${app.classroom.preparation.queue-capacity:100}") int queueCapacity
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Math.max(1, corePoolSize));
        executor.setMaxPoolSize(Math.max(executor.getCorePoolSize(), maxPoolSize));
        executor.setQueueCapacity(Math.max(0, queueCapacity));
        executor.setThreadNamePrefix("classroom-prep-");
        executor.setDaemon(true);
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
