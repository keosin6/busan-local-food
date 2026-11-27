package com.busanlocalfood.image.service;

import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.global.exception.ErrorCode;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 업로드 이미지 검증 (FUNCTION_SPEC F1-03 업로드 흐름 2번). 클라이언트 검사는 믿지 않고 서버에서 다시 한다.
 * 확인 항목: 크기(5MB 이하, 0바이트 금지), 선언된 MIME 타입, 확장자, 파일 앞부분의 시그니처(매직 바이트).
 * 시그니처까지 맞아야 한다. MIME 헤더나 확장자만 바꾼 파일은 거부한다.
 */
public final class ImagePolicy {

    public static final long MAX_BYTES = 5L * 1024 * 1024;

    private ImagePolicy() {
    }

    public enum ImageType {
        JPEG("image/jpeg", "jpg", "jpeg"),
        PNG("image/png", "png"),
        WEBP("image/webp", "webp");

        private final String contentType;
        private final List<String> extensions;

        ImageType(String contentType, String... extensions) {
            this.contentType = contentType;
            this.extensions = Arrays.asList(extensions);
        }

        public String contentType() {
            return contentType;
        }

        /** 객체 키에 쓰는 확장자. */
        public String canonicalExtension() {
            return extensions.get(0);
        }

        boolean allowsExtension(String extension) {
            return extensions.contains(extension);
        }

        static Optional<ImageType> byContentType(String contentType) {
            return Arrays.stream(values()).filter(t -> t.contentType.equals(contentType)).findFirst();
        }
    }

    /** 검증을 통과한 업로드 파일. bytes는 서버가 읽은 실제 내용이다. */
    public record ValidatedImage(ImageType type, String originalFilename, byte[] bytes) {
    }

    public static ValidatedImage validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessException(ErrorCode.IMAGE_TOO_LARGE);
        }
        ImageType type = ImageType.byContentType(normalizeContentType(file.getContentType()))
                .orElseThrow(() -> new BusinessException(ErrorCode.IMAGE_TYPE_NOT_ALLOWED));
        String originalFilename = sanitizeFilename(file.getOriginalFilename());
        String extension = extensionOf(originalFilename);
        if (!type.allowsExtension(extension)) {
            throw new BusinessException(ErrorCode.IMAGE_TYPE_NOT_ALLOWED);
        }
        byte[] bytes = read(file);
        if (bytes.length == 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        if (bytes.length > MAX_BYTES) {
            throw new BusinessException(ErrorCode.IMAGE_TOO_LARGE);
        }
        if (detect(bytes) != type) {
            throw new BusinessException(ErrorCode.IMAGE_TYPE_NOT_ALLOWED);
        }
        return new ValidatedImage(type, originalFilename, bytes);
    }

    /** 실제 내용의 시그니처로 형식을 정한다. 맞지 않으면 null이다. */
    static ImageType detect(byte[] bytes) {
        if (startsWith(bytes, 0xFF, 0xD8, 0xFF)) {
            return ImageType.JPEG;
        }
        if (startsWith(bytes, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return ImageType.PNG;
        }
        // WEBP: "RIFF" + 4바이트 크기 + "WEBP"
        if (startsWith(bytes, 0x52, 0x49, 0x46, 0x46) && bytes.length >= 12
                && bytes[8] == 0x57 && bytes[9] == 0x45 && bytes[10] == 0x42 && bytes[11] == 0x50) {
            return ImageType.WEBP;
        }
        return null;
    }

    private static boolean startsWith(byte[] bytes, int... signature) {
        if (bytes.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((bytes[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }

    private static String normalizeContentType(String declared) {
        if (declared == null) {
            return "";
        }
        int semicolon = declared.indexOf(';');
        String base = semicolon >= 0 ? declared.substring(0, semicolon) : declared;
        return base.trim().toLowerCase(Locale.ROOT);
    }

    /** 원본 파일명에서 경로를 떼고 255자 이내로 줄인다. DB에는 이 값만 저장한다. */
    static String sanitizeFilename(String raw) {
        if (raw == null) {
            return "image";
        }
        String name = raw;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.trim();
        if (name.isEmpty()) {
            return "image";
        }
        return name.length() > 255 ? name.substring(0, 255) : name;
    }

    private static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static byte[] read(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
    }
}
