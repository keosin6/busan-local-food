package com.busanlocalfood.ai.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.busanlocalfood.ai.service.ChatCompletionClient;
import com.busanlocalfood.ai.service.ChatCompletionClient.AiUnavailableException;
import com.busanlocalfood.ai.service.ChatCompletionClient.ChatMessage;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiModerationAutoConfiguration;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Spring AI 스타터 자동 구성과 AiConfig가 함께 뜨는지 확인한다. 외부 OpenRouter에는 접속하지 않는다.
 * 키나 모델 ID가 없어도 기동은 실패하지 않고, 설정이 없으면 호출하지 않는다.
 * 재시도 설정(spring.ai.retry.max-attempts=1)이 실제 ChatModel에 적용되는지 로컬 서버로 확인한다.
 */
class AiConfigWiringTest {

    /**
     * 실제 application.yml과 같은 스타터 설정이다. 스타터의 OpenAI 자동 구성 전부를 올려서,
     * 키가 없을 때 음성·이미지 같은 쓰지 않는 빈이 기동을 막지 않는지도 함께 본다.
     */
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OpenAiChatAutoConfiguration.class,
                    OpenAiEmbeddingAutoConfiguration.class, OpenAiImageAutoConfiguration.class,
                    OpenAiModerationAutoConfiguration.class, OpenAiAudioSpeechAutoConfiguration.class,
                    OpenAiAudioTranscriptionAutoConfiguration.class,
                    SpringAiRetryAutoConfiguration.class, ToolCallingAutoConfiguration.class))
            .withUserConfiguration(AiConfig.class)
            .withPropertyValues("spring.ai.model.chat=openai",
                    "spring.ai.model.embedding=none", "spring.ai.model.image=none",
                    "spring.ai.model.moderation=none", "spring.ai.model.audio.speech=none",
                    "spring.ai.model.audio.transcription=none",
                    "spring.ai.retry.max-attempts=1",
                    "app.ai.openrouter.base-url=https://openrouter.ai/api/v1",
                    "app.ai.openrouter.timeout-seconds=10");

    private static final List<ChatMessage> MESSAGES = List.of(new ChatMessage("user", "리뷰"));

    @Test
    void 키와_모델이_없어도_기동되고_호출하지_않는다() {
        runner.withPropertyValues("app.ai.openrouter.api-key=", "app.ai.openrouter.model=")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ChatCompletionClient.class);
                    ChatCompletionClient client = context.getBean(ChatCompletionClient.class);
                    assertThatThrownBy(() -> client.complete(MESSAGES))
                            .isInstanceOf(AiUnavailableException.class);
                });
    }

    @Test
    void 환경_변수가_전혀_없어도_기동된다() {
        runner.withPropertyValues("app.ai.openrouter.api-key=", "app.ai.openrouter.model=")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void 스타터가_만든_ChatModel이_있고_자동_재시도로_두_번_호출하지_않는다() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/chat/completions", exchange -> {
            calls.incrementAndGet();
            byte[] body = "{\"error\":\"boom\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(500, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1";
            runner.withPropertyValues("app.ai.openrouter.base-url=" + base,
                            "app.ai.openrouter.api-key=sk-test-only-key",
                            "app.ai.openrouter.model=test/model-id")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context).hasSingleBean(ChatModel.class);
                        ChatCompletionClient client = context.getBean(ChatCompletionClient.class);
                        assertThatThrownBy(() -> client.complete(MESSAGES))
                                .isInstanceOf(AiUnavailableException.class);
                        assertThat(calls.get()).isEqualTo(1);
                    });
        } finally {
            server.stop(0);
        }
    }
}
