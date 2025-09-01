package com.smarteye.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaAuditing
@EnableJpaRepositories(basePackages = "com.smarteye.repository")
public class JpaConfig {
    // JPA Auditing 활성화
    // @CreatedDate, @LastModifiedDate 어노테이션 지원
}