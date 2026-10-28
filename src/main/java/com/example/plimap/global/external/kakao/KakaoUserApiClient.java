package com.example.plimap.global.external.kakao;

public interface KakaoUserApiClient {

    KakaoUserInfoResponse getUserInfo(String accessToken);
}
