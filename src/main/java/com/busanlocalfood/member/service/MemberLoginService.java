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

        Optional<Member> found = memberRepository.findByEmail(email);
        if (found.isEmpty()) {
            passwordEncoder.matches(rawPassword, dummyPasswordHash);
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        Member member = found.get();
        // Google 전용 계정은 비밀번호 해시가 없으므로 BCrypt 비교 없이 같은 오류를 낸다.
        if (member.getPasswordHash() == null) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!passwordEncoder.matches(rawPassword, member.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        return member;
    }
}
