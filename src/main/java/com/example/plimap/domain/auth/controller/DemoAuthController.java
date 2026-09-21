package com.example.plimap.domain.auth.controller;

import com.example.plimap.domain.auth.controller.docs.DemoAuthControllerDocs;
import com.example.plimap.domain.auth.exception.AuthSuccessCode;
import com.example.plimap.domain.auth.service.command.DemoAuthCommandService;
import com.example.plimap.global.apiPayload.ApiResponse;
import com.example.plimap.global.security.AuthCookieUtil;
import com.example.plimap.global.security.JwtUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class DemoAuthController implements DemoAuthControllerDocs {

    private final DemoAuthCommandService demoAuthCommandService;
    private final AuthCookieUtil authCookieUtil;
    private final JwtUtil jwtUtil;

    @Override
    @PostMapping("/demo")
    public ApiResponse<Void> loginDemo(HttpServletResponse response) {
        String accessToken = demoAuthCommandService.issueAccessToken();
        authCookieUtil.setCookie(response, "accessToken", accessToken, jwtUtil.getAccessTokenExpiry());
        authCookieUtil.clearCookie(response, "refreshToken");
        return ApiResponse.success(AuthSuccessCode.DEMO_LOGIN, null);
    }
}
