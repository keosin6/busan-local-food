package com.busanlocalfood.member.ratelimit;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 클라이언트 IP 판별. 신뢰 프록시가 전달한 X-Forwarded-For만 본다 (API_SPEC 1.4, T16).
 *
 * 규칙:
 * - Remote Address가 신뢰 프록시가 아니면 X-Forwarded-For를 무시한다.
 * - 신뢰 프록시 목록이 비어 있으면 어떤 헤더도 신뢰하지 않는다.
 * - 체인은 오른쪽부터 본다. 신뢰 프록시가 아닌 첫 항목이 클라이언트다. 왼쪽 항목은 임의로 넣을 수 있으므로 무시한다.
 * - 체인에 잘못된 항목이 있으면 헤더를 무시하고 Remote Address를 쓴다.
 * 결과는 HMAC 처리 후에만 식별자로 쓴다. 원문 IP나 헤더는 로그에 남기지 않는다.
 */
public final class ClientIpResolver {

    /** Remote Address를 해석할 수 없을 때 쓰는 값. 이 값끼리는 같은 키로 집계되어 보수적으로 동작한다. */
    static final String UNKNOWN = "unknown";
    private static final int MAX_HEADER_LENGTH = 512;

    private final Set<String> trustedProxies;

    public ClientIpResolver(List<String> trustedProxyAddresses) {
        Set<String> canonical = new HashSet<>();
        for (String raw : trustedProxyAddresses) {
            Optional<String> ip = IpAddresses.canonical(raw);
            if (ip.isEmpty()) {
                // 설정 값은 로그에 남기지 않는다. 어느 항목이 잘못됐는지는 설정 파일에서 확인한다.
                throw new IllegalStateException("app.security.trusted-proxies에 올바르지 않은 IP 주소가 있습니다.");
            }
            canonical.add(ip.get());
        }
        this.trustedProxies = Set.copyOf(canonical);
    }

    public String resolve(String remoteAddress, String forwardedFor) {
        String remote = IpAddresses.canonical(remoteAddress).orElse(UNKNOWN);
        if (UNKNOWN.equals(remote) || trustedProxies.isEmpty() || !trustedProxies.contains(remote)) {
            return remote;
        }
        if (forwardedFor == null || forwardedFor.isBlank() || forwardedFor.length() > MAX_HEADER_LENGTH) {
            return remote;
        }
        String[] hops = forwardedFor.split(",", -1);
        for (int i = hops.length - 1; i >= 0; i--) {
            Optional<String> hop = IpAddresses.canonical(hops[i]);
            if (hop.isEmpty()) {
                return remote;
            }
            if (trustedProxies.contains(hop.get())) {
                continue;
            }
            return hop.get();
        }
        // 모든 항목이 신뢰 프록시라면 가장 왼쪽 항목을 쓴다.
        return IpAddresses.canonical(hops[0]).orElse(remote);
    }
}
