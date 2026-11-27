package com.busanlocalfood.web;

import com.busanlocalfood.favorite.service.FavoriteService;
import com.busanlocalfood.favorite.web.FavoriteNotices;
import com.busanlocalfood.member.web.ScreenSession;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.RestaurantSummary;
import com.busanlocalfood.restaurant.service.RestaurantService;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 공개 메인 홈 `/` (FUNCTION_SPEC F1-08). 누구나 볼 수 있다.
 * 서비스 소개와 검색 진입, 카테고리별 탐색, 최신 등록 맛집 8곳을 보여 준다. 맛집 목록과 검색 결과는 /restaurants가 맡는다.
 * 로그인 회원에게만 카드의 찜 버튼 상태를 계산한다. 홈은 AI를 부르지 않는다.
 */
@Controller
public class HomeController {

    static final String VIEW = "home";
    static final int LATEST_COUNT = 8;

    private final RestaurantService restaurantService;
    private final FavoriteService favoriteService;

    public HomeController(RestaurantService restaurantService, FavoriteService favoriteService) {
        this.restaurantService = restaurantService;
        this.favoriteService = favoriteService;
    }

    @GetMapping("/")
    public String home(@RequestParam(name = "favorite", required = false) String favorite,
                       @RequestParam(name = "favoriteError", required = false) String favoriteError,
                       Model model) {
        List<RestaurantSummary> latest = restaurantService.latest(LATEST_COUNT);
        List<Long> ids = latest.stream().map(RestaurantSummary::id).toList();
        Long viewerId = ScreenSession.currentMemberId().orElse(null);
        Set<Long> favorited = viewerId == null || ids.isEmpty() ? Set.of()
                : favoriteService.favoritedAmong(viewerId, ids);

        model.addAttribute("latest", latest);
        model.addAttribute("categories", restaurantService.categories());
        model.addAttribute("favoritedIds", favorited);
        model.addAttribute("favoriteNotice", FavoriteNotices.notice(favorite, favoriteError));
        // 카드 프래그먼트가 쓰는 값. 홈은 목록 조건이 없으므로 고정 값을 준다.
        model.addAttribute("restaurants", latest);
        model.addAttribute("listFrom", "home");
        model.addAttribute("keyword", "");
        model.addAttribute("sort", "latest");
        model.addAttribute("page", 0);
        model.addAttribute("pageSize", LATEST_COUNT);
        return VIEW;
    }
}
