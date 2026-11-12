package com.busanlocalfood;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.busanlocalfood.global.config.TimeConfig;
import com.busanlocalfood.member.domain.Member;
import com.busanlocalfood.member.jwt.AccessTokenIssuer;
import com.busanlocalfood.member.jwt.AccessTokenVerifier;
import com.busanlocalfood.member.refresh.RefreshRotationGate;
import com.busanlocalfood.member.refresh.RefreshTokenService;
import com.busanlocalfood.member.repository.MemberRepository;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.TimeZone;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 전체 애플리케이션 컨텍스트가 실제 MySQL 8 테스트 DB로 기동되는지, 그리고 시간대 기준이 일치하는지 확인한다.
 * 환경 변수 TEST_DB_URL, TEST_DB_USERNAME, TEST_DB_PASSWORD가 없으면 건너뛴다.
 * 테스트 DB에는 db/manual/001, 002를 순서대로 적용해야 한다. JWT 키는 테스트 안에서 임의로 만든다.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=${TEST_DB_URL}",
        "spring.datasource.username=${TEST_DB_USERNAME}",
        "spring.datasource.password=${TEST_DB_PASSWORD}"
})
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
class ApplicationContextDbTest {

    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        String secret = Base64.getEncoder().encodeToString(key);
        registry.add("jwt.secret", () -> secret);
    }

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 인증_관련_빈이_모두_연결되고_시계_빈은_하나다() {
        assertThat(context.getBean(AccessTokenIssuer.class)).isNotNull();
        assertThat(context.getBean(AccessTokenVerifier.class)).isNotNull();
        assertThat(context.getBean(RefreshTokenService.class)).isNotNull();
        assertThat(context.getBeansOfType(RefreshRotationGate.class)).hasSize(1);
        assertThat(context.getBeansOfType(Clock.class)).hasSize(1);
        assertThat(context.getBean(Clock.class).getZone()).isEqualTo(TimeConfig.SERVICE_ZONE);
    }

    @Test
    void JVM_시간대와_DB_세션_시간대가_서비스_시간대로_일치한다() {
        assertThat(TimeZone.getDefault().toZoneId()).isEqualTo(TimeConfig.SERVICE_ZONE);
        assertThat(jdbcTemplate.queryForObject("SELECT @@session.time_zone", String.class)).isEqualTo("+09:00");
    }

    @Test
    void 앱이_저장한_시각과_DB의_NOW가_같은_기준이다() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Member saved = memberRepository.saveAndFlush(Member.create(
                "ctx" + suffix + "@example.com", "$2a$10$abcdefghijklmnopqrstuuvwxyz0123456789ABCDEFGHIJKLMNO",
                "문맥" + suffix.substring(0, 4), null));
        try {
            // 드라이버 변환을 거치지 않도록 DB에서 문자열로 읽어 비교한다.
            String storedText = jdbcTemplate.queryForObject(
                    "SELECT CAST(created_at AS CHAR) FROM member WHERE id = ?", String.class, saved.getId());
            String dbNowText = jdbcTemplate.queryForObject("SELECT CAST(NOW(6) AS CHAR)", String.class);
            LocalDateTime stored = Timestamp.valueOf(storedText).toLocalDateTime();
            LocalDateTime dbNow = Timestamp.valueOf(dbNowText).toLocalDateTime();

            assertThat(stored).isCloseTo(dbNow, within(1, ChronoUnit.MINUTES));
            assertThat(stored).isCloseTo(LocalDateTime.now(TimeConfig.SERVICE_ZONE), within(1, ChronoUnit.MINUTES));
        } finally {
            jdbcTemplate.update("DELETE FROM member WHERE id = ?", saved.getId());
        }
    }
}
