package com.busanlocalfood.member.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import java.util.List;
import static org.mockito.Mockito.when;

import com.busanlocalfood.member.domain.Role;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/** API JWT 인증 필터의 단위 동작. 검증기는 목으로 두고, 필터가 검증 결과를 어떻게 컨텍스트에 쓰는지 본다. */
class AccessTokenAuthenticationFilterTest {

    private final AccessTokenVerifier verifier = mock(AccessTokenVerifier.class);
    private final AccessTokenAuthenticationFilter filter = new AccessTokenAuthenticationFilter(verifier);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private Authentication run(MockHttpServletRequest request) throws Exception {
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest()).isNotNull();
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Test
    void 쿠키가_없으면_인증_없이_진행한다() throws Exception {
        assertThat(run(new MockHttpServletRequest("GET", "/api/me"))).isNull();
    }

    @Test
    void 검증된_토큰의_회원_ID와_ROLE을_인증_객체에_반영한다() throws Exception {
        when(verifier.verify("valid-token")).thenReturn(new AuthenticatedMember(7L, Role.ADMIN));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/me");
        request.setCookies(new Cookie(AccessTokenAuthenticationFilter.COOKIE_NAME, "valid-token"));

        Authentication auth = run(request);

        assertThat(auth).isInstanceOf(AccessTokenAuthentication.class);
        assertThat(auth.isAuthenticated()).isTrue();
        assertThat(auth.getPrincipal()).isEqualTo(7L);
        assertThat(auth.getCredentials()).isNull();
        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_ADMIN");
    }

    @Test
    void 검증에_실패한_토큰은_인증_없이_진행하고_이전_컨텍스트를_이어받지_않는다() throws Exception {
        // 이전 요청이나 다른 경로에서 남은 인증 정보가 있어도 재사용되지 않아야 한다.
        SecurityContextHolder.getContext().setAuthentication(new AccessTokenAuthentication(new AuthenticatedMember(9L, Role.USER)));
        when(verifier.verify(anyString())).thenThrow(new InvalidAccessTokenException("token rejected"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/me");
        request.setCookies(new Cookie(AccessTokenAuthenticationFilter.COOKIE_NAME, "forged-token"));

        assertThat(run(request)).isNull();
    }

    @Test
    void Bearer_헤더의_토큰을_검증하고_쿠키는_무시한다() throws Exception {
        when(verifier.verify("bearer-token")).thenReturn(new AuthenticatedMember(7L, Role.USER));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/me");
        request.addHeader("Authorization", "Bearer bearer-token");
        request.setCookies(new Cookie(AccessTokenAuthenticationFilter.COOKIE_NAME, "cookie-token"));

        assertThat(run(request).getPrincipal()).isEqualTo(7L);
        verify(verifier, never()).verify("cookie-token");
    }

    @Test
    void 잘못된_Bearer_형식은_쿠키로_대체하지_않고_검증기를_호출하지_않는다() throws Exception {
        for (String header : List.of("Bearer ", "Bearer a b", "Basic abc", "Bearer", "")) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/me");
            request.addHeader("Authorization", header);
            request.setCookies(new Cookie(AccessTokenAuthenticationFilter.COOKIE_NAME, "valid-cookie-token"));

            assertThat(run(request)).isNull();
        }
        verify(verifier, never()).verify(anyString());
    }

    @Test
    void Bearer_스킴과_토큰_앞뒤_공백을_처리한다() {
        assertThat(AccessTokenAuthenticationFilter.bearerToken("bearer  tok ")).isEqualTo("tok");
        assertThat(AccessTokenAuthenticationFilter.bearerToken("BEARER tok")).isEqualTo("tok");
        assertThat(AccessTokenAuthenticationFilter.bearerToken("Bearer\ttok")).isNull();
        assertThat(AccessTokenAuthenticationFilter.bearerToken("Token tok")).isNull();
    }

    @Test
    void 인증_실패_시_쿠키를_변경하지_않는다() throws Exception {
        when(verifier.verify(anyString())).thenThrow(new InvalidAccessTokenException("token rejected"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/me");
        request.addHeader("Authorization", "Bearer forged");

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getCookies()).isEmpty();
        assertThat(response.getHeaders("Set-Cookie")).isEmpty();
    }

    @Test
    void 빈_쿠키_값은_검증기를_호출하지_않는다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/me");
        request.setCookies(new Cookie(AccessTokenAuthenticationFilter.COOKIE_NAME, " "));

        assertThat(run(request)).isNull();
    }
}
