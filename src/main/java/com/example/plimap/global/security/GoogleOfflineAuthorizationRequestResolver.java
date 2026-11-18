package com.example.plimap.global.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;

// 웹 구글 로그인에 access_type=offline을 붙여 탈퇴 시 연결 끊기에 쓸 refresh token을 받는다(OAuthSuccessHandler).
// 구글은 처음 동의할 때만 refresh token을 주므로, 저장된 토큰이 없어 탈퇴가 409(AUTH_GOOGLE_REAUTH_REQUIRED)로
// 막힌 웹 회원이 reauth=true로 다시 로그인할 때만 prompt=consent로 동의 화면을 띄워 새 refresh token을 받는다.
// 평소 로그인에는 동의 화면이 뜨지 않는다.
public class GoogleOfflineAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    private static final String REAUTH_PARAMETER = "reauth";

    private final DefaultOAuth2AuthorizationRequestResolver delegate;

    public GoogleOfflineAuthorizationRequestResolver(
            ClientRegistrationRepository clientRegistrationRepository,
            String authorizationRequestBaseUri
    ) {
        this.delegate = new DefaultOAuth2AuthorizationRequestResolver(
                clientRegistrationRepository, authorizationRequestBaseUri);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return customize(request, delegate.resolve(request));
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        return customize(request, delegate.resolve(request, clientRegistrationId));
    }

    private OAuth2AuthorizationRequest customize(HttpServletRequest request, OAuth2AuthorizationRequest authorizationRequest) {
        if (authorizationRequest == null
                || !"google".equals(authorizationRequest.getAttribute(OAuth2ParameterNames.REGISTRATION_ID))) {
            return authorizationRequest;
        }
        boolean reauth = "true".equals(request.getParameter(REAUTH_PARAMETER));
        return OAuth2AuthorizationRequest.from(authorizationRequest)
                .additionalParameters(params -> {
                    params.put("access_type", "offline");
                    if (reauth) {
                        params.put("prompt", "consent");
                    }
                })
                .build();
    }
}
