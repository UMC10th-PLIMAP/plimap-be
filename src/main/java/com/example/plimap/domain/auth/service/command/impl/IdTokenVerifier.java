package com.example.plimap.domain.auth.service.command.impl;

import com.example.plimap.domain.auth.exception.AuthErrorCode;
import com.example.plimap.domain.auth.exception.AuthException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Locator;
import io.jsonwebtoken.LocatorAdapter;
import io.jsonwebtoken.ProtectedHeader;
import io.jsonwebtoken.security.Jwk;
import io.jsonwebtoken.security.JwkSet;
import io.jsonwebtoken.security.Jwks;
import java.security.Key;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

// 구글/애플 ID 토큰(JWT) 서명을 각 provider의 JWKS로 직접 검증한다.
// nimbus 등 신규 라이브러리 없이, 이미 있는 jjwt(Jwks 파서)만으로 처리한다.
@Component
class IdTokenVerifier {

    private static final Duration JWKS_CACHE_TTL = Duration.ofHours(1);

    private final RestClient restClient = RestClient.builder().build();
    private final ConcurrentHashMap<String, CachedJwkSet> jwkSetCache = new ConcurrentHashMap<>();

    Claims verify(String jwksUrl, Set<String> allowedIssuers, String audience, String idToken) {
        JwkSet jwkSet = getJwkSet(jwksUrl);
        Locator<Key> keyLocator = new LocatorAdapter<>() {
            @Override
            protected Key locate(ProtectedHeader header) {
                String kid = header.getKeyId();
                return jwkSet.getKeys().stream()
                        .filter(jwk -> kid != null && kid.equals(jwk.get("kid")))
                        .findFirst()
                        .map(Jwk::toKey)
                        .orElseThrow(() -> new AuthException(AuthErrorCode.APP_TOKEN_VERIFICATION_FAILED));
            }
        };

        Claims claims;
        try {
            claims = Jwts.parser()
                    .keyLocator(keyLocator)
                    .requireAudience(audience)
                    .build()
                    .parseSignedClaims(idToken)
                    .getPayload();
        } catch (AuthException exception) {
            throw exception;
        } catch (JwtException exception) {
            throw new AuthException(AuthErrorCode.APP_TOKEN_VERIFICATION_FAILED, exception);
        }

        // jjwt의 requireIssuer()는 값 하나만 정확히 일치해야 해서, 구글처럼 iss가
        // 두 가지 표기(https://accounts.google.com / accounts.google.com)로 올 수 있는
        // provider는 여기서 직접 허용 목록 포함 여부로 검증한다.
        if (!allowedIssuers.contains(claims.getIssuer())) {
            throw new AuthException(AuthErrorCode.APP_TOKEN_VERIFICATION_FAILED);
        }
        return claims;
    }

    private JwkSet getJwkSet(String jwksUrl) {
        CachedJwkSet cached = jwkSetCache.get(jwksUrl);
        if (cached != null && cached.isValid()) {
            return cached.jwkSet();
        }

        String json = restClient.get().uri(jwksUrl).retrieve().body(String.class);
        JwkSet jwkSet = Jwks.setParser().build().parse(json);
        // ponytail: 인스턴스 로컬 캐시라 서버가 여러 대면 각자 따로 캐싱한다.
        // 여러 인스턴스로 스케일할 때는 Redis 등 공유 캐시로 교체할 것.
        jwkSetCache.put(jwksUrl, new CachedJwkSet(jwkSet, Instant.now()));
        return jwkSet;
    }

    private record CachedJwkSet(JwkSet jwkSet, Instant fetchedAt) {
        boolean isValid() {
            return fetchedAt.plus(JWKS_CACHE_TTL).isAfter(Instant.now());
        }
    }
}
