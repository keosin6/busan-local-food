package com.busanlocalfood.image.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** S3 설정 (application.yml의 app.aws.s3). 값은 환경 변수 AWS_REGION, AWS_S3_BUCKET으로만 받는다. */
@ConfigurationProperties(prefix = "app.aws.s3")
public record AwsS3Properties(String region, String bucket) {

    public boolean configured() {
        return notBlank(region) && notBlank(bucket);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
