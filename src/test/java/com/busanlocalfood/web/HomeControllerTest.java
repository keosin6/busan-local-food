package com.busanlocalfood.web;

import com.busanlocalfood.restaurant.service.RestaurantService;
import com.busanlocalfood.restaurant.web.RestaurantScreenController;
import com.busanlocalfood.member.service.MemberAccountService;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.busanlocalfood.global.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import com.busanlocalfood.member.ratelimit.AuthRateLimitService;
import com.busanlocalfood.member.ratelimit.ClientIpResolver;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(value = {HomeController.class, RestaurantScreenController.class}, properties = {
        "spring.datasource.url=jdbc:mysql://localhost:3306/busan_local_food_test",
        "spring.datasource.username=test",
        "spring.datasource.password=test-only"
})
@Import(SecurityConfig.class)
class HomeControllerTest {

    @MockitoBean
    private RestaurantService restaurantService;

    @org.junit.jupiter.api.BeforeEach
    void emptyRestaurantList() {
        org.mockito.Mockito.when(restaurantService.list(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(new com.busanlocalfood.restaurant.dto.RestaurantDtos.PageResponse<>(java.util.List.of(), 0, 12, 0, 0));
    }

    @MockitoBean
    private MemberAccountService memberAccountService;

    @Autowired
    private MockMvc mockMvc;

    /** SecurityConfig가 요청 제한 빈을 요구하므로 슬라이스 테스트에서는 목으로 채운다. */
    @MockitoBean
    private AuthRateLimitService rateLimitService;

    @MockitoBean
    private ClientIpResolver clientIpResolver;

    @MockitoBean
    private com.busanlocalfood.member.jwt.AccessTokenVerifier accessTokenVerifier;

    @Test
    void homePageIsPublicAndLinksBootstrapAndAppAssets() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("restaurant-list"))
                .andExpect(content().string(containsString("/vendor/bootstrap/css/bootstrap.min.css")))
                .andExpect(content().string(containsString("/vendor/bootstrap/js/bootstrap.bundle.min.js")))
                .andExpect(content().string(containsString("/css/app.css")))
                .andExpect(content().string(containsString("/js/app.js")));
    }

    @Test
    void staticResourcesAreServed() throws Exception {
        mockMvc.perform(get("/vendor/bootstrap/css/bootstrap.min.css")).andExpect(status().isOk());
        mockMvc.perform(get("/vendor/bootstrap/js/bootstrap.bundle.min.js")).andExpect(status().isOk());
        mockMvc.perform(get("/css/app.css")).andExpect(status().isOk());
        mockMvc.perform(get("/js/app.js")).andExpect(status().isOk());
    }
}
