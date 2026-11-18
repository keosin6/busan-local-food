package com.busanlocalfood.member.web;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 화면 템플릿의 로그인 상태 표시용. 화면 세션(Spring Security 컨텍스트)만 본다.
 * JWT 쿠키만 있어도 화면에서는 로그인 상태가 아니다.
 */
@ControllerAdvice
public class ScreenAuthAdvice {

    @ModelAttribute("loggedIn")
    public boolean loggedIn(Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)
                && authentication.getPrincipal() instanceof Long;
    }
}
