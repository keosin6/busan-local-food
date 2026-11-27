package com.busanlocalfood.favorite.web;

import com.busanlocalfood.favorite.service.FavoriteService;
import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.member.web.ScreenSession;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.PageResponse;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.RestaurantSummary;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 찜 화면 (FUNCTION_SPEC F1-06). 맛집 상세의 찜 버튼, 찜 목록 화면의 취소 버튼이 쓴다.
 * 화면 세션과 CSRF를 쓴다 (SecurityConfig가 경로를 보호한다). 결과는 맛집 상세나 찜 목록으로 돌아가 안내 문구로 보여 준다.
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

    @PostMapping("/restaurants/{id}/favorite")
    public String favorite(@PathVariable("id") long id) {
        Long memberId = ScreenSession.currentMemberId().orElse(null);
        if (memberId == null) {
            return "redirect:/login";
        }
        try {
            favoriteService.favorite(id, memberId);
            return "redirect:/restaurants/" + id + "?favorite=added#favorite";
        } catch (BusinessException e) {
            return switch (e.getErrorCode()) {
                case RESTAURANT_NOT_FOUND -> "redirect:/restaurants?error=not_found";
                case FAVORITE_ALREADY_EXISTS -> "redirect:/restaurants/" + id + "?favoriteError=duplicate#favorite";
                case TOKEN_INVALID -> "redirect:/login";
                default -> throw e;
            };
        }
    }

    /**
     * 찜 취소. from=favorites이면 찜 목록으로 돌아간다. 그 밖의 값은 맛집 상세로 돌아간다.
     * from은 이 두 값 중 하나로만 다뤄서 외부 주소로 보내지 않는다.
     */
    @PostMapping("/restaurants/{id}/favorite/delete")
    public String unfavorite(@PathVariable("id") long id,
                             @RequestParam(name = "from", required = false) String from,
                             @RequestParam(name = "page", defaultValue = "0") int page) {
        Long memberId = ScreenSession.currentMemberId().orElse(null);
        if (memberId == null) {
            return "redirect:/login";
        }
        boolean fromList = "favorites".equals(from);
        String detailPath = "/restaurants/" + id;
        try {
            favoriteService.unfavorite(id, memberId);
            return fromList
                    ? "redirect:" + LIST_PATH + "?page=" + Math.max(page, 0) + "&favorite=removed"
                    : "redirect:" + detailPath + "?favorite=removed#favorite";
        } catch (BusinessException e) {
            return switch (e.getErrorCode()) {
                case RESTAURANT_NOT_FOUND -> "redirect:/restaurants?error=not_found";
                case FAVORITE_NOT_FOUND -> fromList
                        ? "redirect:" + LIST_PATH + "?page=" + Math.max(page, 0) + "&favoriteError=not_favorited"
                        : "redirect:" + detailPath + "?favoriteError=not_favorited#favorite";
                case TOKEN_INVALID -> "redirect:/login";
                default -> throw e;
            };
        }
    }

    /** 내 찜 목록 화면. 로그인하지 않았으면 /login으로 보낸다. */
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
}
