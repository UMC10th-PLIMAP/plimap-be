package com.example.plimap.global.external.kakao;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import org.springframework.web.client.ResourceAccessException;

// 여러 Kakao 클라이언트(Local 검색/좌표/주소/유저 정보)가 각자 들고 있던
// "타임아웃이면 KakaoClientTimeoutException, 아니면 KakaoClientException" 변환 로직을 모은다.
final class KakaoClientExceptionTranslator {

    private KakaoClientExceptionTranslator() {
    }

    static KakaoClientException translate(String message, Exception exception) {
        if (exception instanceof ResourceAccessException resourceAccessException && isTimeout(resourceAccessException)) {
            return new KakaoClientTimeoutException(message, resourceAccessException);
        }
        return new KakaoClientException(message, exception);
    }

    private static boolean isTimeout(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SocketTimeoutException || current instanceof HttpTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
