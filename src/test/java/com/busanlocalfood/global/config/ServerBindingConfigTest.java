package com.busanlocalfood.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

/**
 * 웹 바인딩 주소 설정 검사. 운영은 127.0.0.1에만 바인딩하고, 로컬과 기본 설정은 바꾸지 않는다.
 * 실제 기동 검사는 docs/DEPLOYMENT.md의 바인딩 확인 절차로 한다. 이 테스트는 설정 파일 내용만 본다.
 */
class ServerBindingConfigTest {

    @Test
    void 운영_프로필은_8080을_루프백에만_바인딩한다() {
        Properties prod = load("application-prod.yml");

        assertThat(prod.getProperty("server.address")).isEqualTo("127.0.0.1");
        assertThat(prod.getProperty("server.port")).isEqualTo("8080");
    }

    @Test
    void 기본_설정과_로컬_프로필은_바인딩_주소를_정하지_않는다() {
        assertThat(load("application.yml").getProperty("server.address")).isNull();
        assertThat(load("application-local.yml").getProperty("server.address")).isNull();
    }

    private static Properties load(String name) {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource(name));
        Properties properties = yaml.getObject();
        return properties == null ? new Properties() : properties;
    }
}
