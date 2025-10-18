package com.smarteye.infrastructure.config;

import com.smarteye.domain.user.entity.User;
import com.smarteye.domain.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 개발 환경 전용 데이터 초기화
 * 기본 테스트 사용자를 자동 생성하여 인증 없이 백엔드 테스트 가능
 */
@Component
@Profile("dev")  // 개발 환경에서만 실행
public class DevDataInitializer implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(DevDataInitializer.class);

    @Autowired
    private UserRepository userRepository;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        logger.info("🔧 개발 환경 데이터 초기화 시작...");

        // 기본 개발 사용자 생성 (이미 존재하면 건너뛰기)
        if (userRepository.findByUsername("dev_user").isEmpty()) {
            User devUser = new User("dev_user", "dev@smarteye.com", "개발 테스트 사용자");
            devUser.setActive(true);
            userRepository.save(devUser);
            logger.info("✅ 기본 개발 사용자 생성 완료: {} (ID: {})", devUser.getUsername(), devUser.getId());
        } else {
            logger.info("ℹ️  기본 개발 사용자가 이미 존재합니다.");
        }

        logger.info("🎉 개발 환경 데이터 초기화 완료!");
    }
}
