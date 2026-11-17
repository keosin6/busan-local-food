package com.busanlocalfood.web;

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

@WebMvcTest(value = HomeController.class, properties = {
        "spring.datasource.url=jdbc:mysql://localhost:3306/busan_local_food_test",
        "spring.datasource.username=test",
        "spring.datasource.password=test-only"
})
@Import(SecurityConfig.class)
class HomeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    /** SecurityConfig가 요청 제한 빈을 요구하므로 슬라이스 테스트에서는 목으로 채운다. */
    @MockitoBean
    private AuthRateLimitService rateLimitService;

    @MockitoBean
    private ClientIpResolver clientIpResolver;

    @Test
    void homePageIsPublicAndLinksBootstrapAndAppAssets() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("home"))
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
