package com.example.plimap.global.security;

import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

// 앱 로그인(특히 Apple Sign In)에서 ID 토큰 재전송(replay) 공격을 막기 위한 1회용 nonce.
// 클라이언트가 로그인 시도 전에 서버에서 nonce를 발급받아 provider 인증 요청에 그대로 실어 보내면,
// provider가 발급하는 ID 토큰의 nonce claim에 그대로 담겨 돌아온다. 로그인 검증 시 이 값을
// 소비(1회만 성공)해서, 같은 ID 토큰을 다시 보내도 두 번째부터는 거부되게 한다.
@Component
@RequiredArgsConstructor
public class AppLoginNonceService {

    private static final String KEY_PREFIX = "app-login:nonce:";
    private static final Duration NONCE_TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redisTemplate;

    public String issue() {
        String nonce = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(KEY_PREFIX + nonce, "1", NONCE_TTL);
        return nonce;
    }

    // 원자적으로 1회만 소비한다(Redis DEL은 단일 커맨드라 동시 요청이 와도 하나만 성공한다).
    public boolean consume(String nonce) {
        if (nonce == null || nonce.isBlank()) {
            return false;
        }
        return Boolean.TRUE.equals(redisTemplate.delete(KEY_PREFIX + nonce));
    }
}
