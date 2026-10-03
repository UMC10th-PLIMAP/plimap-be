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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

// 구글/애플 ID 토큰(JWT) 서명을 각 provider의 JWKS로 직접 검증한다.
// nimbus 등 신규 라이브러리 없이, 이미 있는 jjwt(Jwks 파서)만으로 처리한다.
@Component
class IdTokenVerifier {

    private static final Duration JWKS_CACHE_TTL = Duration.ofHours(1);
    private static final Duration MIN_FORCED_REFRESH_INTERVAL = Duration.ofSeconds(60);

    private final RestClient jwksRestClient;
    private final ConcurrentHashMap<String, CachedKeys> keysCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> lastForcedRefreshAt = new ConcurrentHashMap<>();

    IdTokenVerifier(RestClient jwksRestClient) {
        this.jwksRestClient = jwksRestClient;
    }

    Claims verify(String jwksUrl, Set<String> allowedIssuers, String audience, String idToken) {
        Locator<Key> keyLocator = new LocatorAdapter<>() {
            @Override
            protected Key locate(ProtectedHeader header) {
                return locateKey(jwksUrl, header.getKeyId());
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

    private Key locateKey(String jwksUrl, String kid) {
        if (kid == null) {
            throw new AuthException(AuthErrorCode.APP_TOKEN_VERIFICATION_FAILED);
        }
        Key key = getKeys(jwksUrl, false).get(kid);
        if (key != null) {
            return key;
        }
        // 캐시에 없는 kid는 provider가 서명 키를 교체했을 수 있다는 뜻이라, TTL 만료를
        // 기다리지 않고 한 번 강제로 다시 조회해본다(남용 방지를 위해 강제 재조회 자체에도
        // 최소 간격을 둔다 - tryClaimForcedRefresh 참고).
        key = getKeys(jwksUrl, true).get(kid);
        if (key != null) {
            return key;
        }
        throw new AuthException(AuthErrorCode.APP_TOKEN_VERIFICATION_FAILED);
    }

    private Map<String, Key> getKeys(String jwksUrl, boolean forceRefresh) {
        CachedKeys cached = keysCache.get(jwksUrl);
        if (!forceRefresh && cached != null && cached.isValid()) {
            return cached.keysByKid();
        }

        if (forceRefresh && !tryClaimForcedRefresh(jwksUrl) && cached != null) {
            // 알 수 없는 kid로 강제 재조회를 남용하는 걸 막기 위해, 최근에 이미 다른 요청이
            // 강제 재조회를 했다면 새로 fetch하지 않고 있는 캐시(조금 오래됐어도)를 그대로 쓴다.
            return cached.keysByKid();
        }

        Map<String, Key> keys = fetchKeys(jwksUrl);
        // ponytail: 인스턴스 로컬 캐시라 서버가 여러 대면 각자 따로 캐싱한다.
        // 여러 인스턴스로 스케일할 때는 Redis 등 공유 캐시로 교체할 것.
        CachedKeys fresh = new CachedKeys(keys, Instant.now());
        keysCache.put(jwksUrl, fresh);
        return fresh.keysByKid();
    }

    // lastForcedRefreshAt의 "쿨다운 확인 후 갱신"을 merge()로 원자적으로 처리한다. get()과
    // put()을 따로 하면 동시에 들어온 여러 요청이 전부 "쿨다운 아님"을 보고 나서야 각자
    // 타임스탬프를 쓰게 되는 TOCTOU 경쟁이 생겨, 키 로테이션 직후 몰리는 요청마다 강제
    // 재조회가 중복 실행될 수 있다. merge()는 키 단위로 원자적이라 이 경쟁을 없앤다.
    private boolean tryClaimForcedRefresh(String jwksUrl) {
        Instant now = Instant.now();
        Instant recorded = lastForcedRefreshAt.merge(jwksUrl, now, (previous, candidate) ->
                previous.plus(MIN_FORCED_REFRESH_INTERVAL).isAfter(candidate) ? previous : candidate);
        return recorded.equals(now);
    }

    private Map<String, Key> fetchKeys(String jwksUrl) {
        try {
            String json = jwksRestClient.get().uri(jwksUrl).retrieve().body(String.class);
            JwkSet jwkSet = Jwks.setParser().build().parse(json);
            // kid -> Key 변환(RSA 키 생성 등 실제 암호 연산)은 JWKS를 새로 받아올 때 한 번만
            // 하고 캐시해둔다. verify()마다 매번 다시 만들면 로그인 요청마다 불필요한 CPU
            // 연산이 반복된다.
            Map<String, Key> keys = new HashMap<>();
            for (Jwk<?> jwk : jwkSet.getKeys()) {
                if (jwk.get("kid") instanceof String kid) {
                    keys.put(kid, jwk.toKey());
                }
            }
            return Map.copyOf(keys);
        } catch (RestClientException | JwtException exception) {
            // JWKS 조회/파싱 실패를 그대로 흘려보내면 GlobalExceptionHandler의 일반 예외
            // 처리로 떨어져 500이 나간다. "앱이 보낸 토큰을 검증하지 못했다"는 동일한 의미로
            // 묶어서 문서화된 401(AUTH_APP_TOKEN_VERIFICATION_FAILED)로 변환한다.
            throw new AuthException(AuthErrorCode.APP_TOKEN_VERIFICATION_FAILED, exception);
        }
    }

    private record CachedKeys(Map<String, Key> keysByKid, Instant fetchedAt) {
        boolean isValid() {
            return fetchedAt.plus(JWKS_CACHE_TTL).isAfter(Instant.now());
        }
    }
}
