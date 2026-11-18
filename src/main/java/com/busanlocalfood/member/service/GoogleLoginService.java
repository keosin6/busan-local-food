package com.busanlocalfood.member.service;

import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.member.domain.Member;
import com.busanlocalfood.member.repository.MemberRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Google 콜백 판정 (API_SPEC 2.5). 프로필 값을 검증하고 다음 중 하나로 결정한다.
 * 순서: 값 검증 → 이메일 미검증 거부 → sub 연결 회원 로그인 → 같은 이메일 회원 존재(자동 연결 금지) → 신규 가입 진행.
 * 회원을 만들거나 연결하지 않는다. 세션과 토큰도 다루지 않는다.
 */
@Service
public class GoogleLoginService {

    private final MemberRepository memberRepository;

    public GoogleLoginService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Transactional(readOnly = true)
    public GoogleLoginDecision decide(String rawSub, String rawEmail, boolean emailVerified) {
        String sub = rawSub == null ? "" : rawSub.trim();
        if (sub.isEmpty() || sub.length() > 255) {
            return new GoogleLoginDecision.Invalid();
        }
        String email;
        try {
            email = MemberInputRules.requireEmail(rawEmail);
        } catch (BusinessException e) {
            return new GoogleLoginDecision.Invalid();
        }
        if (!emailVerified) {
            return new GoogleLoginDecision.Unverified();
        }
        Optional<Member> linked = memberRepository.findByGoogleSub(sub);
        if (linked.isPresent()) {
            return new GoogleLoginDecision.Login(linked.get());
        }
        if (memberRepository.existsByEmail(email)) {
            return new GoogleLoginDecision.EmailAccountExists();
        }
        return new GoogleLoginDecision.NewMember(sub, email);
    }

    /** 콜백 판정 결과. 자동 연결은 없다. */
    public sealed interface GoogleLoginDecision {

        /** sub가 연결된 기존 회원. 정식 로그인 대상이다. */
        record Login(Member member) implements GoogleLoginDecision {
        }

        /** sub가 없고 같은 이메일의 기존 회원이 있다. 자동 연결하지 않는다. */
        record EmailAccountExists() implements GoogleLoginDecision {
        }

        /** 이메일 미검증. 로그인과 가입을 모두 거부한다. */
        record Unverified() implements GoogleLoginDecision {
        }

        /** 신규 가입 진행 대상. 회원은 아직 만들지 않는다. */
        record NewMember(String googleSub, String email) implements GoogleLoginDecision {
        }

        /** 프로필 값이 없거나 형식이 맞지 않는다. */
        record Invalid() implements GoogleLoginDecision {
        }
    }
}
