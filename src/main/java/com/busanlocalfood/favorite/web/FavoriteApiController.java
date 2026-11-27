package com.busanlocalfood.favorite.web;

import com.busanlocalfood.favorite.dto.FavoriteDtos.FavoriteState;
import com.busanlocalfood.favorite.service.FavoriteService;
import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.global.exception.ErrorCode;
import com.busanlocalfood.global.response.ApiSuccessResponse;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.PageResponse;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.RestaurantSummary;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 찜 API (API_SPEC 8.1~8.3). 모두 JWT 인증이 필요하다 (SecurityConfig의 /api/** 규칙).
 * 본인 찜만 다룬다. 회원 ID는 인증 주체에서만 읽는다. 응답 형식은 기존 API와 같은 ApiSuccessResponse를 쓴다.
 */
@RestController
public class FavoriteApiController {

    private final FavoriteService favoriteService;

    public FavoriteApiController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @PostMapping("/api/restaurants/{id}/favorite")
    public ResponseEntity<ApiSuccessResponse<FavoriteState>> favorite(@PathVariable("id") long id,
                                                                      Authentication authentication) {
        FavoriteState state = favoriteService.favorite(id, requireMemberId(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiSuccessResponse.of(state));
    }

    @DeleteMapping("/api/restaurants/{id}/favorite")
    public ApiSuccessResponse<FavoriteState> unfavorite(@PathVariable("id") long id,
                                                        Authentication authentication) {
        return ApiSuccessResponse.of(favoriteService.unfavorite(id, requireMemberId(authentication)));
    }

    @GetMapping("/api/me/favorites")
    public ApiSuccessResponse<PageResponse<RestaurantSummary>> list(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "12") int size,
            Authentication authentication) {
        return ApiSuccessResponse.of(favoriteService.list(requireMemberId(authentication), page, size));
    }

    /** JWT 인증 주체의 회원 ID. 없으면 TOKEN_INVALID(401)다. ReviewApiController와 같은 규칙이다. */
    private static long requireMemberId(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof Long memberId) {
            return memberId;
        }
        throw new BusinessException(ErrorCode.TOKEN_INVALID);
    }
}
