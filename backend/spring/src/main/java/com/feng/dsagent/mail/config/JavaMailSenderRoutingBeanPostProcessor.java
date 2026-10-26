package com.feng.dsagent.mail.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import com.feng.dsagent.mail.support.SmtpEndpointRouting;
@Component
public final class JavaMailSenderRoutingBeanPostProcessor implements BeanPostProcessor {

    private final SmtpEndpointRouting routing;

    public JavaMailSenderRoutingBeanPostProcessor(SmtpEndpointRouting routing) {
        this.routing = routing;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof JavaMailSenderImpl sender) {
            routing.applyTo(sender, SmtpEndpointRouting.connectionTimeoutMillis(sender.getJavaMailProperties()));
        }
        return bean;
    }
}
