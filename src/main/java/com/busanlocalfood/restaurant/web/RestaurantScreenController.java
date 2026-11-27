package com.busanlocalfood.restaurant.web;

import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.global.exception.ErrorCode;
import com.busanlocalfood.member.web.ScreenSession;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.CategoryResponse;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.DeleteResult;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.PageResponse;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.RestaurantDetail;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.RestaurantRequest;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.RestaurantSummary;
import com.busanlocalfood.restaurant.service.RestaurantService;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 맛집 화면 (FUNCTION_SPEC F1-02, Thymeleaf). 목록과 상세는 누구나 본다.
 * 등록·수정·삭제 화면과 제출은 화면 세션 인증이 필요하다 (SecurityConfig). 작성자 확인은 서비스가 한다.
 * 버튼 표시 여부는 화면의 편의일 뿐이다. 권한은 POST마다 서비스가 다시 검사한다.
 */
@Controller
public class RestaurantScreenController {

    static final String LIST_VIEW = "restaurant-list";
    static final String DETAIL_VIEW = "restaurant-detail";
    static final String FORM_VIEW = "restaurant-form";

    private final RestaurantService restaurantService;

    public RestaurantScreenController(RestaurantService restaurantService) {
        this.restaurantService = restaurantService;
    }

    /** `/`와 `/restaurants`의 목록 화면. HomeController도 이 메서드를 쓴다. */
    public String listPage(Long category, String keyword, String sort, int page, int size,
                           String error, String deleted, String storage, Model model) {
        PageResponse<RestaurantSummary> result;
        try {
            result = restaurantService.list(category, keyword, sort, page, size);
        } catch (BusinessException e) {
            return "redirect:/restaurants";
        }
        List<CategoryResponse> categories = restaurantService.categories();
        String keywordValue = keyword == null ? "" : keyword.trim();
        model.addAttribute("restaurants", result.content());
        model.addAttribute("categories", categories);
        model.addAttribute("category", category);
        model.addAttribute("keyword", keywordValue);
        model.addAttribute("sort", sort);
        model.addAttribute("page", result.page());
        model.addAttribute("totalPages", result.totalPages());
        model.addAttribute("totalElements", result.totalElements());
        model.addAttribute("hasPrev", result.page() > 0);
        model.addAttribute("hasNext", result.page() + 1 < result.totalPages());
        model.addAttribute("prevPage", result.page() - 1);
        model.addAttribute("nextPage", result.page() + 1);
        model.addAttribute("baseQuery", baseQuery(category, keywordValue, sort, result.size()));
        model.addAttribute("error", error);
        model.addAttribute("deleted", "1".equals(deleted));
        model.addAttribute("storageDelayed", "1".equals(storage));
        return LIST_VIEW;
    }

    @GetMapping("/restaurants")
    public String list(@RequestParam(name = "category", required = false) Long category,
                       @RequestParam(name = "keyword", required = false) String keyword,
                       @RequestParam(name = "sort", defaultValue = "latest") String sort,
                       @RequestParam(name = "page", defaultValue = "0") int page,
                       @RequestParam(name = "size", defaultValue = "12") int size,
                       @RequestParam(name = "error", required = false) String error,
                       @RequestParam(name = "deleted", required = false) String deleted,
                       @RequestParam(name = "storage", required = false) String storage,
                       Model model) {
        return listPage(category, keyword, sort, page, size, error, deleted, storage, model);
    }

    @GetMapping("/restaurants/{id}")
    public String detail(@PathVariable("id") long id,
                         @RequestParam(name = "error", required = false) String error,
                         Model model) {
        try {
            RestaurantDetail restaurant = restaurantService.detail(id, ScreenSession.currentMemberId().orElse(null));
            model.addAttribute("restaurant", restaurant);
        } catch (BusinessException e) {
            return "redirect:/restaurants?error=not_found";
        }
        model.addAttribute("error", error);
        return DETAIL_VIEW;
    }

    @GetMapping("/restaurants/new")
    public String newForm(Model model) {
        model.addAttribute("mode", "new");
        model.addAttribute("form", emptyForm());
        model.addAttribute("categories", restaurantService.categories());
        return FORM_VIEW;
    }

    @PostMapping("/restaurants/new")
    public String create(@RequestParam(name = "name", defaultValue = "") String name,
                         @RequestParam(name = "categoryId", defaultValue = "") String categoryId,
                         @RequestParam(name = "address", defaultValue = "") String address,
                         @RequestParam(name = "phone", defaultValue = "") String phone,
                         @RequestParam(name = "description", defaultValue = "") String description,
                         @RequestParam(name = "latitude", defaultValue = "") String latitude,
                         @RequestParam(name = "longitude", defaultValue = "") String longitude,
                         Model model) {
        Long memberId = ScreenSession.currentMemberId().orElse(null);
        if (memberId == null) {
            return "redirect:/login";
        }
        Map<String, String> form = formValues(name, categoryId, address, phone, description, latitude, longitude);
        try {
            RestaurantDetail created = restaurantService.create(memberId, toRequest(form));
            return "redirect:/restaurants/" + created.id();
        } catch (BusinessException e) {
            return formError(model, form, "new", null, e);
        }
    }

    @GetMapping("/restaurants/{id}/edit")
    public String editForm(@PathVariable("id") long id, Model model) {
        Long memberId = ScreenSession.currentMemberId().orElse(null);
        if (memberId == null) {
            return "redirect:/login";
        }
        try {
            RestaurantDetail restaurant = restaurantService.loadForEdit(id, memberId);
            model.addAttribute("mode", "edit");
            model.addAttribute("restaurantId", id);
            model.addAttribute("form", formValues(restaurant));
            model.addAttribute("categories", restaurantService.categories());
            return FORM_VIEW;
        } catch (BusinessException e) {
            return redirectAfterFailure(e, id);
        }
    }

    @PostMapping("/restaurants/{id}/edit")
    public String update(@PathVariable("id") long id,
                         @RequestParam(name = "name", defaultValue = "") String name,
                         @RequestParam(name = "categoryId", defaultValue = "") String categoryId,
                         @RequestParam(name = "address", defaultValue = "") String address,
                         @RequestParam(name = "phone", defaultValue = "") String phone,
                         @RequestParam(name = "description", defaultValue = "") String description,
                         @RequestParam(name = "latitude", defaultValue = "") String latitude,
                         @RequestParam(name = "longitude", defaultValue = "") String longitude,
                         Model model) {
        Long memberId = ScreenSession.currentMemberId().orElse(null);
        if (memberId == null) {
            return "redirect:/login";
        }
        Map<String, String> form = formValues(name, categoryId, address, phone, description, latitude, longitude);
        try {
            restaurantService.update(id, memberId, toRequest(form));
            return "redirect:/restaurants/" + id;
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.FORBIDDEN || e.getErrorCode() == ErrorCode.RESTAURANT_NOT_FOUND) {
                return redirectAfterFailure(e, id);
            }
            return formError(model, form, "edit", id, e);
        }
    }

    @PostMapping("/restaurants/{id}/delete")
    public String delete(@PathVariable("id") long id) {
        Long memberId = ScreenSession.currentMemberId().orElse(null);
        if (memberId == null) {
            return "redirect:/login";
        }
        try {
            DeleteResult result = restaurantService.delete(id, memberId);
            String storage = result.storageDeleteFailedCount() > 0 ? "&storage=1" : "";
            return "redirect:/restaurants?deleted=1" + storage;
        } catch (BusinessException e) {
            return redirectAfterFailure(e, id);
        }
    }

    private static String redirectAfterFailure(BusinessException e, long id) {
        if (e.getErrorCode() == ErrorCode.FORBIDDEN) {
            return "redirect:/restaurants/" + id + "?error=forbidden";
        }
        if (e.getErrorCode() == ErrorCode.RESTAURANT_NOT_FOUND) {
            return "redirect:/restaurants?error=not_found";
        }
        throw e;
    }

    private String formError(Model model, Map<String, String> form, String mode, Long restaurantId,
                             BusinessException e) {
        if (e.getErrorCode() != ErrorCode.VALIDATION_FAILED && e.getErrorCode() != ErrorCode.CATEGORY_NOT_FOUND) {
            throw e;
        }
        model.addAttribute("mode", mode);
        model.addAttribute("restaurantId", restaurantId);
        model.addAttribute("form", form);
        model.addAttribute("categories", restaurantService.categories());
        model.addAttribute("formError", e.getErrorCode() == ErrorCode.CATEGORY_NOT_FOUND
                ? "선택한 카테고리를 찾을 수 없습니다. 다시 선택해 주세요."
                : "입력값을 확인해 주세요. 이름 1~50자, 주소 1~200자, 설명 1~2000자를 지켜 주세요.");
        return FORM_VIEW;
    }

    private static Map<String, String> emptyForm() {
        return formValues("", "", "", "", "", "", "");
    }

    private static Map<String, String> formValues(String name, String categoryId, String address, String phone,
                                                  String description, String latitude, String longitude) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("name", name);
        form.put("categoryId", categoryId);
        form.put("address", address);
        form.put("phone", phone);
        form.put("description", description);
        form.put("latitude", latitude);
        form.put("longitude", longitude);
        return form;
    }

    private static Map<String, String> formValues(RestaurantDetail restaurant) {
        return formValues(restaurant.name(), String.valueOf(restaurant.category().id()), restaurant.address(),
                restaurant.phone() == null ? "" : restaurant.phone(), restaurant.description(),
                decimalText(restaurant.latitude()), decimalText(restaurant.longitude()));
    }

    private static String decimalText(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    /** 화면 입력 문자열을 서비스 입력으로 바꾼다. 빈 값은 null이고, 형식이 틀리면 VALIDATION_FAILED다. */
    private static RestaurantRequest toRequest(Map<String, String> form) {
        return new RestaurantRequest(
                form.get("name"),
                parseLong(form.get("categoryId")),
                form.get("address"),
                blankToNull(form.get("phone")),
                form.get("description"),
                parseDecimal(form.get("latitude")),
                parseDecimal(form.get("longitude")));
    }

    private static Long parseLong(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
    }

    private static BigDecimal parseDecimal(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
    }

    private static String blankToNull(String raw) {
        return raw == null || raw.isBlank() ? null : raw;
    }

    private static String baseQuery(Long category, String keyword, String sort, int size) {
        StringBuilder query = new StringBuilder();
        if (category != null) {
            query.append("category=").append(category).append('&');
        }
        if (keyword != null && !keyword.isEmpty()) {
            query.append("keyword=").append(URLEncoder.encode(keyword, StandardCharsets.UTF_8)).append('&');
        }
        query.append("sort=").append(URLEncoder.encode(sort == null ? "latest" : sort, StandardCharsets.UTF_8));
        query.append("&size=").append(size);
        return query.toString();
    }
}
