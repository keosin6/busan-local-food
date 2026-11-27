package com.busanlocalfood.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.busanlocalfood.ai.config.AiConfig;
import com.busanlocalfood.ai.config.OpenRouterProperties;
import com.busanlocalfood.ai.service.ChatCompletionClient.AiResponseInvalidException;
import com.busanlocalfood.ai.service.ChatCompletionClient.AiUnavailableException;
import com.busanlocalfood.ai.service.ChatCompletionClient.ChatMessage;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * OpenRouter 호출 클라이언트. 외부 서비스는 MockRestServiceServer로 모킹한다 (실제 OpenRouter에 접속하지 않는다).
 * 제한 시간 검증만 로컬 HTTP 서버를 쓴다. 그 서버도 외부가 아니다.
 */
class OpenRouterChatClientTest {

    private static final String BASE = "https://openrouter.ai/api/v1";
    private static final String KEY = "sk-test-only-key";
    private static final String MODEL = "test/model-id";
    private static final List<ChatMessage> MESSAGES = List.of(
            new ChatMessage("system", "시스템 지시"), new ChatMessage("user", "리뷰 본문"));

    private static OpenRouterProperties props() {
        return new OpenRouterProperties(BASE, KEY, MODEL, 10);
    }

    @Test
    void 요청은_chat_completions로_가고_키는_Authorization_헤더에만_실린다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenRouterChatClient client = new OpenRouterChatClient(props(), builder);
        server.expect(requestTo(BASE + "/chat/completions"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + KEY))
                .andExpect(jsonPath("$.model").value(MODEL))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].content").value("리뷰 본문"))
                .andExpect(jsonPath("$.max_tokens").value(OpenRouterChatClient.MAX_OUTPUT_TOKENS))
                .andRespond(withSuccess("""
                        {"id":"x","choices":[{"index":0,"message":{"role":"assistant","content":"맛이 진합니다."}}],"usage":{"total_tokens":10}}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.complete(MESSAGES)).isEqualTo("맛이 진합니다.");
        server.verify();
    }

    @Test
    void 서버_오류는_AiUnavailable이고_메시지에_키가_없다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenRouterChatClient client = new OpenRouterChatClient(props(), builder);
        server.expect(requestTo(BASE + "/chat/completions")).andRespond(withServerError());

        assertThatThrownBy(() -> client.complete(MESSAGES))
                .isInstanceOfSatisfying(AiUnavailableException.class,
                        e -> assertThat(e.getMessage()).contains("500").doesNotContain(KEY));
    }

    @Test
    void 연결_실패는_AiUnavailable이다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenRouterChatClient client = new OpenRouterChatClient(props(), builder);
        server.expect(requestTo(BASE + "/chat/completions"))
                .andRespond(request -> {
                    throw new java.net.ConnectException("연결 거부");
                });

        assertThatThrownBy(() -> client.complete(MESSAGES)).isInstanceOf(AiUnavailableException.class);
    }

    @Test
    void 선택지가_없는_응답은_AiResponseInvalid다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenRouterChatClient client = new OpenRouterChatClient(props(), builder);
        server.expect(requestTo(BASE + "/chat/completions"))
                .andRespond(withSuccess("{\"error\":\"nothing\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.complete(MESSAGES)).isInstanceOf(AiResponseInvalidException.class);
    }

    @Test
    void 설정이_없으면_외부_호출을_하지_않는다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenRouterChatClient client = new OpenRouterChatClient(new OpenRouterProperties(BASE, "", MODEL, 10), builder);

        assertThatThrownBy(() -> client.complete(MESSAGES)).isInstanceOf(AiUnavailableException.class);
        server.verify();
    }

    @Test
    void 응답이_제한_시간을_넘으면_AiUnavailable이고_그_시간_안에_끝난다() throws Exception {
        HttpServer slow = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        slow.createContext("/api/v1/chat/completions", exchange -> {
            try {
                Thread.sleep(4000);
                byte[] body = "{\"choices\":[{\"message\":{\"content\":\"늦은 응답\"}}]}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        slow.start();
        try {
            String base = "http://127.0.0.1:" + slow.getAddress().getPort() + "/api/v1";
            OpenRouterProperties props = new OpenRouterProperties(base, KEY, MODEL, 1);
            OpenRouterChatClient client = new OpenRouterChatClient(props, AiConfig.restClientBuilder(props));

            long started = System.nanoTime();
            assertThatThrownBy(() -> client.complete(MESSAGES)).isInstanceOf(AiUnavailableException.class);
            Duration elapsed = Duration.ofNanos(System.nanoTime() - started);

            assertThat(elapsed).isLessThan(Duration.ofSeconds(3));
        } finally {
            slow.stop(0);
        }
    }
}
