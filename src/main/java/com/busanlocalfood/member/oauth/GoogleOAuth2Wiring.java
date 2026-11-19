package com.busanlocalfood.member.oauth;

import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;

/**
 * Google OAuth2 연결에 필요한 구성 요소 묶음. enabled가 false면 Google 로그인은 꺼져 있다.
 * 화면 체인이 enabled일 때만 oauth2Login을 구성한다.
 *
 * linkRequestResolver: 계정 연동 시작(POST /mypage/google/link)이 Authorization Request를 만들 때 쓴다.
 * sessionStrategy: 연동 모드에서 세션 ID를 바꾸지 않도록 하는 세션 전략이다.
 */
public record GoogleOAuth2Wiring(
        boolean enabled,
        ClientRegistrationRepository registrations,
        HttpSessionOAuth2AuthorizationRequestRepository authorizationRequests,
        OAuth2AuthorizationRequestResolver linkRequestResolver,
        GoogleOAuth2LoginGuardFilter guard,
        AuthenticationSuccessHandler success,
        AuthenticationFailureHandler failure,
        SessionAuthenticationStrategy sessionStrategy) {
}
