package com.example.plimap.global.external.kakao;

public interface KakaoUserApiClient {

    KakaoUserInfoResponse getUserInfo(String accessToken);

    // 사용자 토큰 없이 어드민 키와 회원번호(user_id)로 앱 연결을 끊는다.
    void unlink(String userId);
}
