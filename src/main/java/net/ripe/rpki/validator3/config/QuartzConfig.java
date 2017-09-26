package net.ripe.rpki.validator3.config;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

import javax.sql.DataSource;

@Configuration
public class QuartzConfig {
    @Bean
    public SchedulerFactoryBean schedulerFactoryBean(ApplicationContext applicationContext, DataSource dataSource) {
        SchedulerFactoryBean result = new SchedulerFactoryBean();
        result.setApplicationContext(applicationContext);
        result.setDataSource(dataSource);
        return result;
    }
}
