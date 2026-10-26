package com.feng.dsagent.animation.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.feng.dsagent.animation.support.AnimationValidator;
@Configuration
public class AnimationConfiguration {

    @Bean
    public AnimationValidator animationValidator() {
        return new AnimationValidator();
    }
}
