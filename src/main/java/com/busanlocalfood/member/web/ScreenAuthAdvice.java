package com.busanlocalfood.member.web;

import com.busanlocalfood.member.oauth.GoogleOAuth2Wiring;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 화면 템플릿의 로그인 상태와 Google 로그인 사용 여부 표시용.
 * 로그인 상태는 화면 세션(Spring Security 컨텍스트)만 본다. JWT 쿠키만으로는 로그인되지 않는다.
 */
@ControllerAdvice
public class ScreenAuthAdvice {

    private final ObjectProvider<GoogleOAuth2Wiring> googleWiring;

    public ScreenAuthAdvice(ObjectProvider<GoogleOAuth2Wiring> googleWiring) {
        this.googleWiring = googleWiring;
    }

    @ModelAttribute("loggedIn")
    public boolean loggedIn(Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)
                && authentication.getPrincipal() instanceof Long;
    }

    @ModelAttribute("googleLoginEnabled")
    public boolean googleLoginEnabled() {
        GoogleOAuth2Wiring wiring = googleWiring.getIfAvailable();
        return wiring != null && wiring.enabled();
    }
}
