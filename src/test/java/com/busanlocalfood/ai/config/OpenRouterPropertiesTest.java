package com.busanlocalfood.ai.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OpenRouterPropertiesTest {

    @Test
    void 키와_모델과_주소가_모두_있어야_설정된_것으로_본다() {
        assertThat(new OpenRouterProperties("https://openrouter.ai/api/v1", "key", "model", 10).configured()).isTrue();
        assertThat(new OpenRouterProperties("https://openrouter.ai/api/v1", "", "model", 10).configured()).isFalse();
        assertThat(new OpenRouterProperties("https://openrouter.ai/api/v1", "key", " ", 10).configured()).isFalse();
        assertThat(new OpenRouterProperties("", "key", "model", 10).configured()).isFalse();
        assertThat(new OpenRouterProperties(null, null, null, null).configured()).isFalse();
    }

    @Test
    void 제한_시간은_기본_10초이고_설정하면_그_값을_쓴다() {
        assertThat(new OpenRouterProperties(null, null, null, null).timeout().toSeconds()).isEqualTo(10);
        assertThat(new OpenRouterProperties(null, null, null, 3).timeout().toSeconds()).isEqualTo(3);
    }

    @Test
    void 문자열_표현에는_API_키가_나오지_않는다() {
        OpenRouterProperties props = new OpenRouterProperties("https://openrouter.ai/api/v1", "sk-secret-123", "vendor/model", 10);

        assertThat(props.toString()).doesNotContain("sk-secret-123").doesNotContain("vendor/model").contains("****");
    }
}
