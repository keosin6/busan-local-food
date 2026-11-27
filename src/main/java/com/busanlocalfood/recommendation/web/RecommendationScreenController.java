package com.busanlocalfood.recommendation.web;

import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.member.web.ScreenSession;
import com.busanlocalfood.recommendation.dto.RecommendationDtos.RecommendationResponse;
import com.busanlocalfood.recommendation.service.RestaurantRecommendationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * AI 추천 화면 (FUNCTION_SPEC F1-07, `/recommend`). 로그인 회원만 쓴다 (SecurityConfig가 세션을 확인한다).
 * 추천 받기는 POST이며 CSRF를 쓴다. 결과는 같은 화면에 그린다. 실패하면 다시 시도하도록 안내한다.
 */
@Controller
public class RecommendationScreenController {

    static final String VIEW = "recommend";

    private final RestaurantRecommendationService recommendations;

    public RecommendationScreenController(RestaurantRecommendationService recommendations) {
        this.recommendations = recommendations;
    }

    @GetMapping("/recommend")
    public String form(Model model) {
        model.addAttribute("preference", "");
        return VIEW;
    }

    @PostMapping("/recommend")
    public String recommend(@RequestParam(name = "preference", required = false) String preference, Model model) {
        if (ScreenSession.currentMemberId().isEmpty()) {
            return "redirect:/login";
        }
        String shown = preference == null ? "" : preference;
        model.addAttribute("preference", shown);
        try {
            RecommendationResponse result = recommendations.recommend(preference);
            model.addAttribute("result", result);
        } catch (BusinessException e) {
            model.addAttribute("errorMessage", messageOf(e));
        }
        return VIEW;
    }

    /** 사용자에게 보일 안내. AI 오류는 다시 시도하라고 안내한다. */
    static String messageOf(BusinessException e) {
        return switch (e.getErrorCode()) {
            case VALIDATION_FAILED -> "취향은 1~200자로 입력해 주세요.";
            case AI_UNAVAILABLE -> "AI 추천을 가져오지 못했습니다. 잠시 후 '추천 받기'를 다시 눌러 주세요.";
            case AI_RESPONSE_INVALID -> "AI 추천 결과를 처리하지 못했습니다. '추천 받기'를 다시 눌러 주세요.";
            default -> throw e;
        };
    }
}
