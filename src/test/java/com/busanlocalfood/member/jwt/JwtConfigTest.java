package com.busanlocalfood.member.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import com.busanlocalfood.member.domain.Role;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** 설정 바인딩과 시작 시 검증. 환경 변수는 application.yml의 ${JWT_SECRET} 플레이스홀더를 통해 주입된다. */
class JwtConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(JwtConfig.class, com.busanlocalfood.global.config.TimeConfig.class);

    private static String validSecret() {
        return Base64.getEncoder().encodeToString(JwtTestSupport.randomKey(32).getEncoded());
    }

    @Test
    void 정상_키이면_발급기와_검증기를_만든다() {
        runner.withPropertyValues("jwt.secret=" + validSecret())
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).hasSingleBean(AccessTokenIssuer.class);
                    assertThat(ctx).hasSingleBean(AccessTokenVerifier.class);
                });
    }

    @Test
    void 만료_시간_기본값은_1800초다() {
        runner.withPropertyValues("jwt.secret=" + validSecret())
                .run(ctx -> assertThat(ctx.getBean(AccessTokenIssuer.class).ttl().getSeconds()).isEqualTo(1800));
    }

    @Test
    void 만료_시간_환경_값이_적용된다() {
        runner.withPropertyValues("jwt.secret=" + validSecret(), "jwt.access-token-expiry-seconds=600")
                .run(ctx -> assertThat(ctx.getBean(AccessTokenIssuer.class).ttl().getSeconds()).isEqualTo(600));
    }

    @Test
    void 설정된_발급기와_검증기가_서로_호환된다() {
        runner.withPropertyValues("jwt.secret=" + validSecret())
                .run(ctx -> {
                    String token = ctx.getBean(AccessTokenIssuer.class).issue(9L, Role.USER).token();
                    assertThat(ctx.getBean(AccessTokenVerifier.class).verify(token))
                            .isEqualTo(new AuthenticatedMember(9L, Role.USER));
                });
    }

    @Test
    void JWT_SECRET이_없으면_시작을_실패시킨다() {
        runner.run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).hasStackTraceContaining("JWT_SECRET");
        });
    }

    @Test
    void 빈_JWT_SECRET이면_시작을_실패시킨다() {
        runner.withPropertyValues("jwt.secret=")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void 잘못된_Base64면_시작을_실패시킨다() {
        runner.withPropertyValues("jwt.secret=@@@not base64@@@")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void 짧은_키이면_시작을_실패시킨다() {
        String short31 = Base64.getEncoder().encodeToString(new byte[31]);
        runner.withPropertyValues("jwt.secret=" + short31)
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void 만료_시간이_0이하이면_시작을_실패시킨다() {
        runner.withPropertyValues("jwt.secret=" + validSecret(), "jwt.access-token-expiry-seconds=0")
                .run(ctx -> assertThat(ctx).hasFailed());
    }
}
