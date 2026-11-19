package com.busanlocalfood.member.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.busanlocalfood.member.ratelimit.AuthRateLimitService;
import com.busanlocalfood.member.ratelimit.ClientIpResolver;
import com.busanlocalfood.member.refresh.RefreshTokenProperties;
import com.busanlocalfood.member.service.AuthApiService;
import com.busanlocalfood.member.service.GoogleLoginService;
import com.busanlocalfood.member.web.AuthCookies;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.HandlerExceptionResolver;

/** Google OAuth2 설정 검증과 구성. 값은 테스트 안에서만 쓰는 임의 문자열이다. */
class GoogleOAuth2ConfigTest {

    private static final String ID = "test-client-" + System.nanoTime();
    private static final String SECRET = "test-secret-" + System.nanoTime();

    @Test
    void 운영에서_설정이_없으면_기동을_거부한다() {
        assertThatThrownBy(() -> GoogleOAuth2Config.validateCredentials(true, "", ""))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 로컬에서_설정이_없으면_기동하고_Google_로그인은_꺼진다() throws Exception {
        GoogleOAuth2Wiring wiring = wiring("", "");

        assertThat(wiring.enabled()).isFalse();
        assertThat(wiring.registrations().findByRegistrationId("google")).isNull();
    }

    @Test
    void 한쪽만_설정하면_로컬에서도_기동을_거부한다() {
        assertThatThrownBy(() -> GoogleOAuth2Config.validateCredentials(false, ID, ""))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> GoogleOAuth2Config.validateCredentials(false, "", SECRET))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 설정_오류_메시지에는_설정_값이_없다() {
        assertThatThrownBy(() -> GoogleOAuth2Config.validateCredentials(false, ID, ""))
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(ID));
    }

    @Test
    void 둘_다_있으면_Google_등록과_콜백_경로가_구성된다() throws Exception {
        GoogleOAuth2Wiring wiring = wiring(ID, SECRET);

        assertThat(wiring.enabled()).isTrue();
        var registration = wiring.registrations().findByRegistrationId("google");
        assertThat(registration).isNotNull();
        assertThat(registration.getClientId()).isEqualTo(ID);
        assertThat(registration.getRedirectUri()).isEqualTo("{baseUrl}/{action}/oauth2/code/{registrationId}");
        assertThat(registration.getScopes()).contains("openid", "email");
    }

    private static GoogleOAuth2Wiring wiring(String clientId, String clientSecret) throws Exception {
        Environment environment = mock(Environment.class);
        when(environment.acceptsProfiles(org.mockito.ArgumentMatchers.<org.springframework.core.env.Profiles>any()))
                .thenReturn(false);
        AuthCookies cookies = new AuthCookies(environment, new RefreshTokenProperties(1209600L));
        return new GoogleOAuth2Config().googleOAuth2Wiring(environment, clientId, clientSecret,
                mock(AuthRateLimitService.class), mock(ClientIpResolver.class), mock(HandlerExceptionResolver.class),
                mock(GoogleLoginService.class), mock(AuthApiService.class), cookies,
                mock(com.busanlocalfood.member.service.MemberAccountService.class));
    }
}
