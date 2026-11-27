package com.busanlocalfood.ai.service;

import java.util.List;

/**
 * 채팅 완성 호출 경계. 구현은 OpenRouter다. 테스트에서는 목으로 바꾼다.
 * 실패는 두 예외로만 알린다. 예외 메시지에는 키, 요청 본문, 리뷰 내용을 넣지 않는다.
 */
public interface ChatCompletionClient {

    /** 메시지를 보내고 첫 응답의 본문 텍스트를 돌려준다. */
    String complete(List<ChatMessage> messages);

    record ChatMessage(String role, String content) {
    }

    /** 호출 실패, 타임아웃, 설정 없음. */
    final class AiUnavailableException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public AiUnavailableException(String safeMessage) {
            super(safeMessage);
        }
    }

    /** 응답이 기대한 형식이 아니거나 비어 있다. */
    final class AiResponseInvalidException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public AiResponseInvalidException(String safeMessage) {
            super(safeMessage);
        }
    }
}
