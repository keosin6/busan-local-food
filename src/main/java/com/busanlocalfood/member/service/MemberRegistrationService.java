package com.busanlocalfood.member.service;

import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.global.exception.ErrorCode;
import com.busanlocalfood.member.domain.Member;
import com.busanlocalfood.member.repository.MemberRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원가입 서비스 (F1-01). JWT·세션·Refresh Token 발급은 이 단계에서 하지 않는다.
 * 중복 검사는 사전 안내용이며, 최종 방어는 DB UNIQUE 제약(uk_member_email, uk_member_nickname)이다.
 */
@Service
public class MemberRegistrationService {

    static final String EMAIL_UNIQUE_CONSTRAINT = "uk_member_email";
    static final String NICKNAME_UNIQUE_CONSTRAINT = "uk_member_nickname";

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    public MemberRegistrationService(MemberRepository memberRepository, PasswordEncoder passwordEncoder) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 신규 일반 회원을 등록한다. 역할은 USER이며 Google 연동은 없다.
     * 검증 순서: 이메일 → 닉네임 → 비밀번호 → 이메일 중복 → 닉네임 중복 → 저장.
     */
    @Transactional
    public Member register(String rawEmail, String rawPassword, String passwordConfirm, String rawNickname) {
        String email = MemberInputRules.requireEmail(rawEmail);
        String nickname = MemberInputRules.requireNickname(rawNickname);
        MemberInputRules.requireSignupPassword(rawPassword, passwordConfirm);

        // 이메일과 닉네임이 동시에 중복이면 이메일 오류를 우선한다.
        if (memberRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_DUPLICATED);
        }
        if (memberRepository.existsByNickname(nickname)) {
            throw new BusinessException(ErrorCode.NICKNAME_DUPLICATED);
        }

        // 원문 비밀번호는 여기서 해시로만 바뀌고, Member에는 해시만 전달한다.
        String passwordHash = passwordEncoder.encode(rawPassword);
        Member member = Member.create(email, passwordHash, nickname, null);
        try {
            return memberRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException e) {
            throw translateUniqueViolation(e);
        }
    }

    /**
     * 사전 검사 이후 동시 요청으로 UNIQUE 제약이 위반된 경우를 중복 오류로 바꾼다.
     * 제약 이름을 알 수 없거나 다른 무결성 오류이면 원래 예외를 그대로 전파한다.
     */
    private RuntimeException translateUniqueViolation(DataIntegrityViolationException e) {
        String constraintName = findConstraintName(e);
        if (constraintName != null) {
            if (constraintName.contains(EMAIL_UNIQUE_CONSTRAINT)) {
                return new BusinessException(ErrorCode.EMAIL_DUPLICATED);
            }
            if (constraintName.contains(NICKNAME_UNIQUE_CONSTRAINT)) {
                return new BusinessException(ErrorCode.NICKNAME_DUPLICATED);
            }
        }
        return e;
    }

    static String findConstraintName(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ConstraintViolationException cve) {
                return cve.getConstraintName();
            }
            current = current.getCause();
        }
        return null;
    }
}
