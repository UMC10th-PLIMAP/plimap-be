package com.example.plimap.global.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

// 앱 로그인(특히 Apple Sign In)에서 ID 토큰 재전송(replay) 공격을 막기 위한 1회용 nonce.
// 클라이언트가 로그인 시도 전에 서버에서 nonce를 발급받아 provider 인증 요청에 실어 보내면,
// provider가 발급하는 ID 토큰의 nonce claim에 담겨 돌아온다. 로그인 검증 시 이 값을
// 소비(1회만 성공)해서, 같은 ID 토큰을 다시 보내도 두 번째부터는 거부되게 한다.
@Component
@RequiredArgsConstructor
public class AppLoginNonceService {

    private static final String KEY_PREFIX = "app-login:nonce:";
    private static final Duration NONCE_TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redisTemplate;

    // 앱의 애플 로그인 라이브러리(@invertase/react-native-apple-authentication)가 nonce를
    // SHA-256(소문자 hex)으로 해시해서 애플에 보내므로, ID 토큰의 nonce claim에는 해시 값이 담겨 온다.
    // 앱에는 원래 nonce를 반환하고, consume()이 claim 값으로 바로 찾을 수 있게 해시 값을 키로 저장한다.
    public String issue() {
        String nonce = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(KEY_PREFIX + sha256Hex(nonce), "1", NONCE_TTL);
        return nonce;
    }

    // 원자적으로 1회만 소비한다(Redis DEL은 단일 커맨드라 동시 요청이 와도 하나만 성공한다).
    public boolean consume(String nonce) {
        if (nonce == null || nonce.isBlank()) {
            return false;
        }
        return Boolean.TRUE.equals(redisTemplate.delete(KEY_PREFIX + nonce));
    }

    private static String sha256Hex(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
