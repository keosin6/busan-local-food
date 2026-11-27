package com.busanlocalfood.admin.web;

import com.busanlocalfood.admin.dto.AdminDtos.AdminRestaurantItem;
import com.busanlocalfood.admin.service.AdminQueryService;
import com.busanlocalfood.global.response.ApiSuccessResponse;
import com.busanlocalfood.image.service.RestaurantImageService;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.DeleteResult;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.ImageDeleteResult;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.ImageSummary;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.PageResponse;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.RestaurantDetail;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.RestaurantRequest;
import com.busanlocalfood.restaurant.service.RestaurantService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 관리자 맛집 API (FUNCTION_SPEC F1-09, API_SPEC 11). 목록, 수정, 삭제, 이미지 업로드와 삭제.
 * 작성자 확인만 생략하고 나머지 흐름은 일반 맛집 API와 같다. 권한은 서비스가 확인한다.
 */
@RestController
@RequestMapping("/api/admin/restaurants")
public class AdminRestaurantApiController {

    private final AdminQueryService queries;
    private final RestaurantService restaurants;
    private final RestaurantImageService images;

    public AdminRestaurantApiController(AdminQueryService queries, RestaurantService restaurants,
                                        RestaurantImageService images) {
        this.queries = queries;
        this.restaurants = restaurants;
        this.images = images;
    }

    @GetMapping
    public ApiSuccessResponse<PageResponse<AdminRestaurantItem>> list(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "categoryId", required = false) Long categoryId,
            @RequestParam(name = "sort", defaultValue = "latest") String sort,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            Authentication authentication) {
        return ApiSuccessResponse.of(queries.restaurants(authentication, keyword, categoryId, sort, page, size));
    }

    @PutMapping("/{id}")
    public ApiSuccessResponse<RestaurantDetail> update(@PathVariable("id") long id,
                                                       @RequestBody RestaurantRequest request,
                                                       Authentication authentication) {
        return ApiSuccessResponse.of(restaurants.updateAsAdmin(id, authentication, request));
    }

    @DeleteMapping("/{id}")
    public ApiSuccessResponse<DeleteResult> delete(@PathVariable("id") long id, Authentication authentication) {
        return ApiSuccessResponse.of(restaurants.deleteAsAdmin(id, authentication));
    }

    @PostMapping("/{id}/images")
    public ResponseEntity<ApiSuccessResponse<ImageSummary>> upload(
            @PathVariable("id") long id,
            @RequestParam(name = "file", required = false) MultipartFile file,
            Authentication authentication) {
        ImageSummary created = images.uploadAsAdmin(id, authentication, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiSuccessResponse.of(created));
    }

    @DeleteMapping("/{id}/images/{imageId}")
    public ApiSuccessResponse<ImageDeleteResult> deleteImage(@PathVariable("id") long id,
                                                             @PathVariable("imageId") long imageId,
                                                             Authentication authentication) {
        return ApiSuccessResponse.of(images.deleteAsAdmin(id, imageId, authentication));
    }
}
