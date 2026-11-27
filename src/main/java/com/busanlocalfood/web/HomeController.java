package com.busanlocalfood.web;

import com.busanlocalfood.restaurant.web.RestaurantScreenController;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 공개 홈 `/`. API_SPEC 10과 FUNCTION_SPEC F1-02에 따라 맛집 목록 화면이 홈이다.
 * 목록 화면은 RestaurantScreenController가 만든다.
 */
@Controller
public class HomeController {

    private final RestaurantScreenController restaurantScreen;

    public HomeController(RestaurantScreenController restaurantScreen) {
        this.restaurantScreen = restaurantScreen;
    }

    @GetMapping("/")
    public String home(@RequestParam(name = "category", required = false) Long category,
                       @RequestParam(name = "keyword", required = false) String keyword,
                       @RequestParam(name = "sort", defaultValue = "latest") String sort,
                       @RequestParam(name = "page", defaultValue = "0") int page,
                       @RequestParam(name = "size", defaultValue = "12") int size,
                       @RequestParam(name = "error", required = false) String error,
                       @RequestParam(name = "deleted", required = false) String deleted,
                       @RequestParam(name = "storage", required = false) String storage,
                       @RequestParam(name = "favorite", required = false) String favorite,
                       @RequestParam(name = "favoriteError", required = false) String favoriteError,
                       Model model) {
        model.addAttribute("listFrom", "home");
        return restaurantScreen.listPage(category, keyword, sort, page, size, error, deleted, storage,
                favorite, favoriteError, model);
    }
}
