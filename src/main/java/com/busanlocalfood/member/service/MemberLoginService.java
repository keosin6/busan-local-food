package com.busanlocalfood.member.service;

import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.global.exception.ErrorCode;
import com.busanlocalfood.member.domain.Member;
import com.busanlocalfood.member.repository.MemberRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 자격 증명 검증 서비스 (F1-01).
 * 세션 생성, JWT 발급, Refresh Token 발급은 이 단계에서 연결하지 않는다.
 * 미가입 이메일, 비밀번호 불일치, Google 전용 계정은 모두 INVALID_CREDENTIALS로 같게 응답한다.
 */
@Service
public class MemberLoginService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    /** 미가입 이메일에도 BCrypt 비교를 한 번 수행해 응답 시간으로 가입 여부를 추측하기 어렵게 한다. */
    private final String dummyPasswordHash;

    public MemberLoginService(MemberRepository memberRepository, PasswordEncoder passwordEncoder) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /** 자격 증명이 맞으면 검증된 회원을 반환한다. 실패 시 회원 정보를 노출하지 않는다. */
    @Transactional(readOnly = true)
    public Member authenticate(String rawEmail, String rawPassword) {
        String email = MemberInputRules.requireEmail(rawEmail);
        MemberInputRules.requireLoginPassword(rawPassword);

        Member member = memberRepository.findByEmail(email).orElse(null);
        boolean hasPasswordHash = member != null && member.getPasswordHash() != null;

        // 모든 경로(정상, 비밀번호 불일치, 미가입, Google 전용)에서 BCrypt 검증을 정확히 한 번 수행한다.
        // 비밀번호 해시가 없으면(미가입, Google 전용) 더미 해시와 비교하여 응답 시간 차이를 줄인다.
        String hashToCompare = hasPasswordHash ? member.getPasswordHash() : dummyPasswordHash;
        boolean passwordMatches = passwordEncoder.matches(rawPassword, hashToCompare);

        // 실패 사유(미가입, 불일치, Google 전용)는 구분하지 않고 같은 오류로 응답한다.
        if (!hasPasswordHash || !passwordMatches) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        return member;
    }
}
