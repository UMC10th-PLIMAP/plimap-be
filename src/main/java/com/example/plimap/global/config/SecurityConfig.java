package com.example.plimap.global.config;

import com.example.plimap.domain.auth.service.command.impl.CustomOAuthService;
import com.example.plimap.domain.auth.service.command.impl.OAuthFailureHandler;
import com.example.plimap.domain.auth.service.command.impl.OAuthSuccessHandler;
import com.example.plimap.domain.member.repository.MemberRepository;
import com.example.plimap.global.security.AuthCookieUtil;
import com.example.plimap.global.security.BearerTokenRequestMatcher;
import com.example.plimap.global.security.CsrfCookieFilter;
import com.example.plimap.global.security.HttpCookieOAuth2AuthorizationRequestRepository;
import com.example.plimap.global.security.JwtAuthFilter;
import com.example.plimap.global.security.JwtUtil;
import com.example.plimap.global.security.OAuthFrontendOriginFilter;
import com.example.plimap.global.security.OAuthFrontendRedirectCookieRepository;
import com.example.plimap.global.security.SecurityErrorResponseHandler;
import com.example.plimap.global.security.TokenBlacklistService;
import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(OAuthProperties.class)
@Import({
        AuthCookieUtil.class,
        OAuthFrontendRedirectCookieRepository.class
})
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomOAuthService customOAuthService;
    private final OAuthSuccessHandler oAuthSuccessHandler;
    private final OAuthFailureHandler oAuthFailureHandler;
    private final JwtUtil jwtUtil;
    private final MemberRepository memberRepository;
    private final SecurityErrorResponseHandler securityErrorResponseHandler;
    private final TokenBlacklistService tokenBlacklistService;

    @Value("${cookie.secure}")
    private boolean cookieSecure;

    @Value("${cookie.same-site}")
    private String cookieSameSite;

    @Bean
    public CookieCsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookiePath("/");
        repository.setCookieCustomizer(cookie -> cookie
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/"));
        return repository;
    }

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            HttpCookieOAuth2AuthorizationRequestRepository authorizationRequestRepository,
            OAuthFrontendRedirectCookieRepository oAuthFrontendRedirectCookieRepository
    ) throws Exception {
        http
                .csrf(csrf -> csrf
                        .spa()
                        .csrfTokenRepository(csrfTokenRepository())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        // Swagger/Postman은 Bearer 인증을 사용하므로 CSRF 검증에서 제외
                        .ignoringRequestMatchers(new BearerTokenRequestMatcher())
                        // Swagger UI에서 바로 테스트하는 local/dev 전용 임시 API라 CSRF 토큰 없이도 허용
                        .ignoringRequestMatchers("/api/v1/auth/token/test")
                )
                .cors(withDefaults())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                "/oauth/**",
                                "/actuator/health/**",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/api/v1/auth/token/test",
                                "/api/v1/auth/reissue",
                                "/api/v1/pins/map",
                                "/api/v1/auth/csrf"
                        ).permitAll()
                        // 문의 등록은 비로그인 사용자도 이용할 수 있어야 하므로 인증 없이 허용한다.
                        .requestMatchers(HttpMethod.POST, "/api/v1/inquiries", "/api/v1/auth/demo").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasAuthority("ADMIN")
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(securityErrorResponseHandler)
                        .accessDeniedHandler(securityErrorResponseHandler)
                )
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .oauth2Login(oauth -> oauth
                        .authorizationEndpoint(endpoint -> endpoint
                                .baseUri("/oauth/authorization")
                                .authorizationRequestRepository(authorizationRequestRepository))
                        .redirectionEndpoint(endpoint ->
                                endpoint.baseUri("/oauth/callback/*"))
                        .userInfoEndpoint(userInfo ->
                                userInfo.userService(customOAuthService))
                        .successHandler(oAuthSuccessHandler)
                        .failureHandler(oAuthFailureHandler)
                )
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                .addFilterBefore(
                        new OAuthFrontendOriginFilter(oAuthFrontendRedirectCookieRepository),
                        OAuth2AuthorizationRequestRedirectFilter.class
                )
                .addFilterBefore(
                        new JwtAuthFilter(jwtUtil, memberRepository, tokenBlacklistService),
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
}
