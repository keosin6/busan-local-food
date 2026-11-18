package com.busanlocalfood.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.busanlocalfood.member.oauth.GoogleSignupPending;
import com.busanlocalfood.member.refresh.RefreshTokenService;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** 토큰 발급이 실패하면 회원 생성도 되돌려져야 한다 (같은 트랜잭션). */
@SpringBootTest(properties = {
        "spring.datasource.url=${TEST_DB_URL}",
        "spring.datasource.username=${TEST_DB_USERNAME}",
        "spring.datasource.password=${TEST_DB_PASSWORD}"
})
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
class GoogleSignupRollbackDbTest {

    @DynamicPropertySource
    static void secrets(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> randomBase64Key());
        registry.add("auth.rate-limit.hmac-secret", () -> randomBase64Key());
    }

    private static String randomBase64Key() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    @MockitoBean
    private RefreshTokenService refreshTokenService;

    @Autowired
    private GoogleSignupService signupService;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanTables() {
        jdbc.update("DELETE FROM refresh_token");
        jdbc.update("DELETE FROM member");
    }

    @Test
    void 토큰_발급이_실패하면_회원도_만들어지지_않는다() {
        when(refreshTokenService.issueForNewLogin(anyLong())).thenThrow(new IllegalStateException("token failure"));

        assertThatThrownBy(() -> signupService.complete(
                new GoogleSignupPending("sub-rb-1", "rollback@example.com", Instant.now().plusSeconds(600)), "롤백닉네임"))
                .isInstanceOf(IllegalStateException.class);

        Integer members = jdbc.queryForObject("SELECT COUNT(*) FROM member", Integer.class);
        assertThat(members).isZero();
    }
}
