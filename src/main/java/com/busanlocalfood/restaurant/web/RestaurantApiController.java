package com.busanlocalfood.restaurant.web;

import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.global.exception.ErrorCode;
import com.busanlocalfood.global.response.ApiSuccessResponse;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.CategoryResponse;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.DeleteResult;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.PageResponse;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.RestaurantDetail;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.RestaurantRequest;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.RestaurantSummary;
import com.busanlocalfood.restaurant.service.RestaurantService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 카테고리와 맛집 API (API_SPEC 3.1, 4.1~4.5).
 * 조회(GET)는 비회원도 된다. 등록·수정·삭제는 JWT 인증이 필요하고, 작성자 확인은 서비스가 한다.
 * SecurityConfig의 API 체인이 GET 공개 경로와 나머지 인증 요구를 정한다.
 */
@RestController
public class RestaurantApiController {

    private final RestaurantService restaurantService;

    public RestaurantApiController(RestaurantService restaurantService) {
        this.restaurantService = restaurantService;
    }

    @GetMapping("/api/categories")
    public ApiSuccessResponse<List<CategoryResponse>> categories() {
        return ApiSuccessResponse.of(restaurantService.categories());
    }

    @GetMapping("/api/restaurants")
    public ApiSuccessResponse<PageResponse<RestaurantSummary>> list(
            @RequestParam(name = "category", required = false) Long category,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "sort", defaultValue = "latest") String sort,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "12") int size) {
        return ApiSuccessResponse.of(restaurantService.list(category, keyword, sort, page, size));
    }

    @GetMapping("/api/restaurants/{id}")
    public ApiSuccessResponse<RestaurantDetail> detail(@PathVariable("id") long id, Authentication authentication) {
        return ApiSuccessResponse.of(restaurantService.detail(id, memberIdOf(authentication)));
    }

    @PostMapping("/api/restaurants")
    public ResponseEntity<ApiSuccessResponse<RestaurantDetail>> create(@RequestBody RestaurantRequest request,
                                                                      Authentication authentication) {
        RestaurantDetail created = restaurantService.create(requireMemberId(authentication), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiSuccessResponse.of(created));
    }

    @PutMapping("/api/restaurants/{id}")
    public ApiSuccessResponse<RestaurantDetail> update(@PathVariable("id") long id,
                                                       @RequestBody RestaurantRequest request,
                                                       Authentication authentication) {
        return ApiSuccessResponse.of(restaurantService.update(id, requireMemberId(authentication), request));
    }

    @DeleteMapping("/api/restaurants/{id}")
    public ApiSuccessResponse<DeleteResult> delete(@PathVariable("id") long id, Authentication authentication) {
        return ApiSuccessResponse.of(restaurantService.delete(id, requireMemberId(authentication)));
    }

    /** JWT 인증 주체의 회원 ID. 인증되지 않았으면 null이다. */
    static Long memberIdOf(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof Long memberId) {
            return memberId;
        }
        return null;
    }

    static long requireMemberId(Authentication authentication) {
        Long memberId = memberIdOf(authentication);
        if (memberId == null) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        return memberId;
    }
}
