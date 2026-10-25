package com.feng.dsagent.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationBeans {

    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }
}
