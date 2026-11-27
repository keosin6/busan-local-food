package com.busanlocalfood.favorite.web;

/** 찜 화면의 안내 문구. 리다이렉트 파라미터 값을 사용자에게 보일 문장으로 바꾼다. */
public final class FavoriteNotices {

    private FavoriteNotices() {
    }

    public static String notice(String favorite, String favoriteError) {
        if (favoriteError != null) {
            return switch (favoriteError) {
                case "duplicate" -> "이미 찜한 맛집입니다.";
                case "not_favorited" -> "찜한 맛집이 아닙니다. 이미 취소되었을 수 있습니다.";
                default -> "찜 요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.";
            };
        }
        if ("added".equals(favorite)) {
            return "찜 목록에 추가했습니다.";
        }
        if ("removed".equals(favorite)) {
            return "찜을 취소했습니다.";
        }
        return null;
    }
}
