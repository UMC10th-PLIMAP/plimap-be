package com.example.plimap.domain.auth.service.command;

import com.example.plimap.domain.auth.enums.AuthProvider;

public interface SocialAccountCommandService {

    void deleteByMemberId(Long memberId);

    // 앱 로그인: 애플 authorizationCode / 구글 serverAuthCode를 refresh token으로 교환해 저장한다.
    // 실패해도 로그인은 막지 않는다(탈퇴 시 재로그인 경로로 보완).
    void saveRefreshTokenByCode(Long memberId, AuthProvider provider, String authorizationCode);

    // 웹 구글 로그인: Spring OAuth2가 받아 온 refresh token을 그대로 저장한다.
    void saveRefreshToken(Long memberId, String refreshToken);

    // 자발적 탈퇴: provider 연결을 끊는다. 애플/구글은 끊지 못하면 재로그인 요청 예외를 던진다.
    // reauthCode가 있으면 저장된 토큰 대신 그 code로 새로 교환한 토큰을 끊는다.
    void unlink(Long memberId, String reauthCode);

    // 자동 탈퇴(벌점 누적): 끊기를 시도하되 실패해도 로그만 남긴다.
    void unlinkQuietly(Long memberId);
}
