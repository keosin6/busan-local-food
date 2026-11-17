package com.busanlocalfood.member.jwt;

import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * API 요청의 인증 객체. 검증된 Access Token에서만 만든다.
 * 주체는 회원 ID(JWT sub), 권한은 JWT role을 ROLE_<role> 형식으로 바꾼 값이다.
 * 토큰 원문은 보관하지 않는다 (credentials는 null).
 */
public final class AccessTokenAuthentication extends AbstractAuthenticationToken {

    private final long memberId;

    public AccessTokenAuthentication(AuthenticatedMember member) {
        super(List.of(new SimpleGrantedAuthority("ROLE_" + member.role().name())));
        this.memberId = member.memberId();
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return memberId;
    }

    @Override
    public String getName() {
        return Long.toString(memberId);
    }
}
