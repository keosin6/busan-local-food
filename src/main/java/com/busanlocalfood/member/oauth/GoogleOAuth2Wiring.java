package com.busanlocalfood.member.oauth;

import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

/**
 * Google OAuth2 연결에 필요한 구성 요소 묶음. enabled가 false면 Google 로그인은 꺼져 있다.
 * 화면 체인이 enabled일 때만 oauth2Login을 구성한다.
 */
public record GoogleOAuth2Wiring(
        boolean enabled,
        ClientRegistrationRepository registrations,
        HttpSessionOAuth2AuthorizationRequestRepository authorizationRequests,
        GoogleOAuth2LoginGuardFilter guard,
        AuthenticationSuccessHandler success,
        AuthenticationFailureHandler failure) {
}
