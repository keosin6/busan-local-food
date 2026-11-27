package com.busanlocalfood.ai.service;

import com.busanlocalfood.ai.config.OpenRouterProperties;
import java.util.List;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.web.client.RestClientException;

/**
 * Spring AI ChatModel로 OpenRouter를 호출한다 (DEVELOPMENT_RULES 5.4).
 * 모델 ID는 설정(OPENROUTER_MODEL)에서 읽어 호출 옵션으로 넘긴다. 키는 ChatModel 안의 OpenAiApi가 Authorization 헤더로만 쓴다.
 * 설정이 하나라도 없으면 ChatModel을 부르지 않는다. 예외 메시지에는 키, 요청 본문, 응답 본문을 넣지 않는다.
 */
public final class OpenRouterChatModelClient implements ChatCompletionClient {

    static final int MAX_OUTPUT_TOKENS = 600;
    static final double TEMPERATURE = 0.3;

    private final ChatModel chatModel;
    private final OpenRouterProperties properties;

    public OpenRouterChatModelClient(ChatModel chatModel, OpenRouterProperties properties) {
        this.chatModel = chatModel;
        this.properties = properties;
    }

    @Override
    public String complete(List<ChatMessage> messages) {
        if (!properties.configured()) {
            throw new AiUnavailableException("OpenRouter 설정이 없어 호출하지 않았습니다.");
        }
        Prompt prompt = new Prompt(messages.stream().map(OpenRouterChatModelClient::toSpringMessage).toList(),
                options());
        ChatResponse response;
        try {
            response = chatModel.call(prompt);
        } catch (NonTransientAiException e) {
            throw new AiUnavailableException("OpenRouter가 오류 응답을 돌려줬습니다.");
        } catch (TransientAiException e) {
            throw new AiUnavailableException("OpenRouter 응답이 일시적으로 실패했습니다.");
        } catch (RestClientException e) {
            // 연결 실패, 읽기 제한 시간 초과 포함
            throw new AiUnavailableException("OpenRouter 호출이 실패하거나 제한 시간을 넘었습니다.");
        } catch (RuntimeException e) {
            // 예상하지 못한 Spring AI 예외. 메시지는 버리고 종류만 남기지 않는다(호출자가 로그를 남긴다).
            throw new AiUnavailableException("OpenRouter 호출 중 예상하지 못한 오류가 났습니다.");
        }
        String text = textOf(response);
        if (text == null) {
            throw new AiResponseInvalidException("OpenRouter 응답에 본문이 없습니다.");
        }
        return text;
    }

    private OpenAiChatOptions options() {
        return OpenAiChatOptions.builder()
                .model(properties.model())
                .maxTokens(MAX_OUTPUT_TOKENS)
                .temperature(TEMPERATURE)
                .build();
    }

    private static Message toSpringMessage(ChatMessage message) {
        return switch (message.role()) {
            case "system" -> new SystemMessage(message.content());
            case "user" -> new UserMessage(message.content());
            default -> throw new IllegalArgumentException("지원하지 않는 메시지 역할입니다.");
        };
    }

    private static String textOf(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return null;
        }
        AssistantMessage output = response.getResult().getOutput();
        return output.getText();
    }
}
