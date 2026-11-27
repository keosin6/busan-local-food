package com.busanlocalfood.admin.web;

import com.busanlocalfood.admin.dto.AdminDtos.RetryResult;
import com.busanlocalfood.admin.service.AdminQueryService;
import com.busanlocalfood.admin.service.AdminStorageRetryService;
import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.global.exception.ErrorCode;
import com.busanlocalfood.image.service.RestaurantImageService;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.RestaurantRequest;
import com.busanlocalfood.restaurant.service.RestaurantService;
import com.busanlocalfood.review.service.ReviewService;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.multipart.MultipartFile;

/**
 * 관리자 화면 (FUNCTION_SPEC F1-09, /admin/**). SecurityConfig가 ADMIN만 허용하고, 서비스가 다시 확인한다.
 * 제출은 POST와 CSRF를 쓰고, 결과는 플래시 속성으로 다음 화면에 보여 준 뒤 리다이렉트한다.
 * 회원 화면은 조회 전용이다. 회원 역할 변경, 정지, 탈퇴 버튼은 없다.
 */
@Controller
public class AdminScreenController {

    static final String ADMIN = "/admin";
    static final String NOTICE = "notice";
    static final String ERROR = "error";

    private final AdminQueryService queries;
    private final AdminStorageRetryService storageRetry;
    private final RestaurantService restaurants;
    private final RestaurantImageService images;
    private final ReviewService reviews;

    public AdminScreenController(AdminQueryService queries, AdminStorageRetryService storageRetry,
                                 RestaurantService restaurants, RestaurantImageService images,
                                 ReviewService reviews) {
        this.queries = queries;
        this.storageRetry = storageRetry;
        this.restaurants = restaurants;
        this.images = images;
        this.reviews = reviews;
    }

    @GetMapping(ADMIN)
    public String dashboard(Authentication authentication, Model model) {
        model.addAttribute("dashboard", queries.dashboard(authentication));
        return "admin-dashboard";
    }

    @GetMapping(ADMIN + "/restaurants")
    public String restaurantList(@RequestParam(name = "keyword", required = false) String keyword,
                                 @RequestParam(name = "categoryId", required = false) Long categoryId,
                                 @RequestParam(name = "sort", defaultValue = "latest") String sort,
                                 @RequestParam(name = "page", defaultValue = "0") int page,
                                 @RequestParam(name = "size", defaultValue = "20") int size,
                                 Authentication authentication, Model model) {
        var result = queries.restaurants(authentication, keyword, categoryId, sort, page, size);
        model.addAttribute("result", result);
        model.addAttribute("categories", restaurants.categories());
        model.addAttribute("keyword", keyword == null ? "" : keyword.trim());
        model.addAttribute("categoryId", categoryId);
        model.addAttribute("sort", sort);
        model.addAttribute("baseQuery", query(Map.of(
                "keyword", keyword == null ? "" : keyword.trim(),
                "categoryId", categoryId == null ? "" : categoryId.toString(),
                "sort", sort, "size", String.valueOf(result.size()))));
        return "admin-restaurants";
    }

    @GetMapping(ADMIN + "/restaurants/{id}/edit")
    public String restaurantEdit(@PathVariable("id") long id, Authentication authentication, Model model) {
        model.addAttribute("restaurant", restaurants.loadForEditAsAdmin(id, authentication));
        model.addAttribute("categories", restaurants.categories());
        return "admin-restaurant-edit";
    }

    @PostMapping(ADMIN + "/restaurants/{id}/edit")
    public String restaurantUpdate(@PathVariable("id") long id,
                                   @RequestParam(name = "name", defaultValue = "") String name,
                                   @RequestParam(name = "categoryId", required = false) Long categoryId,
                                   @RequestParam(name = "address", defaultValue = "") String address,
                                   @RequestParam(name = "phone", required = false) String phone,
                                   @RequestParam(name = "description", defaultValue = "") String description,
                                   @RequestParam(name = "latitude", required = false) String latitude,
                                   @RequestParam(name = "longitude", required = false) String longitude,
                                   Authentication authentication, RedirectAttributes redirect) {
        try {
            restaurants.updateAsAdmin(id, authentication, new RestaurantRequest(name, categoryId, address,
                    blankToNull(phone), description, decimal(latitude), decimal(longitude)));
            redirect.addFlashAttribute(NOTICE, "맛집 정보를 저장했습니다.");
        } catch (BusinessException e) {
            redirect.addFlashAttribute(ERROR, e.getErrorCode().getMessage());
        }
        return "redirect:" + ADMIN + "/restaurants/" + id + "/edit";
    }

    @PostMapping(ADMIN + "/restaurants/{id}/delete")
    public String restaurantDelete(@PathVariable("id") long id, Authentication authentication,
                                   RedirectAttributes redirect) {
        try {
            var result = restaurants.deleteAsAdmin(id, authentication);
            redirect.addFlashAttribute(NOTICE, result.storageDeleteFailedCount() > 0
                    ? "맛집을 삭제했습니다. 일부 이미지 삭제가 지연되어 재시도 대기 목록에 남았습니다."
                    : "맛집을 삭제했습니다.");
        } catch (BusinessException e) {
            redirect.addFlashAttribute(ERROR, e.getErrorCode().getMessage());
        }
        return "redirect:" + ADMIN + "/restaurants";
    }

    @PostMapping(ADMIN + "/restaurants/{id}/images")
    public String imageUpload(@PathVariable("id") long id,
                              @RequestParam(name = "file", required = false) MultipartFile file,
                              Authentication authentication, RedirectAttributes redirect) {
        try {
            images.uploadAsAdmin(id, authentication, file);
            redirect.addFlashAttribute(NOTICE, "사진을 올렸습니다.");
        } catch (BusinessException e) {
            redirect.addFlashAttribute(ERROR, e.getErrorCode().getMessage());
        }
        return "redirect:" + ADMIN + "/restaurants/" + id + "/edit";
    }

    @PostMapping(ADMIN + "/restaurants/{id}/images/{imageId}/delete")
    public String imageDelete(@PathVariable("id") long id, @PathVariable("imageId") long imageId,
                              Authentication authentication, RedirectAttributes redirect) {
        try {
            var result = images.deleteAsAdmin(id, imageId, authentication);
            redirect.addFlashAttribute(NOTICE, result.storageDeleted()
                    ? "사진을 삭제했습니다."
                    : "사진을 삭제했습니다. 저장소 삭제는 재시도 대기 목록에 남았습니다.");
        } catch (BusinessException e) {
            redirect.addFlashAttribute(ERROR, e.getErrorCode().getMessage());
        }
        return "redirect:" + ADMIN + "/restaurants/" + id + "/edit";
    }

    @GetMapping(ADMIN + "/reviews")
    public String reviewList(@RequestParam(name = "restaurantId", required = false) Long restaurantId,
                             @RequestParam(name = "memberId", required = false) Long memberId,
                             @RequestParam(name = "page", defaultValue = "0") int page,
                             @RequestParam(name = "size", defaultValue = "20") int size,
                             Authentication authentication, Model model) {
        var result = queries.reviews(authentication, restaurantId, memberId, page, size);
        model.addAttribute("result", result);
        model.addAttribute("restaurantId", restaurantId);
        model.addAttribute("memberId", memberId);
        model.addAttribute("baseQuery", query(Map.of(
                "restaurantId", restaurantId == null ? "" : restaurantId.toString(),
                "memberId", memberId == null ? "" : memberId.toString(),
                "size", String.valueOf(result.size()))));
        return "admin-reviews";
    }

    @PostMapping(ADMIN + "/reviews/{reviewId}/delete")
    public String reviewDelete(@PathVariable("reviewId") long reviewId, Authentication authentication,
                               RedirectAttributes redirect) {
        try {
            reviews.deleteAsAdmin(reviewId, authentication);
            redirect.addFlashAttribute(NOTICE, "리뷰를 삭제했습니다. 맛집 평점과 리뷰 수를 다시 계산했습니다.");
        } catch (BusinessException e) {
            redirect.addFlashAttribute(ERROR, e.getErrorCode().getMessage());
        }
        return "redirect:" + ADMIN + "/reviews";
    }

    @GetMapping(ADMIN + "/members")
    public String memberList(@RequestParam(name = "keyword", required = false) String keyword,
                             @RequestParam(name = "page", defaultValue = "0") int page,
                             @RequestParam(name = "size", defaultValue = "20") int size,
                             Authentication authentication, Model model) {
        var result = queries.members(authentication, keyword, page, size);
        model.addAttribute("result", result);
        model.addAttribute("keyword", keyword == null ? "" : keyword.trim());
        model.addAttribute("baseQuery", query(Map.of(
                "keyword", keyword == null ? "" : keyword.trim(),
                "size", String.valueOf(result.size()))));
        return "admin-members";
    }

    @GetMapping(ADMIN + "/members/{memberId}")
    public String memberDetail(@PathVariable("memberId") long memberId, Authentication authentication, Model model) {
        model.addAttribute("member", queries.member(authentication, memberId));
        return "admin-member-detail";
    }

    @GetMapping(ADMIN + "/storage-deletions")
    public String storageList(@RequestParam(name = "page", defaultValue = "0") int page,
                              @RequestParam(name = "size", defaultValue = "20") int size,
                              Authentication authentication, Model model) {
        var result = queries.storageDeletions(authentication, page, size);
        model.addAttribute("result", result);
        model.addAttribute("baseQuery", query(Map.of("size", String.valueOf(result.size()))));
        return "admin-storage-deletions";
    }

    @PostMapping(ADMIN + "/storage-deletions/retry")
    public String storageRetry(Authentication authentication, RedirectAttributes redirect) {
        try {
            RetryResult result = storageRetry.retry(authentication);
            redirect.addFlashAttribute("retryResult", result);
            redirect.addFlashAttribute(NOTICE, "재시도를 마쳤습니다.");
        } catch (BusinessException e) {
            redirect.addFlashAttribute(ERROR, e.getErrorCode().getMessage());
        }
        return "redirect:" + ADMIN + "/storage-deletions";
    }

    /** 빈 값과 null은 뺀 질의 문자열을 만든다. 값은 URL 인코딩한다. */
    static String query(Map<String, String> params) {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String> e : new LinkedHashMap<>(params).entrySet()) {
            if (e.getValue() == null || e.getValue().isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append('&');
            }
            out.append(e.getKey()).append('=').append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
        }
        return out.toString();
    }

    private static String blankToNull(String raw) {
        return raw == null || raw.isBlank() ? null : raw.trim();
    }

    private static BigDecimal decimal(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
    }
}
