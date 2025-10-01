package com.smarteye;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableCaching
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties
@EnableJpaRepositories(basePackages = {
    "com.smarteye.domain.analysis.repository",
    "com.smarteye.domain.book.repository",
    "com.smarteye.domain.document.repository",
    "com.smarteye.domain.logging.repository",
    "com.smarteye.domain.user.repository",
    "com.smarteye.infrastructure.persistence"
})
public class SmartEyeApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartEyeApplication.class, args);
    }
}