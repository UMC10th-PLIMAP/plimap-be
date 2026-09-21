package com.example.plimap.domain.auth.demo;

import com.example.plimap.domain.auth.config.DemoAuthProperties;
import com.example.plimap.domain.auth.entity.AuthMember;
import com.example.plimap.domain.auth.exception.AuthErrorCode;
import com.example.plimap.global.apiPayload.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

@RequiredArgsConstructor
public class DemoAccountProtectionFilter extends OncePerRequestFilter {

    private static final RequestMatcher WITHDRAWAL_REQUEST = PathPatternRequestMatcher.withDefaults()
            .matcher(HttpMethod.DELETE, "/api/v1/members/me");

    private final DemoAuthProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !WITHDRAWAL_REQUEST.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Long demoMemberId = properties.memberId();
        // 접속 기능을 꺼도 이미 발급된 토큰으로 공용 계정을 탈퇴할 수 없어야 한다.
        if (demoMemberId != null && demoMemberId > 0
                && authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AuthMember authMember
                && demoMemberId.equals(authMember.getMember().getId())) {
            AuthErrorCode errorCode = AuthErrorCode.DEMO_ACCOUNT_WITHDRAWAL_NOT_ALLOWED;
            response.setStatus(errorCode.getStatus().value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            objectMapper.writeValue(response.getOutputStream(), ApiResponse.failure(errorCode));
            return;
        }

        filterChain.doFilter(request, response);
    }
}
