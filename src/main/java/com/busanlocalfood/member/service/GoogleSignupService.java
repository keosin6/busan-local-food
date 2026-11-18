package com.busanlocalfood.member.service;

import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.global.exception.ErrorCode;
import com.busanlocalfood.member.domain.Member;
import com.busanlocalfood.member.repository.MemberRepository;
import com.busanlocalfood.member.service.AuthApiService.IssuedSession;
import com.busanlocalfood.member.oauth.GoogleSignupPending;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Google 신규 가입 완료 (API_SPEC 2.5.1). 닉네임을 검증하고 회원을 만든 뒤 토큰을 발급한다.
 * 회원 생성과 토큰 발급은 한 트랜잭션이다. 토큰 발급이 실패하면 회원도 만들어지지 않는다.
 * 중복 검사는 사전 안내용이다. 최종 방어는 DB UNIQUE 제약(uk_member_email, uk_member_nickname, uk_member_google_sub)이다.
 */
@Service
public class GoogleSignupService {

    static final String GOOGLE_SUB_UNIQUE_CONSTRAINT = "uk_member_google_sub";

    private final MemberRepository memberRepository;
    private final AuthApiService authService;

    public GoogleSignupService(MemberRepository memberRepository, AuthApiService authService) {
        this.memberRepository = memberRepository;
        this.authService = authService;
    }

    @Transactional
    public IssuedSession complete(GoogleSignupPending pending, String rawNickname) {
        String nickname = MemberInputRules.requireNickname(rawNickname);
        if (memberRepository.existsByNickname(nickname)) {
            throw new BusinessException(ErrorCode.NICKNAME_DUPLICATED);
        }
        if (memberRepository.existsByEmail(pending.email())) {
            throw new BusinessException(ErrorCode.EMAIL_DUPLICATED);
        }
        if (memberRepository.findByGoogleSub(pending.googleSub()).isPresent()) {
            throw new BusinessException(ErrorCode.GOOGLE_ALREADY_LINKED);
        }
        Member member = insertMember(pending, nickname);
        return authService.issueSession(member);
    }

    /**
     * 사전 검사 뒤 저장한다. 동시 요청으로 UNIQUE 제약이 깨지면 같은 의미의 BusinessException으로 바꾼다.
     * 제약 이름을 알 수 없으면 원래 예외를 그대로 전파한다.
     */
    Member insertMember(GoogleSignupPending pending, String nickname) {
        try {
            return memberRepository.saveAndFlush(Member.create(pending.email(), null, nickname, pending.googleSub()));
        } catch (DataIntegrityViolationException e) {
            String constraint = MemberRegistrationService.findConstraintName(e);
            if (constraint != null) {
                if (constraint.contains(MemberRegistrationService.NICKNAME_UNIQUE_CONSTRAINT)) {
                    throw new BusinessException(ErrorCode.NICKNAME_DUPLICATED);
                }
                if (constraint.contains(MemberRegistrationService.EMAIL_UNIQUE_CONSTRAINT)) {
                    throw new BusinessException(ErrorCode.EMAIL_DUPLICATED);
                }
                if (constraint.contains(GOOGLE_SUB_UNIQUE_CONSTRAINT)) {
                    throw new BusinessException(ErrorCode.GOOGLE_ALREADY_LINKED);
                }
            }
            throw e;
        }
    }
}
