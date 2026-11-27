package com.busanlocalfood.global.security;

import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.global.exception.ErrorCode;
import org.springframework.security.core.Authentication;

/**
 * 관리자 권한 검사 (FUNCTION_SPEC F1-09, DEVELOPMENT_RULES 서비스 계층 권한 검사).
 *
 * 인증 정보의 ROLE_ADMIN 권한만 본다. 요청 파라미터나 본문의 role 값은 보지 않는다.
 * - 인증이 없거나 회원 ID가 아니면 TOKEN_INVALID (401).
 * - 회원이지만 ADMIN이 아니면 FORBIDDEN (403).
 * 관리자 서비스의 모든 진입점에서 먼저 부른다. 컨트롤러 검사만으로 끝내지 않는다.
 */
public final class AdminAccess {

    static final String ROLE_ADMIN = "ROLE_ADMIN";

    private AdminAccess() {
    }

    /** 관리자이면 회원 ID를 돌려준다. */
    public static long requireAdmin(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof Long memberId)) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority -> ROLE_ADMIN.equals(authority.getAuthority()));
        if (!admin) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return memberId;
    }
}
