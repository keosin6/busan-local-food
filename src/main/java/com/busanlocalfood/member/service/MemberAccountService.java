package com.busanlocalfood.member.service;

import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.global.exception.ErrorCode;
import com.busanlocalfood.member.domain.Member;
import com.busanlocalfood.member.repository.MemberRepository;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 화면 계정 관리: Google 계정 연결(API_SPEC 2.6.1), 해제(2.7), 로컬 비밀번호 설정(2.7.1).
 *
 * 세 작업 모두 회원 행을 SELECT ... FOR UPDATE로 잠근 뒤 판정한다. 같은 회원에 대한 동시 요청은 직렬화된다.
 * 업무 판정 결과는 예외가 아니라 결과 값으로 돌려준다. 트랜잭션 안에서 예외를 던지면 호출자가 결과를 다루기 어렵다.
 * 단 Google sub의 UNIQUE 위반은 같은 트랜잭션에서 복구할 수 없으므로 BusinessException으로 바꿔 트랜잭션 밖으로 던진다.
 *
 * 세션과 JWT는 이 서비스가 만들거나 지우지 않는다. 호출자가 HTTP 계층에서 다룬다.
 */
@Service
public class MemberAccountService {

    static final String GOOGLE_SUB_UNIQUE_CONSTRAINT = "uk_member_google_sub";

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    public MemberAccountService(MemberRepository memberRepository, PasswordEncoder passwordEncoder) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public enum LinkOutcome { LINKED, ALREADY_LINKED_TO_OTHER, ALREADY_LINKED, MEMBER_NOT_FOUND }

    public enum UnlinkOutcome { UNLINKED, NOT_LINKED, PASSWORD_REQUIRED, INVALID_CREDENTIALS, MEMBER_NOT_FOUND }

    public enum PasswordOutcome { SET, ALREADY_SET, INVALID, MEMBER_NOT_FOUND }

    /**
     * Google sub를 회원에게 연결한다. 연동 대상 회원은 호출자가 세션에서 확보한 회원 ID다. 이메일로 찾지 않는다.
     * - 같은 회원에게 같은 sub가 이미 있으면 변경 없이 LINKED (멱등).
     * - 다른 회원에게 있으면 ALREADY_LINKED_TO_OTHER.
     * - 이 회원에게 다른 sub가 이미 있으면 ALREADY_LINKED.
     * - 동시 요청으로 UNIQUE가 깨지면 GOOGLE_ALREADY_LINKED BusinessException을 던진다.
     */
    @Transactional
    public LinkOutcome linkGoogle(long memberId, String googleSub) {
        Optional<Member> locked = memberRepository.findByIdForUpdate(memberId);
        if (locked.isEmpty()) {
            return LinkOutcome.MEMBER_NOT_FOUND;
        }
        Member member = locked.get();
        Optional<Member> owner = memberRepository.findByGoogleSub(googleSub);
        if (owner.isPresent()) {
            return owner.get().getId().equals(memberId) ? LinkOutcome.LINKED : LinkOutcome.ALREADY_LINKED_TO_OTHER;
        }
        if (member.isGoogleLinked()) {
            return LinkOutcome.ALREADY_LINKED;
        }
        member.attachGoogleSub(googleSub);
        try {
            memberRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException e) {
            if (MemberRegistrationService.findConstraintName(e) != null
                    && MemberRegistrationService.findConstraintName(e).contains(GOOGLE_SUB_UNIQUE_CONSTRAINT)) {
                throw new BusinessException(ErrorCode.GOOGLE_ALREADY_LINKED);
            }
            throw e;
        }
        return LinkOutcome.LINKED;
    }

    /**
     * Google 연결을 해제한다 (API_SPEC 2.7). 순서: 연결 여부 → 비밀번호 존재 → 비밀번호 검증.
     * 비밀번호 검증은 BCrypt로 하며, 빈 입력도 불일치로 본다.
     */
    @Transactional
    public UnlinkOutcome unlinkGoogle(long memberId, String rawPassword) {
        Optional<Member> locked = memberRepository.findByIdForUpdate(memberId);
        if (locked.isEmpty()) {
            return UnlinkOutcome.MEMBER_NOT_FOUND;
        }
        Member member = locked.get();
        if (!member.isGoogleLinked()) {
            return UnlinkOutcome.NOT_LINKED;
        }
        if (!member.hasPassword()) {
            return UnlinkOutcome.PASSWORD_REQUIRED;
        }
        if (rawPassword == null || rawPassword.isEmpty()
                || !passwordEncoder.matches(rawPassword, member.getPasswordHash())) {
            return UnlinkOutcome.INVALID_CREDENTIALS;
        }
        member.detachGoogleSub();
        memberRepository.saveAndFlush(member);
        return UnlinkOutcome.UNLINKED;
    }

    /**
     * Google 전용 회원에게 로컬 비밀번호를 설정한다 (API_SPEC 2.7.1). 순서: 이미 설정됨 → 검증 → 저장.
     * 비밀번호 규칙은 회원가입과 같은 MemberInputRules를 쓴다. 원문은 해시로만 저장한다.
     * 동시 요청은 행 잠금으로 직렬화되므로, 두 번째 요청은 ALREADY_SET이 된다.
     */
    @Transactional
    public PasswordOutcome setLocalPassword(long memberId, String password, String passwordConfirm) {
        Optional<Member> locked = memberRepository.findByIdForUpdate(memberId);
        if (locked.isEmpty()) {
            return PasswordOutcome.MEMBER_NOT_FOUND;
        }
        Member member = locked.get();
        if (member.hasPassword()) {
            return PasswordOutcome.ALREADY_SET;
        }
        try {
            MemberInputRules.requireSignupPassword(password, passwordConfirm);
        } catch (BusinessException e) {
            return PasswordOutcome.INVALID;
        }
        member.assignPasswordHash(passwordEncoder.encode(password));
        memberRepository.saveAndFlush(member);
        return PasswordOutcome.SET;
    }
}
