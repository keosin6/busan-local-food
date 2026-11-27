package com.busanlocalfood.image.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.busanlocalfood.image.service.ImageStorage.StorageException;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

/** S3 어댑터. 실제 버킷에 접근하지 않고 S3Client를 목으로 바꿔 요청 내용과 오류 메시지를 확인한다. */
class S3ImageStorageTest {

    private static final String BUCKET = "test-bucket";
    private static final String KEY = "restaurants/10/0f8f.jpg";

    @Test
    void 업로드는_버킷_키_타입으로_요청한다() {
        S3Client client = mock(S3Client.class);
        when(client.putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        S3ImageStorage storage = new S3ImageStorage(client, BUCKET);

        storage.put(KEY, "image/jpeg", new byte[]{1, 2, 3});

        verify(client).putObject(org.mockito.ArgumentMatchers.argThat((PutObjectRequest r) ->
                BUCKET.equals(r.bucket()) && KEY.equals(r.key()) && "image/jpeg".equals(r.contentType())),
                any(software.amazon.awssdk.core.sync.RequestBody.class));
    }

    @Test
    void 삭제는_버킷과_키로_요청한다() {
        S3Client client = mock(S3Client.class);
        when(client.deleteObject(any(DeleteObjectRequest.class))).thenReturn(DeleteObjectResponse.builder().build());
        S3ImageStorage storage = new S3ImageStorage(client, BUCKET);

        storage.delete(KEY);

        verify(client).deleteObject(org.mockito.ArgumentMatchers.argThat((DeleteObjectRequest r) ->
                BUCKET.equals(r.bucket()) && KEY.equals(r.key())));
    }

    @Test
    void S3_오류_메시지에는_HTTP_상태와_AWS_오류_코드만_남고_서명과_자격_증명은_빠진다() {
        S3Client client = mock(S3Client.class);
        S3Exception failure = (S3Exception) S3Exception.builder()
                .statusCode(403)
                .message("Request signature Credential=AKIAEXAMPLE/20260101 X-Amz-Signature=abcdef")
                .awsErrorDetails(AwsErrorDetails.builder()
                        .errorCode("AccessDenied")
                        .errorMessage("Signature=abcdef Credential=AKIAEXAMPLE")
                        .build())
                .build();
        when(client.deleteObject(any(DeleteObjectRequest.class))).thenThrow(failure);
        S3ImageStorage storage = new S3ImageStorage(client, BUCKET);

        assertThatThrownBy(() -> storage.delete(KEY))
                .isInstanceOfSatisfying(StorageException.class, e -> {
                    assertThat(e.safeMessage()).contains("HTTP 403").contains("AccessDenied");
                    assertThat(e.safeMessage()).doesNotContain("Signature").doesNotContain("Credential")
                            .doesNotContain("AKIA").doesNotContain("abcdef");
                });
    }

    @Test
    void 허용되지_않는_문자가_든_오류_코드는_메시지에서_뺀다() {
        S3Client client = mock(S3Client.class);
        S3Exception failure = (S3Exception) S3Exception.builder()
                .statusCode(500)
                .awsErrorDetails(AwsErrorDetails.builder().errorCode("bad code=secret").build())
                .build();
        when(client.putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class)))
                .thenThrow(failure);
        S3ImageStorage storage = new S3ImageStorage(client, BUCKET);

        assertThatThrownBy(() -> storage.put(KEY, "image/png", new byte[]{1}))
                .isInstanceOfSatisfying(StorageException.class, e -> {
                    assertThat(e.safeMessage()).contains("HTTP 500").doesNotContain("secret");
                });
    }

    @Test
    void 네트워크_오류는_일반_문장으로만_알린다() {
        S3Client client = mock(S3Client.class);
        when(client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(SdkClientException.builder().message("connect timed out to 10.0.0.1 signature=x").build());
        S3ImageStorage storage = new S3ImageStorage(client, BUCKET);

        assertThatThrownBy(() -> storage.delete(KEY))
                .isInstanceOfSatisfying(StorageException.class, e ->
                        assertThat(e.safeMessage()).doesNotContain("10.0.0.1").doesNotContain("signature"));
    }

    @Test
    void 설정이_없는_저장소는_항상_실패하고_가짜_성공을_돌려주지_않는다() {
        UnconfiguredImageStorage storage = new UnconfiguredImageStorage();

        assertThatThrownBy(() -> storage.put(KEY, "image/png", new byte[]{1})).isInstanceOf(StorageException.class);
        assertThatThrownBy(() -> storage.delete(KEY)).isInstanceOf(StorageException.class);
    }
}
