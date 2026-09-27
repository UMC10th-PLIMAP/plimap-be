package com.example.plimap.domain.auth.controller;

import com.example.plimap.domain.auth.controller.docs.AuthControllerDocs;
import com.example.plimap.domain.auth.dto.request.AuthReqDTO;
import com.example.plimap.domain.auth.dto.response.AuthResponse;
import com.example.plimap.domain.auth.entity.AuthMember;
import com.example.plimap.domain.auth.exception.AuthErrorCode;
import com.example.plimap.domain.auth.exception.AuthException;
import com.example.plimap.domain.auth.exception.AuthSuccessCode;
import com.example.plimap.domain.auth.service.command.AppOAuthCommandService;
import com.example.plimap.domain.member.converter.MemberConverter;
import com.example.plimap.domain.member.dto.request.MemberReqDTO;
import com.example.plimap.domain.member.dto.request.TermsReqDTO;
import com.example.plimap.domain.member.dto.response.MemberResponse;
import com.example.plimap.domain.member.dto.response.TermsResponse;
import com.example.plimap.domain.member.entity.Member;
import com.example.plimap.domain.member.enums.MemberStatus;
import com.example.plimap.domain.member.exception.MemberErrorCode;
import com.example.plimap.domain.member.exception.MemberException;
import com.example.plimap.domain.member.exception.MemberSuccessCode;
import com.example.plimap.domain.member.exception.TermsSuccessCode;
import com.example.plimap.domain.member.repository.MemberRepository;
import com.example.plimap.domain.member.service.command.MemberCommandService;
import com.example.plimap.domain.member.service.command.TermsCommandService;
import com.example.plimap.domain.member.service.query.TermsQueryService;
import com.example.plimap.global.apiPayload.ApiResponse;
import com.example.plimap.global.security.AuthCookieUtil;
import com.example.plimap.global.security.JwtUtil;
import com.example.plimap.global.security.RefreshTokenService;
import com.example.plimap.global.security.SessionInvalidationService;
import com.example.plimap.global.security.TokenResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController implements AuthControllerDocs {

    private final MemberCommandService memberCommandService;
    private final TermsQueryService termsQueryService;
    private final TermsCommandService termsCommandService;
    private final MemberRepository memberRepository;
    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;
    private final AuthCookieUtil authCookieUtil;
    private final SessionInvalidationService sessionInvalidationService;
    private final AppOAuthCommandService appOAuthCommandService;

    @Override
    @PostMapping("/app/login")
    public ApiResponse<AuthResponse.AppLogin> appLogin(@Valid @RequestBody AuthReqDTO.AppLogin request) {
        return ApiResponse.success(AuthSuccessCode.APP_LOGIN, appOAuthCommandService.login(request));
    }

    @Override
    @GetMapping("/csrf")
    public ApiResponse<AuthResponse.CsrfToken> getCsrfToken(CsrfToken csrfToken) {
        return ApiResponse.success(
                AuthSuccessCode.CSRF_TOKEN_ISSUED,
                new AuthResponse.CsrfToken(csrfToken.getToken())
        );
    }

    @Override
    @PostMapping("/onboarding")
    public ApiResponse<MemberResponse.Onboarding> onboarding(
            @AuthenticationPrincipal AuthMember authMember,
            @Valid @RequestBody MemberReqDTO.Onboarding request
    ) {
        Member member = memberCommandService.completeOnboarding(authMember.getMember().getId(), request);
        return ApiResponse.success(MemberSuccessCode.ONBOARDING_COMPLETED, MemberConverter.toOnboarding(member));
    }

    @Override
    @GetMapping("/terms")
    public ApiResponse<List<TermsResponse.Result>> getTermsAgreementStatus(@AuthenticationPrincipal AuthMember authMember) {
        List<TermsResponse.Result> result = termsQueryService.findTermsAgreementStatus(authMember.getMember().getId());
        return ApiResponse.success(TermsSuccessCode.TERMS_AGREEMENT_STATUS_RETRIEVED, result);
    }

    @Override
    @PostMapping("/terms")
    public ApiResponse<List<TermsResponse.Result>> agreeToTerms(
            @AuthenticationPrincipal AuthMember authMember,
            @Valid @RequestBody TermsReqDTO.Agree request
    ) {
        List<TermsResponse.Result> result = termsCommandService.agreeToTerms(authMember.getMember().getId(), request);
        return ApiResponse.success(TermsSuccessCode.TERMS_AGREED, result);
    }

    @Override
    @DeleteMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        sessionInvalidationService.invalidate(request, response);

        return ApiResponse.success(MemberSuccessCode.LOGOUT, null);
    }

    @Override
    @PostMapping("/reissue")
    public ApiResponse<Void> reissue(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = TokenResolver.resolveRefreshToken(request);
        if (refreshToken == null || !jwtUtil.isValid(refreshToken) || !jwtUtil.isRefreshToken(refreshToken)) {
            throw new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        Long memberId = jwtUtil.getMemberId(refreshToken);
        Member member = memberRepository.findByIdAndStatusAndDeletedAtIsNull(memberId, MemberStatus.ACTIVE)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));
        AuthMember authMember = new AuthMember(member);

        String newAccessToken = jwtUtil.createAccessToken(authMember);
        String newRefreshToken = jwtUtil.createRefreshToken(authMember);
        boolean rotated = refreshTokenService.rotateIfMatches(
                memberId,
                jwtUtil.getJti(refreshToken),
                jwtUtil.getJti(newRefreshToken),
                jwtUtil.getRefreshTokenExpiry()
        );
        if (!rotated) {
            throw new AuthException(AuthErrorCode.REFRESH_TOKEN_MISMATCH);
        }

        authCookieUtil.setCookie(response, "accessToken", newAccessToken, jwtUtil.getAccessTokenExpiry());
        authCookieUtil.setCookie(response, "refreshToken", newRefreshToken, jwtUtil.getRefreshTokenExpiry());

        return ApiResponse.success(AuthSuccessCode.TOKEN_REISSUED, null);
    }
}
