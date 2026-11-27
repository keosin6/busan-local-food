package com.busanlocalfood.image.service;

import java.util.regex.Pattern;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * AWS S3 구현. 자격 증명은 SDK 기본 체인이 찾는다(환경 변수 또는 EC2 인스턴스 역할). 코드에 키를 두지 않는다.
 * 예외 메시지는 HTTP 상태와 AWS 오류 코드만 쓴다. 요청 서명이나 헤더는 메시지에 넣지 않는다.
 */
public final class S3ImageStorage implements ImageStorage {

    private static final Pattern SAFE_CODE = Pattern.compile("[A-Za-z0-9_.-]{1,60}");

    private final S3Client client;
    private final String bucket;

    public S3ImageStorage(S3Client client, String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    @Override
    public void put(String s3Key, String contentType, byte[] content) {
        try {
            client.putObject(PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(s3Key)
                    .contentType(contentType)
                    .build(), RequestBody.fromBytes(content));
        } catch (S3Exception e) {
            throw new StorageException("S3 업로드가 실패했습니다 (" + describe(e) + ").");
        } catch (SdkException e) {
            throw new StorageException("S3 요청이 실패했습니다. 네트워크 상태와 설정을 확인해 주세요.");
        }
    }

    @Override
    public void delete(String s3Key) {
        try {
            client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(s3Key)
                    .build());
        } catch (S3Exception e) {
            throw new StorageException("S3 삭제가 실패했습니다 (" + describe(e) + ").");
        } catch (SdkException e) {
            throw new StorageException("S3 요청이 실패했습니다. 네트워크 상태와 설정을 확인해 주세요.");
        }
    }

    /** HTTP 상태와 AWS 오류 코드만 남긴다. 코드는 영문, 숫자, 몇 가지 기호만 허용한다. */
    static String describe(S3Exception e) {
        AwsErrorDetails details = e.awsErrorDetails();
        String code = details == null ? null : details.errorCode();
        String status = "HTTP " + e.statusCode();
        if (code != null && SAFE_CODE.matcher(code).matches()) {
            return status + ", " + code;
        }
        return status;
    }
}
