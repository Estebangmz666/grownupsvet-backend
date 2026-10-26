package edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.reminder.configuration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "grownupsvet.clinical.reminders", name = "polling-enabled", havingValue = "true", matchIfMissing = true)
public class ClinicalReminderWorkerConfiguration {
    @Bean("clinicalReminderExecutor")
    ThreadPoolTaskExecutor clinicalReminderExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.setThreadNamePrefix("clinical-reminder-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }
}
