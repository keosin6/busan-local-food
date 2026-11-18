package com.busanlocalfood.member.oauth;

import com.busanlocalfood.member.ratelimit.AuthRateLimitService;
import com.busanlocalfood.member.ratelimit.ClientIpResolver;
import com.busanlocalfood.member.service.AuthApiService;
import com.busanlocalfood.member.service.GoogleLoginService;
import com.busanlocalfood.member.web.AuthCookies;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Google OAuth2 구성 (API_SPEC 2.5). Client ID와 Secret은 환경 변수 GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET에서만 읽는다.
 *
 * - 둘 다 있으면 Google 로그인이 켜진다. 시작은 /oauth2/authorization/google, 콜백은 /login/oauth2/code/google.
 * - 둘 다 없으면 Google 로그인은 꺼진다. 기동은 된다. 이 경로들은 노출되지 않는다.
 * - 운영(prod)에서 둘 다 없거나 한쪽만 있으면 기동하지 않는다. local에서 한쪽만 있어도 기동하지 않는다.
 * 값은 로그와 오류 메시지에 남기지 않는다.
 */
@Configuration
public class GoogleOAuth2Config {

    static final String REGISTRATION_ID = "google";

    @Bean
    GoogleOAuth2Wiring googleOAuth2Wiring(
            Environment environment,
            @Value("${app.oauth2.google.client-id:}") String clientId,
            @Value("${app.oauth2.google.client-secret:}") String clientSecret,
            AuthRateLimitService rateLimitService,
            ClientIpResolver clientIpResolver,
            // 지연 주입: 웹 설정이 보안 설정을 거쳐 이 빈으로 되돌아오므로 즉시 주입하면 순환 참조가 생긴다.
            @Lazy @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver,
            GoogleLoginService googleLoginService,
            AuthApiService authService,
            AuthCookies cookies) {
        validateCredentials(environment.acceptsProfiles(Profiles.of("prod")), clientId, clientSecret);
        boolean enabled = hasText(clientId) && hasText(clientSecret);
        List<ClientRegistration> registrations = enabled
                ? List.of(CommonOAuth2Provider.GOOGLE.getBuilder(REGISTRATION_ID)
                        .clientId(clientId)
                        .clientSecret(clientSecret)
                        .build())
                : List.of();
        HttpSessionOAuth2AuthorizationRequestRepository requests = new HttpSessionOAuth2AuthorizationRequestRepository();
        ClientRegistrationRepository repository = enabled
                ? new InMemoryClientRegistrationRepository(registrations)
                : registrationId -> null;
        return new GoogleOAuth2Wiring(
                enabled,
                repository,
                requests,
                new GoogleOAuth2LoginGuardFilter(rateLimitService, clientIpResolver, exceptionResolver, requests),
                new GoogleOAuth2SuccessHandler(googleLoginService, authService, cookies),
                new GoogleOAuth2FailureHandler());
    }

    @Bean
    ClientRegistrationRepository clientRegistrationRepository(GoogleOAuth2Wiring wiring) {
        return wiring.registrations();
    }

    /**
     * 설정 검증. 값은 메시지에 넣지 않는다.
     * 한쪽만 설정되면 항상 실패한다. 운영에서 둘 다 없으면 실패한다.
     */
    static void validateCredentials(boolean production, String clientId, String clientSecret) {
        boolean hasId = hasText(clientId);
        boolean hasSecret = hasText(clientSecret);
        if (hasId != hasSecret) {
            throw new IllegalStateException("GOOGLE_CLIENT_ID와 GOOGLE_CLIENT_SECRET은 함께 설정해야 합니다.");
        }
        if (!hasId && production) {
            throw new IllegalStateException("운영 환경에서는 GOOGLE_CLIENT_ID와 GOOGLE_CLIENT_SECRET을 설정해야 합니다.");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
