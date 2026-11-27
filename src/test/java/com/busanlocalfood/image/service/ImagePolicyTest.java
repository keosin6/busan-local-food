package com.busanlocalfood.image.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.global.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/** 업로드 이미지 검증 규칙 (FUNCTION_SPEC F1-03). 실제 파일 시그니처까지 확인한다. DB와 S3 없이 실행한다. */
class ImagePolicyTest {

    private static byte[] jpeg() {
        return new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0};
    }

    private static byte[] png() {
        return new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    }

    private static byte[] webp() {
        return "RIFF\u0000\u0000\u0000\u0000WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1);
    }

    private static MockMultipartFile file(String name, String contentType, byte[] content) {
        return new MockMultipartFile("file", name, contentType, content);
    }

    private static void assertCode(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    @Test
    void JPEG_PNG_WEBP는_MIME_확장자_시그니처가_맞으면_허용한다() {
        ImagePolicy.ValidatedImage a = ImagePolicy.validate(file("a.jpg", "image/jpeg", jpeg()));
        ImagePolicy.ValidatedImage b = ImagePolicy.validate(file("b.JPEG", "image/jpeg", jpeg()));
        ImagePolicy.ValidatedImage c = ImagePolicy.validate(file("c.png", "image/png", png()));
        ImagePolicy.ValidatedImage d = ImagePolicy.validate(file("d.webp", "image/webp", webp()));

        assertThat(a.type()).isEqualTo(ImagePolicy.ImageType.JPEG);
        assertThat(b.type()).isEqualTo(ImagePolicy.ImageType.JPEG);
        assertThat(c.type()).isEqualTo(ImagePolicy.ImageType.PNG);
        assertThat(d.type()).isEqualTo(ImagePolicy.ImageType.WEBP);
        assertThat(d.type().canonicalExtension()).isEqualTo("webp");
        assertThat(ImagePolicy.ImageType.JPEG.canonicalExtension()).isEqualTo("jpg");
    }

    @Test
    void MIME_파라미터가_붙어도_기본_형식으로_판단한다() {
        ImagePolicy.ValidatedImage v = ImagePolicy.validate(file("a.png", "image/png; charset=binary", png()));

        assertThat(v.type()).isEqualTo(ImagePolicy.ImageType.PNG);
    }

    @Test
    void GIF와_텍스트_같은_허용되지_않은_형식은_거부한다() {
        assertCode(() -> ImagePolicy.validate(file("a.gif", "image/gif", jpeg())), ErrorCode.IMAGE_TYPE_NOT_ALLOWED);
        assertCode(() -> ImagePolicy.validate(file("a.txt", "text/plain", jpeg())), ErrorCode.IMAGE_TYPE_NOT_ALLOWED);
        assertCode(() -> ImagePolicy.validate(file("a.jpg", null, jpeg())), ErrorCode.IMAGE_TYPE_NOT_ALLOWED);
    }

    @Test
    void 확장자가_MIME과_다르면_거부한다() {
        assertCode(() -> ImagePolicy.validate(file("a.png", "image/jpeg", jpeg())), ErrorCode.IMAGE_TYPE_NOT_ALLOWED);
        assertCode(() -> ImagePolicy.validate(file("noext", "image/jpeg", jpeg())), ErrorCode.IMAGE_TYPE_NOT_ALLOWED);
    }

    @Test
    void 실제_내용이_선언한_형식과_다르면_거부한다() {
        // 확장자와 MIME은 맞지만 내용은 PNG다.
        assertCode(() -> ImagePolicy.validate(file("a.jpg", "image/jpeg", png())), ErrorCode.IMAGE_TYPE_NOT_ALLOWED);
        // 스크립트 내용을 이미지 이름으로 올리는 경우
        assertCode(() -> ImagePolicy.validate(file("a.png", "image/png", "<script>".getBytes(StandardCharsets.UTF_8))),
                ErrorCode.IMAGE_TYPE_NOT_ALLOWED);
    }

    @Test
    void 파일_크기가_5MB를_넘으면_IMAGE_TOO_LARGE다() {
        byte[] big = new byte[(int) ImagePolicy.MAX_BYTES + 1];
        System.arraycopy(jpeg(), 0, big, 0, 4);

        assertCode(() -> ImagePolicy.validate(file("a.jpg", "image/jpeg", big)), ErrorCode.IMAGE_TOO_LARGE);
    }

    @Test
    void 정확히_5MB인_파일은_허용한다() {
        byte[] exact = new byte[(int) ImagePolicy.MAX_BYTES];
        System.arraycopy(jpeg(), 0, exact, 0, 4);

        assertThat(ImagePolicy.validate(file("a.jpg", "image/jpeg", exact)).bytes()).hasSize((int) ImagePolicy.MAX_BYTES);
    }

    @Test
    void 파일이_없거나_비어_있으면_VALIDATION_FAILED다() {
        assertCode(() -> ImagePolicy.validate(null), ErrorCode.VALIDATION_FAILED);
        assertCode(() -> ImagePolicy.validate(file("a.jpg", "image/jpeg", new byte[0])), ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void 원본_파일명은_경로를_떼고_저장한다() {
        ImagePolicy.ValidatedImage v = ImagePolicy.validate(file("../../secret/photo.png", "image/png", png()));

        assertThat(v.originalFilename()).isEqualTo("photo.png");
    }

    @Test
    void 경로_구분자가_윈도우_방식이어도_파일명만_남긴다() {
        assertThat(ImagePolicy.sanitizeFilename("C:\\pictures\\food.jpg")).isEqualTo("food.jpg");
        assertThat(ImagePolicy.sanitizeFilename("   ")).isEqualTo("image");
        assertThat(ImagePolicy.sanitizeFilename(null)).isEqualTo("image");
    }
}
