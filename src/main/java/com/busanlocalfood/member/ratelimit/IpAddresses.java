package com.busanlocalfood.member.ratelimit;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * IP 문자열 검증과 정규화. 표준 Java API만 쓴다. DNS 조회를 하지 않도록 리터럴 형식만 허용한다.
 */
final class IpAddresses {

    private static final int MAX_LENGTH = 45;
    private static final Pattern IPV4 = Pattern.compile(
            "^(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}$");
    private static final Pattern IPV6_CHARS = Pattern.compile("^[0-9A-Fa-f:.]+$");

    private IpAddresses() {
    }

    /** 유효한 IP 리터럴이면 정규화한 문자열을 돌려준다. 그 밖의 값은 빈 Optional이다. */
    static Optional<String> canonical(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String value = raw.trim();
        if (value.isEmpty() || value.length() > MAX_LENGTH) {
            return Optional.empty();
        }
        if (value.indexOf(':') >= 0) {
            if (!IPV6_CHARS.matcher(value).matches()) {
                return Optional.empty();
            }
            try {
                // 콜론이 있는 리터럴이므로 DNS 조회 없이 주소로만 해석한다.
                return Optional.of(InetAddress.getByName(value).getHostAddress());
            } catch (UnknownHostException e) {
                return Optional.empty();
            }
        }
        return IPV4.matcher(value).matches() ? Optional.of(value) : Optional.empty();
    }
}
