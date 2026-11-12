package com.busanlocalfood;

import com.busanlocalfood.global.config.TimeConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

// 기본 UserDetailsService 자동 구성을 제외해 시작 로그에 임시 비밀번호가 출력되지 않도록 한다.
// TODO(인증 구현 단계): 회원 기반 인증을 구현할 때 이 제외 설정을 제거한다.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class BusanLocalFoodApplication {

    public static void main(String[] args) {
        // 시간대를 먼저 고정한다. DB 연결과 시각 계산이 모두 이 값을 기준으로 한다.
        TimeConfig.applyDefaultTimeZone();
        SpringApplication.run(BusanLocalFoodApplication.class, args);
    }
}
