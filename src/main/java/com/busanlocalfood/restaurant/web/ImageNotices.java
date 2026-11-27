package com.busanlocalfood.restaurant.web;

/** 수정 화면의 이미지 안내 문구. 리다이렉트 파라미터 값을 사용자에게 보일 문장으로 바꾼다. */
final class ImageNotices {

    private ImageNotices() {
    }

    static String notice(String image, String imageError, boolean storageDelayed) {
        if (imageError != null) {
            return "이미지 업로드에 실패했습니다. " + reason(imageError);
        }
        if ("added".equals(image)) {
            return "사진을 추가했습니다.";
        }
        if ("deleted".equals(image)) {
            return storageDelayed
                    ? "사진을 삭제했습니다. 저장소에서 파일을 지우는 작업은 재시도 대기 중입니다."
                    : "사진을 삭제했습니다.";
        }
        return null;
    }

    private static String reason(String code) {
        return switch (code) {
            case "type" -> "JPEG, PNG, WEBP 형식만 올릴 수 있습니다.";
            case "size" -> "파일은 5MB 이하만 올릴 수 있습니다.";
            case "limit" -> "맛집 사진은 최대 5장까지 올릴 수 있습니다.";
            case "storage" -> "사진 저장소 요청이 실패했습니다. 잠시 후 다시 시도해 주세요.";
            case "file" -> "올릴 파일을 선택해 주세요.";
            default -> "잠시 후 다시 시도해 주세요.";
        };
    }
}
