package com.busanlocalfood.member.web;

import com.busanlocalfood.member.domain.Member;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Optional;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

/**
 * 화면 세션 생성 (API_SPEC 2.9, 2.10). 화면 체인의 HttpSession에 인증 정보를 저장한다.
 * 주체는 회원 ID(Long), 권한은 ROLE_<role>이다. API JWT 인증과 섞지 않는다.
 *
 * 세션 고정 공격 방어: 로그인이나 가입 성공 시 기존 세션을 버리고 새 세션 ID로 인증 정보를 저장한다.
 */
public final class ScreenSession {

    private static final HttpSessionSecurityContextRepository CONTEXT_REPOSITORY =
            new HttpSessionSecurityContextRepository();

    private ScreenSession() {
    }

    /**
     * 현재 화면 세션의 로그인 회원 ID. 로그인되지 않았거나 익명이면 빈 값이다.
     * JWT 쿠키만으로는 로그인으로 보지 않는다 (ScreenAuthAdvice와 같은 기준).
     */
    public static Optional<Long> currentMemberId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return authentication.getPrincipal() instanceof Long id ? Optional.of(id) : Optional.empty();
    }

    public static void establish(Member member, HttpServletRequest request, HttpServletResponse response) {
        // 로그인 전 세션(공격자가 심은 세션 ID 포함)과 그 속성을 모두 버린다.
        var previous = request.getSession(false);
        if (previous != null) {
            previous.invalidate();
        }
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                member.getId(), null, List.of(new SimpleGrantedAuthority("ROLE_" + member.getRole().name())));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        CONTEXT_REPOSITORY.saveContext(context, request, response);
    }
}
