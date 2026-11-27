package com.busanlocalfood.ai.config;

import com.busanlocalfood.ai.service.ChatCompletionClient;
import com.busanlocalfood.ai.service.OpenRouterChatModelClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * OpenRouter 연동 설정 (AI 리뷰 요약, F1-05).
 *
 * Spring AI OpenAI 스타터의 ChatModel을 쓴다. OpenRouter는 OpenAI 호환 API이므로 OpenAiApi만
 * OpenRouter 주소와 키, 제한 시간으로 직접 만든다. ChatModel과 재시도 설정(spring.ai.retry.max-attempts=1)은
 * 스타터 자동 구성을 따른다. 자동 재시도는 하지 않는다.
 */
@Configuration
@EnableConfigurationProperties(OpenRouterProperties.class)
public class AiConfig {

    /** OpenRouter 기본 URL(app.ai.openrouter.base-url, .../api/v1) 뒤에 붙는 경로. */
    static final String COMPLETIONS_PATH = "/chat/completions";

    @Bean
    public OpenAiApi openAiApi(OpenRouterProperties properties) {
        return openAiApi(properties, restClientBuilder(properties));
    }

    @Bean
    public ChatCompletionClient chatCompletionClient(ChatModel chatModel, OpenRouterProperties properties) {
        return new OpenRouterChatModelClient(chatModel, properties);
    }

    /**
     * 키가 없으면 빈 키로 만든다. 이 객체로는 호출하지 않는다. 호출 전에 OpenRouterProperties.configured()로 막는다.
     */
    public static OpenAiApi openAiApi(OpenRouterProperties properties, RestClient.Builder builder) {
        String key = properties.apiKey() == null ? "" : properties.apiKey();
        return OpenAiApi.builder()
                .baseUrl(properties.baseUrl() == null ? "" : properties.baseUrl())
                .apiKey(key)
                .completionsPath(COMPLETIONS_PATH)
                .restClientBuilder(builder)
                .build();
    }

    /** 연결과 응답 읽기 모두 설정된 제한 시간을 따른다. 제한 시간이 지나면 요청이 실패로 끝난다. */
    public static RestClient.Builder restClientBuilder(OpenRouterProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.timeout());
        factory.setReadTimeout(properties.timeout());
        return RestClient.builder().requestFactory(factory);
    }
}
