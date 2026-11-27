package com.busanlocalfood.image.service;

import com.busanlocalfood.image.config.AwsS3Properties;
import org.springframework.stereotype.Component;

/**
 * 객체 키로 공개 URL을 만든다 (가상 호스팅 형식). 버킷 공개 읽기 정책이나 CDN은 배포 단계에서 확정한다.
 * 설정이 없으면 null을 돌려준다. 화면은 null이면 이미지 자리에 기본 표시를 쓴다.
 */
@Component
public class ImageUrls {

    private final AwsS3Properties properties;

    public ImageUrls(AwsS3Properties properties) {
        this.properties = properties;
    }

    public String urlOf(String s3Key) {
        if (!properties.configured()) {
            return null;
        }
        return "https://" + properties.bucket() + ".s3." + properties.region() + ".amazonaws.com/" + s3Key;
    }
}
