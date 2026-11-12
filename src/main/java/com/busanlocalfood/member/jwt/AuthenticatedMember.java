package com.busanlocalfood.member.jwt;

import com.busanlocalfood.member.domain.Role;

/** 검증에 성공한 Access Token의 인증 정보. */
public record AuthenticatedMember(long memberId, Role role) {
}
