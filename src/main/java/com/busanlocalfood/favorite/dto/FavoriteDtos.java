package com.busanlocalfood.favorite.dto;

/** 찜 API 응답 (API_SPEC 8.1, 8.2). 목록 항목은 맛집 목록 항목(RestaurantDtos.RestaurantSummary)을 그대로 쓴다. */
public final class FavoriteDtos {

    private FavoriteDtos() {
    }

    /** 찜하기와 찜 취소의 결과. favorited는 처리 후 상태, favoriteCount는 처리 후 맛집의 찜 수다. */
    public record FavoriteState(long restaurantId, boolean favorited, int favoriteCount) {
    }
}
