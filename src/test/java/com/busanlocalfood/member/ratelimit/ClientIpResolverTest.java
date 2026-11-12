package com.busanlocalfood.member.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClientIpResolverTest {

    private static final String PROXY_1 = "10.0.0.1";
    private static final String PROXY_2 = "10.0.0.2";

    private final ClientIpResolver resolver = new ClientIpResolver(List.of(PROXY_1, PROXY_2));

    @Test
    void 신뢰하지_않는_원격_주소의_X_Forwarded_For는_무시한다() {
        assertThat(resolver.resolve("203.0.113.9", "198.51.100.7")).isEqualTo("203.0.113.9");
    }

    @Test
    void 신뢰_프록시_목록이_비어_있으면_어떤_헤더도_신뢰하지_않는다() {
        ClientIpResolver none = new ClientIpResolver(List.of());

        assertThat(none.resolve(PROXY_1, "198.51.100.7")).isEqualTo(PROXY_1);
    }

    @Test
    void 신뢰_프록시가_전달한_단일_클라이언트_주소를_쓴다() {
        assertThat(resolver.resolve(PROXY_1, "198.51.100.7")).isEqualTo("198.51.100.7");
    }

    @Test
    void 체인은_오른쪽부터_보고_왼쪽의_위조_항목을_무시한다() {
        // 왼쪽 spoofed는 클라이언트가 임의로 넣을 수 있다. 신뢰 프록시 PROXY_2가 붙인 198.51.100.7이 실제 클라이언트다.
        assertThat(resolver.resolve(PROXY_1, "203.0.113.200, 198.51.100.7, 10.0.0.2"))
                .isEqualTo("198.51.100.7");
    }

    @Test
    void 모든_항목이_신뢰_프록시이면_가장_왼쪽을_쓴다() {
        assertThat(resolver.resolve(PROXY_1, "10.0.0.2, 10.0.0.1")).isEqualTo("10.0.0.2");
    }

    @Test
    void 체인에_잘못된_항목이_있으면_헤더를_무시하고_원격_주소를_쓴다() {
        assertThat(resolver.resolve(PROXY_1, "198.51.100.7, not-an-ip")).isEqualTo(PROXY_1);
        assertThat(resolver.resolve(PROXY_1, "198.51.100.7, , 10.0.0.2")).isEqualTo(PROXY_1);
    }

    @Test
    void 빈_헤더와_지나치게_긴_헤더는_무시한다() {
        assertThat(resolver.resolve(PROXY_1, "  ")).isEqualTo(PROXY_1);
        assertThat(resolver.resolve(PROXY_1, null)).isEqualTo(PROXY_1);
        assertThat(resolver.resolve(PROXY_1, "198.51.100.7," + "1".repeat(600))).isEqualTo(PROXY_1);
    }

    @Test
    void IPv6_주소도_정규화하여_비교한다() throws Exception {
        ClientIpResolver v6 = new ClientIpResolver(List.of("2001:DB8::1"));

        String resolved = v6.resolve("2001:db8::1", "2001:DB8::7");

        assertThat(resolved).isEqualTo(InetAddress.getByName("2001:db8::7").getHostAddress());
    }

    @Test
    void 해석할_수_없는_원격_주소는_unknown으로_모은다() {
        assertThat(resolver.resolve("not-an-ip", "198.51.100.7")).isEqualTo("unknown");
        assertThat(resolver.resolve(null, null)).isEqualTo("unknown");
    }

    @Test
    void IPv4_앞자리_0이나_범위를_넘는_값은_거부한다() {
        assertThat(resolver.resolve("203.0.113.9", null)).isEqualTo("203.0.113.9");
        assertThat(resolver.resolve(PROXY_1, "198.051.100.7")).isEqualTo(PROXY_1);
        assertThat(resolver.resolve(PROXY_1, "256.1.1.1")).isEqualTo(PROXY_1);
    }

    @Test
    void 잘못된_신뢰_프록시_설정은_기동을_막고_값을_메시지에_넣지_않는다() {
        assertThatThrownBy(() -> new ClientIpResolver(List.of("999.1.1.1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("trusted-proxies")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("999.1.1.1"));
    }
}
