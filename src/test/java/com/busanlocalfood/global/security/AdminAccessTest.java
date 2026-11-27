package com.busanlocalfood.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.global.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** 관리자 권한 검사 (F1-09). 인증 정보의 ROLE_ADMIN만 보고, 인증이 없거나 회원 ID가 아니면 401이다. */
class AdminAccessTest {

    private static Authentication member(long id, String role) {
        return new UsernamePasswordAuthenticationToken(id, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private static void assertCode(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    @Test
    void 관리자_권한이면_회원_ID를_돌려준다() {
        assertThat(AdminAccess.requireAdmin(member(9L, "ADMIN"))).isEqualTo(9L);
    }

    @Test
    void USER는_FORBIDDEN이다() {
        assertCode(() -> AdminAccess.requireAdmin(member(7L, "USER")), ErrorCode.FORBIDDEN);
    }

    @Test
    void 인증이_없으면_TOKEN_INVALID이다() {
        assertCode(() -> AdminAccess.requireAdmin(null), ErrorCode.TOKEN_INVALID);
    }

    @Test
    void 익명_인증은_회원이_아니므로_TOKEN_INVALID이다() {
        Authentication anonymous = new AnonymousAuthenticationToken("key", "anonymousUser",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        assertCode(() -> AdminAccess.requireAdmin(anonymous), ErrorCode.TOKEN_INVALID);
    }
}
