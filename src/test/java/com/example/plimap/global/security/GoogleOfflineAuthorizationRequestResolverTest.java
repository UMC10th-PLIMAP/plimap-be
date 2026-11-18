package com.example.plimap.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

class GoogleOfflineAuthorizationRequestResolverTest {

    private final GoogleOfflineAuthorizationRequestResolver resolver = new GoogleOfflineAuthorizationRequestResolver(
            new InMemoryClientRegistrationRepository(google(), kakao()), "/oauth/authorization");

    @Test
    void 구글_로그인은_offline_접근을_요청하고_동의_화면은_강제하지_않는다() {
        OAuth2AuthorizationRequest request = resolver.resolve(authorizationRequest("google", false));

        assertThat(request.getAuthorizationRequestUri())
                .contains("access_type=offline")
                .doesNotContain("prompt=consent");
    }

    @Test
    void 탈퇴_재로그인일_때만_구글_동의_화면을_강제한다() {
        OAuth2AuthorizationRequest request = resolver.resolve(authorizationRequest("google", true));

        assertThat(request.getAuthorizationRequestUri())
                .contains("access_type=offline")
                .contains("prompt=consent");
    }

    @Test
    void 카카오_로그인은_파라미터를_추가하지_않는다() {
        OAuth2AuthorizationRequest request = resolver.resolve(authorizationRequest("kakao", true));

        assertThat(request.getAuthorizationRequestUri())
                .doesNotContain("access_type")
                .doesNotContain("prompt");
    }

    private static MockHttpServletRequest authorizationRequest(String registrationId, boolean reauth) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth/authorization/" + registrationId);
        request.setServletPath("/oauth/authorization/" + registrationId);
        if (reauth) {
            request.setParameter("reauth", "true");
        }
        return request;
    }

    private static ClientRegistration google() {
        return CommonOAuth2Provider.GOOGLE.getBuilder("google")
                .clientId("google-client-id")
                .clientSecret("google-client-secret")
                .redirectUri("http://localhost:8080/oauth/callback/google")
                .build();
    }

    private static ClientRegistration kakao() {
        return ClientRegistration.withRegistrationId("kakao")
                .clientId("kakao-client-id")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost:8080/oauth/callback/kakao")
                .authorizationUri("https://kauth.kakao.com/oauth/authorize")
                .tokenUri("https://kauth.kakao.com/oauth/token")
                .build();
    }
}
