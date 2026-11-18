package com.busanlocalfood.member.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.busanlocalfood.member.ratelimit.AuthRateLimitService;
import com.busanlocalfood.member.ratelimit.ClientIpResolver;
import com.busanlocalfood.member.ratelimit.RateLimitDecision;
import jakarta.servlet.FilterChain;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

/** Google 로그인 요청 제한 필터의 순서와 상태 정리. */
class GoogleOAuth2LoginGuardFilterTest {

    private static final String START = "/oauth2/authorization/google";
    private static final String CALLBACK = "/login/oauth2/code/google";

    private final AuthRateLimitService rateLimit = mock(AuthRateLimitService.class);
    private final HandlerExceptionResolver resolver = mock(HandlerExceptionResolver.class);
    private final HttpSessionOAuth2AuthorizationRequestRepository requests =
            mock(HttpSessionOAuth2AuthorizationRequestRepository.class);
    private final GoogleOAuth2LoginGuardFilter filter = new GoogleOAuth2LoginGuardFilter(
            rateLimit, new ClientIpResolver(List.of()), resolver, requests);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 시작_요청_제한_초과는_후속_처리_없이_429로_끝난다() throws Exception {
        when(rateLimit.recordGoogleOAuthRequest(any())).thenReturn(new RateLimitDecision.Limited(60));
        when(resolver.resolveException(any(), any(), any(), any())).thenReturn(new ModelAndView());
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(get(START), new MockHttpServletResponse(), chain);

        verify(chain, never()).doFilter(any(), any());
        verify(rateLimit, times(1)).recordGoogleOAuthRequest(any());
        verify(requests, never()).removeAuthorizationRequest(any(), any());
    }

    @Test
    void 시작_요청은_허용되면_한_번만_집계하고_다음_필터로_넘긴다() throws Exception {
        when(rateLimit.recordGoogleOAuthRequest(any())).thenReturn(new RateLimitDecision.Allowed());
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(get(START), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
        verify(rateLimit, times(1)).recordGoogleOAuthRequest(any());
    }

    @Test
    void 콜백_제한_초과는_이_요청의_임시_상태만_지우고_기존_인증은_건드리지_않는다() throws Exception {
        Authentication existing = new UsernamePasswordAuthenticationToken(7L, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(existing);
        when(rateLimit.recordGoogleOAuthRequest(any())).thenReturn(new RateLimitDecision.Limited(60));
        when(resolver.resolveException(any(), any(), any(), any())).thenReturn(new ModelAndView());
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = get(CALLBACK);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        verify(chain, never()).doFilter(any(), any());
        verify(requests, times(1)).removeAuthorizationRequest(any(), any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(existing);
    }

    @Test
    void 콜백은_처리_전에_현재_인증_상태를_기록해_둔다() throws Exception {
        Authentication existing = new UsernamePasswordAuthenticationToken(7L, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(existing);
        when(rateLimit.recordGoogleOAuthRequest(any())).thenReturn(new RateLimitDecision.Allowed());
        MockHttpServletRequest request = get(CALLBACK);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(request.getAttribute(GoogleOAuth2LoginGuardFilter.PREVIOUS_AUTHENTICATION)).isSameAs(existing);
    }

    @Test
    void 관련_없는_경로와_POST는_집계하지_않는다() throws Exception {
        filter.doFilter(get("/restaurants"), new MockHttpServletResponse(), new MockFilterChain());
        MockHttpServletRequest post = get(START);
        post.setMethod("POST");
        filter.doFilter(post, new MockHttpServletResponse(), new MockFilterChain());

        verify(rateLimit, never()).recordGoogleOAuthRequest(any());
    }

    private static MockHttpServletRequest get(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRemoteAddr("203.0.113.10");
        return request;
    }
}
