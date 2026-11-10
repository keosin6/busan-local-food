package com.busanlocalfood.member.domain;

/** 회원 권한. DB의 CHECK (role IN ('USER','ADMIN'))과 같은 값을 사용한다 (DATABASE_ERD 2.1). */
public enum Role {
    USER,
    ADMIN
}
