package com.busanlocalfood.favorite.web;

import com.busanlocalfood.favorite.service.FavoriteService;
import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.member.web.ScreenSession;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.PageResponse;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.RestaurantSummary;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 찜 화면 (FUNCTION_SPEC F1-06). 맛집 상세의 찜 버튼, 맛집 목록 카드의 찜 버튼, 찜 목록 화면의 취소 버튼이 쓴다.
 * 화면 세션과 CSRF를 쓴다 (SecurityConfig가 경로를 보호한다). 제출이 끝나면 누른 화면으로 돌아간다.
 *
 * 돌아갈 곳은 from 값 세 개(favorites, restaurants, home)만 인정한다. 그 밖의 값은 맛집 상세로 간다.
 * 외부 주소를 받지 않으므로 열린 리다이렉트가 생기지 않는다. 검색 조건과 페이지는 서버가 다시 조립한다.
 */
@Controller
public class FavoriteScreenController {

    static final String LIST_VIEW = "favorite-list";
    static final String LIST_PATH = "/favorites";
    static final int PAGE_SIZE = 12;

    private final FavoriteService favoriteService;

    public FavoriteScreenController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    /** 돌아갈 화면. 허용된 값이 아니면 맛집 상세다. */
    enum Origin {
        DETAIL, FAVORITES, RESTAURANTS, HOME;

        static Origin of(String from) {
            if (from == null) {
                return DETAIL;
            }
            return switch (from) {
                case "favorites" -> FAVORITES;
                case "restaurants" -> RESTAURANTS;
                case "home" -> HOME;
                default -> DETAIL;
            };
        }
    }

    @PostMapping("/restaurants/{id}/favorite")
    public String favorite(@PathVariable("id") long id,
                           @RequestParam(name = "from", required = false) String from,
                           @RequestParam(name = "category", required = false) Long category,
                           @RequestParam(name = "keyword", required = false) String keyword,
                           @RequestParam(name = "sort", required = false) String sort,
                           @RequestParam(name = "page", defaultValue = "0") int page,
                           @RequestParam(name = "size", defaultValue = "12") int size) {
        Long memberId = ScreenSession.currentMemberId().orElse(null);
        if (memberId == null) {
            return "redirect:/login";
        }
        Origin origin = Origin.of(from);
        try {
            favoriteService.favorite(id, memberId);
            return back(origin, id, category, keyword, sort, page, size, "favorite", "added");
        } catch (BusinessException e) {
            return switch (e.getErrorCode()) {
                case RESTAURANT_NOT_FOUND -> "redirect:/restaurants?error=not_found";
                case FAVORITE_ALREADY_EXISTS ->
                        back(origin, id, category, keyword, sort, page, size, "favoriteError", "duplicate");
                case TOKEN_INVALID -> "redirect:/login";
                default -> throw e;
            };
        }
    }

    @PostMapping("/restaurants/{id}/favorite/delete")
    public String unfavorite(@PathVariable("id") long id,
                             @RequestParam(name = "from", required = false) String from,
                             @RequestParam(name = "category", required = false) Long category,
                             @RequestParam(name = "keyword", required = false) String keyword,
                             @RequestParam(name = "sort", required = false) String sort,
                             @RequestParam(name = "page", defaultValue = "0") int page,
                             @RequestParam(name = "size", defaultValue = "12") int size) {
        Long memberId = ScreenSession.currentMemberId().orElse(null);
        if (memberId == null) {
            return "redirect:/login";
        }
        Origin origin = Origin.of(from);
        try {
            favoriteService.unfavorite(id, memberId);
            return back(origin, id, category, keyword, sort, page, size, "favorite", "removed");
        } catch (BusinessException e) {
            return switch (e.getErrorCode()) {
                case RESTAURANT_NOT_FOUND -> "redirect:/restaurants?error=not_found";
                case FAVORITE_NOT_FOUND ->
                        back(origin, id, category, keyword, sort, page, size, "favoriteError", "not_favorited");
                case TOKEN_INVALID -> "redirect:/login";
                default -> throw e;
            };
        }
    }

    /** 찜 목록 화면. 로그인하지 않았으면 /login으로 보낸다. */
    @GetMapping(LIST_PATH)
    public String list(@RequestParam(name = "page", defaultValue = "0") int page,
                       @RequestParam(name = "favorite", required = false) String favorite,
                       @RequestParam(name = "favoriteError", required = false) String favoriteError,
                       Model model) {
        Long memberId = ScreenSession.currentMemberId().orElse(null);
        if (memberId == null) {
            return "redirect:/login";
        }
        PageResponse<RestaurantSummary> result = favoriteService.list(memberId, Math.max(page, 0), PAGE_SIZE);
        model.addAttribute("restaurants", result.content());
        model.addAttribute("page", result.page());
        model.addAttribute("totalPages", result.totalPages());
        model.addAttribute("totalElements", result.totalElements());
        model.addAttribute("hasPrev", result.page() > 0);
        model.addAttribute("hasNext", result.page() + 1 < result.totalPages());
        model.addAttribute("prevPage", result.page() - 1);
        model.addAttribute("nextPage", result.page() + 1);
        model.addAttribute("loggedIn", true);
        model.addAttribute("notice", FavoriteNotices.notice(favorite, favoriteError));
        return LIST_VIEW;
    }

    /** 누른 화면으로 돌아가는 리다이렉트 주소를 만든다. */
    private static String back(Origin origin, long id, Long category, String keyword, String sort, int page, int size,
                               String key, String value) {
        int safePage = Math.max(page, 0);
        switch (origin) {
            case FAVORITES:
                return "redirect:" + LIST_PATH + "?page=" + safePage + "&" + key + "=" + value;
            case RESTAURANTS:
            case HOME:
                String base = origin == Origin.HOME ? "/" : "/restaurants";
                return "redirect:" + base + "?" + listQuery(category, keyword, sort, safePage, size)
                        + "&" + key + "=" + value + "#restaurant-" + id;
            default:
                return "redirect:/restaurants/" + id + "?" + key + "=" + value + "#favorite";
        }
    }

    /** 검색 조건과 페이지를 목록 주소의 질의로 만든다. 값은 허용된 형식만 넣는다. */
    static String listQuery(Long category, String keyword, String sort, int page, int size) {
        StringBuilder query = new StringBuilder();
        if (category != null) {
            query.append("category=").append(category).append('&');
        }
        if (keyword != null && !keyword.isBlank()) {
            query.append("keyword=").append(URLEncoder.encode(keyword.trim(), StandardCharsets.UTF_8)).append('&');
        }
        if ("latest".equals(sort) || "rating".equals(sort)) {
            query.append("sort=").append(sort).append('&');
        }
        if (size >= 1 && size <= 50) {
            query.append("size=").append(size).append('&');
        }
        query.append("page=").append(page);
        return query.toString();
    }
}
