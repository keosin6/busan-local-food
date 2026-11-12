package com.busanlocalfood.member.ratelimit;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 신뢰 프록시 IP 목록. 비어 있으면 X-Forwarded-For를 신뢰하지 않는다.
 * 운영 기본값을 코드에 두지 않는다. 값은 설정 파일이나 환경 설정에서 명시적으로 지정한다.
 */
@ConfigurationProperties(prefix = "app.security")
public record TrustedProxyProperties(List<String> trustedProxies) {

    public TrustedProxyProperties {
        trustedProxies = trustedProxies == null ? List.of() : List.copyOf(trustedProxies);
    }
}
