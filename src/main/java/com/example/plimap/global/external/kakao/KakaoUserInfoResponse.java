package com.example.plimap.global.external.kakao;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KakaoUserInfoResponse(
        Long id,
        @JsonProperty("kakao_account")
        KakaoAccount kakaoAccount
) {
    public record KakaoAccount(
            String email,
            @JsonProperty("is_email_verified")
            Boolean isEmailVerified,
            Profile profile
    ) {
        public record Profile(
                String nickname
        ) {
        }
    }
}
