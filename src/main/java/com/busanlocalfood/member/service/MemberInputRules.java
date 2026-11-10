package com.busanlocalfood.member.service;

import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.global.exception.ErrorCode;
import com.busanlocalfood.member.domain.Member;
import java.util.regex.Pattern;

/**
 * 회원가입·로그인 입력 검증 규칙 (FUNCTION_SPEC F1-01 "회원가입 검증 규칙").
 * 실패하면 BusinessException으로 세부 오류 코드를 던진다. 입력값 원문은 메시지에 넣지 않는다.
 */
final class MemberInputRules {

    static final int EMAIL_MAX_LENGTH = 255;
    static final int NICKNAME_MIN_LENGTH = 2;
    static final int NICKNAME_MAX_LENGTH = 20;
    static final int PASSWORD_MIN_LENGTH = 8;
    static final int PASSWORD_MAX_LENGTH = 20;

    // 이메일: 공백 없이 @와 도메인 점을 포함하는 단순 형식. 복잡한 RFC 정규식은 사용하지 않는다.
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    // 닉네임: 한글 완성형, 영문, 숫자만 허용. 내부 공백과 특수문자 불가.
    private static final Pattern NICKNAME_PATTERN =
            Pattern.compile("^[가-힣A-Za-z0-9]{" + NICKNAME_MIN_LENGTH + "," + NICKNAME_MAX_LENGTH + "}$");
    private static final Pattern LETTER = Pattern.compile("[A-Za-z]");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");
    // ASCII 문장부호·기호 (영문과 숫자를 제외한 출력 가능 문자)
    private static final Pattern SPECIAL = Pattern.compile("\\p{Punct}");

    private MemberInputRules() {
    }

    /** 이메일을 정규화하고 필수·형식·길이를 검증한다. 중복 검사는 호출자가 수행한다. */
    static String requireEmail(String rawEmail) {
        if (rawEmail == null || rawEmail.isBlank()) {
            throw new BusinessException(ErrorCode.EMAIL_REQUIRED);
        }
        String normalized = Member.normalizeEmail(rawEmail);
        if (normalized.length() > EMAIL_MAX_LENGTH || !EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new BusinessException(ErrorCode.EMAIL_INVALID);
        }
        return normalized;
    }

    /** 닉네임의 앞뒤 공백을 제거하고 필수·길이·허용 문자를 검증한다. */
    static String requireNickname(String rawNickname) {
        if (rawNickname == null || rawNickname.isBlank()) {
            throw new BusinessException(ErrorCode.NICKNAME_REQUIRED);
        }
        String trimmed = rawNickname.trim();
        if (!NICKNAME_PATTERN.matcher(trimmed).matches()) {
            throw new BusinessException(ErrorCode.NICKNAME_INVALID);
        }
        return trimmed;
    }

    /**
     * 회원가입 비밀번호를 검증한다. 공백 제거·소문자 변환을 하지 않는다.
     * 비밀번호 확인값이 일치해야 한다.
     */
    static void requireSignupPassword(String password, String passwordConfirm) {
        if (password == null || password.isEmpty() || passwordConfirm == null || passwordConfirm.isEmpty()) {
            throw new BusinessException(ErrorCode.PASSWORD_REQUIRED);
        }
        if (!satisfiesPasswordPolicy(password) || !password.equals(passwordConfirm)) {
            throw new BusinessException(ErrorCode.PASSWORD_INVALID);
        }
    }

    /** 로그인 비밀번호는 필수 여부만 확인한다. 조합 규칙은 가입 시에만 적용한다. */
    static void requireLoginPassword(String password) {
        if (password == null || password.isEmpty()) {
            throw new BusinessException(ErrorCode.PASSWORD_REQUIRED);
        }
    }

    /**
     * 모든 Unicode 코드 포인트를 검사한다. 정규식 \s는 NBSP 등 일부 공백을 포함하지 않으므로 사용하지 않는다.
     */
    private static boolean containsWhitespace(String value) {
        return value.codePoints().anyMatch(cp -> Character.isWhitespace(cp) || Character.isSpaceChar(cp));
    }

    private static boolean satisfiesPasswordPolicy(String password) {
        return password.length() >= PASSWORD_MIN_LENGTH
                && password.length() <= PASSWORD_MAX_LENGTH
                && !containsWhitespace(password)
                && LETTER.matcher(password).find()
                && DIGIT.matcher(password).find()
                && SPECIAL.matcher(password).find();
    }
}
