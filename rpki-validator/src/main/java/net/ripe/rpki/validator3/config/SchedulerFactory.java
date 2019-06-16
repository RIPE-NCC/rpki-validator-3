package net.ripe.rpki.validator3.config;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;

import javax.inject.Singleton;

@Factory
public class SchedulerFactory {


    org.quartz.SchedulerFactory factory = new org.quartz.impl.StdSchedulerFactory();


    @Bean
    @Singleton
    Scheduler scheduler() {
        try {
            return factory.getScheduler();
        } catch (SchedulerException e) {
            throw new RuntimeException(e);
        }
    }
}