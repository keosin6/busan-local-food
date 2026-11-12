package com.busanlocalfood.member.jwt;

import com.busanlocalfood.member.domain.Role;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/** Access Token 검증. 성공하면 회원 ID와 권한을 돌려준다. */
public class AccessTokenVerifier {

    private final JwtDecoder decoder;

    public AccessTokenVerifier(JwtDecoder decoder) {
        this.decoder = decoder;
    }

    public AuthenticatedMember verify(String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidAccessTokenException("token missing");
        }
        Jwt jwt;
        try {
            jwt = decoder.decode(token);
        } catch (JwtException | IllegalArgumentException e) {
            // 서명, 알고리즘, 발급자, 대상, 만료, 클레임 검증 실패를 모두 같은 예외로 바꾼다.
            throw new InvalidAccessTokenException("token rejected", e);
        }
        long memberId;
        try {
            memberId = Long.parseLong(jwt.getSubject());
        } catch (NumberFormatException e) {
            throw new InvalidAccessTokenException("subject format", e);
        }
        Object role = jwt.getClaims().get(JwtSpec.CLAIM_ROLE);
        if (!(role instanceof String value) || !JwtValidators.isKnownRole(value)) {
            throw new InvalidAccessTokenException("role value");
        }
        return new AuthenticatedMember(memberId, Role.valueOf((String) role));
    }
}
