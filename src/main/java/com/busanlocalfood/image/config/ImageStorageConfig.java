package com.busanlocalfood.image.config;

import com.busanlocalfood.image.service.ImageStorage;
import com.busanlocalfood.image.service.S3ImageStorage;
import com.busanlocalfood.image.service.UnconfiguredImageStorage;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@EnableConfigurationProperties(AwsS3Properties.class)
public class ImageStorageConfig {

    /**
     * 리전과 버킷이 모두 있으면 S3 구현을 만든다. 하나라도 없으면 항상 실패하는 구현을 쓴다.
     * S3 클라이언트는 이 시점에 AWS를 호출하지 않는다. 자격 증명은 실제 요청 때 SDK 기본 체인이 찾는다.
     */
    @Bean
    public ImageStorage imageStorage(AwsS3Properties properties) {
        if (!properties.configured()) {
            return new UnconfiguredImageStorage();
        }
        S3Client client = S3Client.builder()
                .region(Region.of(properties.region()))
                .build();
        return new S3ImageStorage(client, properties.bucket());
    }
}
