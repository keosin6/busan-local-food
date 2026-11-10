package com.busanlocalfood.member.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MemberTest {

    @Test
    void 이메일은_소문자와_공백_제거로_정규화된다() {
        Member member = Member.create("  User@Example.COM ", "$2a$10$hash", "부산먹보", null);

        assertThat(member.getEmail()).isEqualTo("user@example.com");
    }

    @Test
    void 신규_회원의_역할은_USER다() {
        Member member = Member.create("a@example.com", "$2a$10$hash", "부산먹보", null);

        assertThat(member.getRole()).isEqualTo(Role.USER);
    }

    @Test
    void Google_전용_회원은_password_hash가_NULL이다() {
        Member member = Member.create("g@example.com", null, "구글회원", "google-sub-1");

        assertThat(member.getPasswordHash()).isNull();
        assertThat(member.getGoogleSub()).isEqualTo("google-sub-1");
    }

    @Test
    void 비밀번호_회원은_google_sub가_NULL이다() {
        Member member = Member.create("p@example.com", "$2a$10$hash", "부산먹보", null);

        assertThat(member.getPasswordHash()).isEqualTo("$2a$10$hash");
        assertThat(member.getGoogleSub()).isNull();
    }

    @Test
    void 비밀번호와_Google_sub가_모두_없으면_생성할_수_없다() {
        assertThatThrownBy(() -> Member.create("x@example.com", null, "부산먹보", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Member.create("x@example.com", " ", "부산먹보", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
