package com.busanlocalfood.member.service;

import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.global.exception.ErrorCode;
import com.busanlocalfood.global.exception.RateLimitExceededException;
import com.busanlocalfood.member.domain.Member;
import com.busanlocalfood.member.domain.Role;
import com.busanlocalfood.member.jwt.AccessTokenIssuer;
import com.busanlocalfood.member.jwt.IssuedAccessToken;
import com.busanlocalfood.member.ratelimit.AuthRateLimitService;
import com.busanlocalfood.member.ratelimit.LoginAttemptResult;
import com.busanlocalfood.member.refresh.IssuedRefreshToken;
import com.busanlocalfood.member.refresh.RefreshTokenRotation;
import com.busanlocalfood.member.refresh.RefreshTokenService;
import com.busanlocalfood.member.repository.MemberRepository;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API 인증 흐름 (API_SPEC 2.1~2.4, 2.8). 컨트롤러는 HTTP 입출력과 쿠키만 다루고,
 * 가입·로그인·재발급·로그아웃의 순서와 판정은 여기서 정한다. 세션은 만들지 않는다.
 *
 * 회원가입 집계(SignupRateLimitFilter)는 필터가 맡으므로 이 서비스에서 다시 집계하지 않는다.
 */
@Service
public class AuthApiService {

    private final MemberRegistrationService registrationService;
    private final MemberLoginService loginService;
    private final AuthRateLimitService rateLimitService;
    private final RefreshTokenService refreshTokenService;
    private final AccessTokenIssuer accessTokenIssuer;
    private final MemberRepository memberRepository;

    public AuthApiService(MemberRegistrationService registrationService,
                          MemberLoginService loginService,
                          AuthRateLimitService rateLimitService,
                          RefreshTokenService refreshTokenService,
                          AccessTokenIssuer accessTokenIssuer,
                          MemberRepository memberRepository) {
        this.registrationService = registrationService;
        this.loginService = loginService;
        this.rateLimitService = rateLimitService;
        this.refreshTokenService = refreshTokenService;
        this.accessTokenIssuer = accessTokenIssuer;
        this.memberRepository = memberRepository;
    }

    /**
     * 가입 후 자동 로그인한다. 가입과 토큰 발급을 한 트랜잭션에서 하므로, 발급이 실패하면 가입도 되돌린다.
     */
    @Transactional
    public IssuedSession signup(String rawEmail, String rawPassword, String passwordConfirm, String rawNickname) {
        Member member = registrationService.register(rawEmail, rawPassword, passwordConfirm, rawNickname);
        return issueSession(member);
    }

    /**
     * 로그인. 입력 형식은 판정 전에 검증한다. 제한 판정과 자격 증명 확인은 AuthRateLimitService.attemptLogin이
     * 한 잠금 구간에서 처리한다. 제한 중에는 자격 증명을 확인하지 않는다.
     * 제한 초과는 RateLimitExceededException(429), 자격 증명 실패는 INVALID_CREDENTIALS(401)다.
     */
    public IssuedSession login(String rawEmail, String rawPassword, String clientIp) {
        MemberInputRules.requireEmail(rawEmail);
        MemberInputRules.requireLoginPassword(rawPassword);

        AtomicReference<Member> verified = new AtomicReference<>();
        LoginAttemptResult result = rateLimitService.attemptLogin(rawEmail, clientIp, () -> {
            Optional<Member> member = loginService.verifyCredentials(rawEmail, rawPassword);
            member.ifPresent(verified::set);
            return member.isPresent();
        });
        return switch (result) {
            case LoginAttemptResult.Authenticated authenticated -> issueSession(verified.get());
            case LoginAttemptResult.InvalidCredentials invalid -> throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
            case LoginAttemptResult.Limited limited -> throw new RateLimitExceededException(limited.retryAfterSeconds());
        };
    }

    /**
     * Refresh Token 회전. 판정 순서와 재사용 폐기는 RefreshTokenService가 한다.
     * 정상 갱신일 때만 새 Access Token을 만든다. 재사용 탐지는 예외 없이 결과로 돌아오므로 폐기가 커밋된다.
     */
    public Renewal renew(String rawRefreshToken) {
        RefreshTokenRotation rotation = refreshTokenService.rotate(rawRefreshToken);
        return switch (rotation) {
            case RefreshTokenRotation.Rotated rotated -> new Renewal.Renewed(
                    new IssuedTokens(accessTokenIssuer.issue(rotated.memberId(), rotated.role()), rotated.next()));
            case RefreshTokenRotation.RateLimited limited -> new Renewal.RateLimited(limited.retryAfterSeconds());
            case RefreshTokenRotation.Rejected rejected -> new Renewal.Rejected();
            case RefreshTokenRotation.ReuseDetected reused -> new Renewal.Rejected();
        };
    }

    /** 로그아웃: 쿠키의 Refresh Token을 폐기한다. 없거나 이미 폐기된 토큰이면 아무 일도 하지 않는다. */
    public void logout(String rawRefreshToken) {
        refreshTokenService.logout(rawRefreshToken);
    }

    /** 현재 인증된 회원을 DB에서 확인한다. 회원이 없으면 인증 실패(TOKEN_INVALID)로 본다. */
    @Transactional(readOnly = true)
    public Member findMember(long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TOKEN_INVALID));
    }

    /** 회원에게 화면 세션용 정보와 API 토큰 한 쌍을 발급한다. 화면 세션 생성은 호출자가 ScreenSession으로 한다. */
    public IssuedSession issueSession(Member member) {
        IssuedRefreshToken refresh = refreshTokenService.issueForNewLogin(member.getId());
        IssuedAccessToken access = accessTokenIssuer.issue(member.getId(), member.getRole());
        return new IssuedSession(member, new IssuedTokens(access, refresh));
    }

    /** 응답에 실을 토큰 한 쌍. 원문은 쿠키로만 내보낸다. */
    public record IssuedTokens(IssuedAccessToken access, IssuedRefreshToken refresh) {
    }

    /** 가입·로그인 결과: 회원 정보와 토큰 한 쌍. */
    public record IssuedSession(Member member, IssuedTokens tokens) {
    }

    /** 재발급 결과. Rejected는 재사용 탐지와 만료·위조·행 없음을 같은 401로 묶는다. */
    public sealed interface Renewal {
        record Renewed(IssuedTokens tokens) implements Renewal {
        }

        record Rejected() implements Renewal {
        }

        record RateLimited(long retryAfterSeconds) implements Renewal {
        }
    }
}
