package com.example.plimap.domain.auth.dto;

import com.example.plimap.domain.auth.enums.AuthProvider;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AppleDTO implements OAuthDTO {

    private final String providerSubject;
    private final String email;

    @Override
    public AuthProvider getProvider() {
        return AuthProvider.APPLE;
    }

    @Override
    public String getProviderSubject() {
        return providerSubject;
    }

    @Override
    public String getEmail() {
        return email;
    }

    @Override
    public String getNickname() {
        // Apple 로그인은 식별 토큰에 닉네임을 포함하지 않는다(앱이 별도 폼으로 입력받아 전달해도
        // member.nickname은 온보딩에서만 채운다는 기존 정책과 동일하게 여기서도 무시한다).
        return null;
    }
}
