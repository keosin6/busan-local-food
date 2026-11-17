package com.busanlocalfood.global.config;

import com.busanlocalfood.member.ratelimit.AuthRateLimitService;
import com.busanlocalfood.member.ratelimit.ClientIpResolver;
import com.busanlocalfood.member.ratelimit.SignupRateLimitFilter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   AuthRateLimitService rateLimitService,
                                                   ClientIpResolver clientIpResolver,
                                                   @Qualifier("handlerExceptionResolver")
                                                   HandlerExceptionResolver exceptionResolver) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                // TODO(인증 구현 단계): 임시 접근 정책이다. 회원가입·로그인 경로와 USER/ADMIN 권한 규칙
                //  (FR-01-08, FR-09-07)으로 교체한다. 지금은 모든 요청을 허용한다.
                .anyRequest().permitAll());

        // 회원가입 요청 집계 (API_SPEC 1.4). CSRF 검증 뒤, 본문 읽기 이전에 실행한다. CSRF 실패 요청은 집계되지 않는다.
        http.addFilterAfter(new SignupRateLimitFilter(rateLimitService, clientIpResolver, exceptionResolver),
                CsrfFilter.class);

        // TODO(T11): OAuth2 임시 세션 정책은 인증 구현 단계에서 확정한다.
        //  세션 정책을 완전 Stateless로 고정하지 않는다 (DEVELOPMENT_RULES 5.7).
        // CSRF 보호는 기본값(활성)을 유지한다. 쿠키 기반 인증을 사용하므로 비활성화하지 않는다.
        return http.build();
    }
}
