package com.example.plimap.domain.auth.dto.response;

import com.example.plimap.domain.auth.exception.SanctionedMemberAuthenticationException;
import com.example.plimap.domain.member.enums.MemberStatus;
import com.example.plimap.domain.member.enums.SuspensionPeriod;
import com.example.plimap.domain.report.enums.ReportCategory;
import java.time.Instant;

public final class AuthResponse {

    private AuthResponse() {
    }

    public record CsrfToken(String token) {
    }

    // 정지/자동탈퇴 회원은 리다이렉트가 없는 앱 흐름 특성상 토큰 없이 제재 정보만 담아 반환한다.
    // (웹 로그인의 OAuthFailureHandler가 리다이렉트 쿼리스트링으로 전달하는 것과 동일한 정보)
    public record AppLogin(
            String accessToken,
            String refreshToken,
            boolean isNewUser,
            MemberStatus status,
            ReportCategory reasonCategory,
            String reasonDetail,
            Instant suspendedUntil,
            SuspensionPeriod lastPenaltyPeriod,
            int penaltyPoint
    ) {
        public static AppLogin of(String accessToken, String refreshToken, boolean isNewUser) {
            return new AppLogin(
                    accessToken, refreshToken, isNewUser,
                    MemberStatus.ACTIVE, null, null, null, null, 0
            );
        }

        public static AppLogin sanctioned(SanctionedMemberAuthenticationException exception) {
            return new AppLogin(
                    null, null, false,
                    exception.getStatus(),
                    exception.getReasonCategory(),
                    exception.getReasonDetail(),
                    exception.getSuspendedUntil(),
                    exception.getLastPenaltyPeriod(),
                    exception.getPenaltyPoint()
            );
        }
    }

    public record AppTokenReissue(String accessToken, String refreshToken) {
    }

    public record AppLoginNonce(String nonce) {
    }
}
