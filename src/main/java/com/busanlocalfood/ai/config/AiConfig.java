package com.busanlocalfood.ai.config;

import com.busanlocalfood.ai.service.ChatCompletionClient;
import com.busanlocalfood.ai.service.OpenRouterChatClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(OpenRouterProperties.class)
public class AiConfig {

    @Bean
    public ChatCompletionClient chatCompletionClient(OpenRouterProperties properties) {
        return new OpenRouterChatClient(properties, restClientBuilder(properties));
    }

    /** 연결과 응답 읽기 모두 설정된 제한 시간을 따른다. 제한 시간이 지나면 요청이 실패로 끝난다. */
    public static RestClient.Builder restClientBuilder(OpenRouterProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.timeout());
        factory.setReadTimeout(properties.timeout());
        return RestClient.builder().requestFactory(factory);
    }
}
