package com.busanlocalfood.ai.service;

import com.busanlocalfood.ai.config.OpenRouterProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * OpenRouter의 OpenAI 호환 chat/completions 호출 (DEVELOPMENT_RULES 5.4).
 * 모델 ID는 설정에서 읽고, 키는 Authorization 헤더에만 쓴다. 로그와 예외 메시지에는 넣지 않는다.
 */
public final class OpenRouterChatClient implements ChatCompletionClient {

    static final int MAX_OUTPUT_TOKENS = 600;
    static final double TEMPERATURE = 0.3;

    private final OpenRouterProperties properties;
    private final RestClient restClient;

    public OpenRouterChatClient(OpenRouterProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = builder.baseUrl(properties.baseUrl()).build();
    }

    @Override
    public String complete(List<ChatMessage> messages) {
        if (!properties.configured()) {
            throw new AiUnavailableException("OpenRouter 설정이 없어 호출하지 않았습니다.");
        }
        ChatRequest body = new ChatRequest(properties.model(),
                messages.stream().map(m -> new MessageDto(m.role(), m.content())).toList(),
                MAX_OUTPUT_TOKENS, TEMPERATURE);
        ChatResponse response;
        try {
            response = restClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .body(body)
                    .retrieve()
                    .body(ChatResponse.class);
        } catch (RestClientResponseException e) {
            throw new AiUnavailableException("OpenRouter가 오류 응답을 돌려줬습니다 (HTTP " + e.getStatusCode().value() + ").");
        } catch (RestClientException e) {
            // 연결 실패, 읽기 제한 시간 초과 포함
            throw new AiUnavailableException("OpenRouter 호출이 실패하거나 제한 시간을 넘었습니다.");
        }
        if (response == null || response.choices() == null || response.choices().isEmpty()
                || response.choices().get(0) == null || response.choices().get(0).message() == null
                || response.choices().get(0).message().content() == null) {
            throw new AiResponseInvalidException("OpenRouter 응답에 본문이 없습니다.");
        }
        return response.choices().get(0).message().content();
    }

    /** 요청 본문. 필드 이름은 OpenAI 호환 형식을 따른다. */
    record ChatRequest(String model, List<MessageDto> messages, @JsonProperty("max_tokens") int maxTokens,
                       double temperature) {
    }

    record MessageDto(String role, String content) {
    }

    /** 응답은 필요한 필드만 읽는다. 나머지 필드는 무시한다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChatResponse(List<Choice> choices) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Choice(MessageDto message) {
    }
}
