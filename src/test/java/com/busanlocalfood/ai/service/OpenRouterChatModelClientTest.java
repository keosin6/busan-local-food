package com.busanlocalfood.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
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
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Spring AI ChatModel 기반 OpenRouter 호출. 외부 OpenRouter에는 접속하지 않는다.
 * 요청 형식은 MockRestServiceServer로, 제한 시간과 재시도 횟수는 로컬 HTTP 서버로 검증한다.
 */
class OpenRouterChatModelClientTest {

    private static final String BASE = "https://openrouter.ai/api/v1";
    private static final String KEY = "sk-test-only-key";
    private static final String MODEL = "test/model-id";
    private static final List<ChatMessage> MESSAGES = List.of(
            new ChatMessage("system", "시스템 지시"), new ChatMessage("user", "리뷰 본문"));

    private static OpenRouterProperties props() {
        return new OpenRouterProperties(BASE, KEY, MODEL, 10);
    }

    /** 실제 구성과 같은 OpenAiApi를 쓰되, 재시도는 한 번만 하는 ChatModel을 만든다. */
    static ChatModel chatModel(OpenRouterProperties properties, RestClient.Builder builder) {
        OpenAiApi api = AiConfig.openAiApi(properties, builder);
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder().build())
                .retryTemplate(RetryTemplate.builder().maxAttempts(1).build())
                .build();
    }

    @Test
    void 요청은_chat_completions로_가고_키는_Authorization_헤더에만_실리며_모델과_옵션이_전달된다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ChatModel model = chatModel(props(), builder);
        server.expect(requestTo(BASE + "/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + KEY))
                .andExpect(jsonPath("$.model").value(MODEL))
                .andExpect(jsonPath("$.max_tokens").value(OpenRouterChatModelClient.MAX_OUTPUT_TOKENS))
                .andExpect(jsonPath("$.temperature").value(OpenRouterChatModelClient.TEMPERATURE))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.messages[1].content").value("리뷰 본문"))
                .andRespond(withSuccess("""
                        {"id":"x","choices":[{"index":0,"message":{"role":"assistant","content":"맛이 진합니다."}}],"usage":{"total_tokens":10}}
                        """, MediaType.APPLICATION_JSON));

        String text = new OpenRouterChatModelClient(model, props()).complete(MESSAGES);

        assertThat(text).isEqualTo("맛이 진합니다.");
        server.verify();
    }

    @Test
    void 서버_오류는_AiUnavailable이고_메시지에_키가_없다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ChatModel model = chatModel(props(), builder);
        server.expect(requestTo(BASE + "/chat/completions")).andRespond(withServerError());

        assertThatThrownBy(() -> new OpenRouterChatModelClient(model, props()).complete(MESSAGES))
                .isInstanceOfSatisfying(AiUnavailableException.class,
                        e -> assertThat(e.getMessage()).doesNotContain(KEY));
    }

    @Test
    void 연결_실패는_AiUnavailable이다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ChatModel model = chatModel(props(), builder);
        server.expect(requestTo(BASE + "/chat/completions"))
                .andRespond(request -> {
                    throw new java.net.ConnectException("연결 거부");
                });

        assertThatThrownBy(() -> new OpenRouterChatModelClient(model, props()).complete(MESSAGES))
                .isInstanceOf(AiUnavailableException.class);
    }

    @Test
    void 선택지가_없는_응답은_AiUnavailable_또는_AiResponseInvalid다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ChatModel model = chatModel(props(), builder);
        server.expect(requestTo(BASE + "/chat/completions"))
                .andRespond(withSuccess("{\"error\":\"nothing\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> new OpenRouterChatModelClient(model, props()).complete(MESSAGES))
                .isInstanceOfAny(AiResponseInvalidException.class, AiUnavailableException.class);
    }

    @Test
    void 본문이_null인_응답은_AiResponseInvalid다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ChatModel model = chatModel(props(), builder);
        server.expect(requestTo(BASE + "/chat/completions"))
                .andRespond(withSuccess("""
                        {"choices":[{"index":0,"message":{"role":"assistant","content":null}}]}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> new OpenRouterChatModelClient(model, props()).complete(MESSAGES))
                .isInstanceOfAny(AiResponseInvalidException.class, AiUnavailableException.class);
    }

    @Test
    void 예상하지_못한_Spring_AI_예외는_AiUnavailable로_바뀐다() {
        ChatModel model = mock(ChatModel.class);
        when(model.call(org.mockito.ArgumentMatchers.any(org.springframework.ai.chat.prompt.Prompt.class)))
                .thenThrow(new IllegalStateException("내부 상태"));

        assertThatThrownBy(() -> new OpenRouterChatModelClient(model, props()).complete(MESSAGES))
                .isInstanceOf(AiUnavailableException.class);
    }

    @Test
    void 키가_없으면_ChatModel을_호출하지_않는다() {
        ChatModel model = mock(ChatModel.class);
        OpenRouterProperties noKey = new OpenRouterProperties(BASE, "", MODEL, 10);

        assertThatThrownBy(() -> new OpenRouterChatModelClient(model, noKey).complete(MESSAGES))
                .isInstanceOf(AiUnavailableException.class);
        verifyNoInteractions(model);
    }

    @Test
    void 모델_ID가_없으면_ChatModel을_호출하지_않는다() {
        ChatModel model = mock(ChatModel.class);
        OpenRouterProperties noModel = new OpenRouterProperties(BASE, KEY, "  ", 10);

        assertThatThrownBy(() -> new OpenRouterChatModelClient(model, noModel).complete(MESSAGES))
                .isInstanceOf(AiUnavailableException.class);
        verifyNoInteractions(model);
    }

    @Test
    void 응답이_제한_시간을_넘으면_AiUnavailable이고_그_시간_안에_끝난다() throws Exception {
        HttpServer slow = startServer(4000, 200, "{\"choices\":[{\"message\":{\"content\":\"늦은 응답\"}}]}", new AtomicInteger());
        try {
            String base = "http://127.0.0.1:" + slow.getAddress().getPort() + "/api/v1";
            OpenRouterProperties props = new OpenRouterProperties(base, KEY, MODEL, 1);
            ChatModel model = chatModel(props, AiConfig.restClientBuilder(props));

            long started = System.nanoTime();
            assertThatThrownBy(() -> new OpenRouterChatModelClient(model, props).complete(MESSAGES))
                    .isInstanceOf(AiUnavailableException.class);
            Duration elapsed = Duration.ofNanos(System.nanoTime() - started);

            assertThat(elapsed).isLessThan(Duration.ofSeconds(3));
        } finally {
            slow.stop(0);
        }
    }

    @Test
    void HTTP_오류가_나도_자동으로_다시_호출하지_않는다() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer failing = startServer(0, 500, "{\"error\":\"boom\"}", calls);
        try {
            String base = "http://127.0.0.1:" + failing.getAddress().getPort() + "/api/v1";
            OpenRouterProperties props = new OpenRouterProperties(base, KEY, MODEL, 10);
            ChatModel model = chatModel(props, AiConfig.restClientBuilder(props));

            assertThatThrownBy(() -> new OpenRouterChatModelClient(model, props).complete(MESSAGES))
                    .isInstanceOf(AiUnavailableException.class);
            assertThat(calls.get()).isEqualTo(1);
        } finally {
            failing.stop(0);
        }
    }

    /** 지연(ms) 뒤에 주어진 상태와 본문을 돌려주는 로컬 서버. 요청 수를 센다. */
    private static HttpServer startServer(long delayMillis, int status, String json, AtomicInteger calls)
            throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/chat/completions", exchange -> {
            calls.incrementAndGet();
            try {
                if (delayMillis > 0) {
                    Thread.sleep(delayMillis);
                }
                byte[] body = json.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        return server;
    }
}
