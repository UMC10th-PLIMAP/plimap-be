package com.example.plimap.domain.auth.dto.request;

import com.example.plimap.domain.auth.enums.AuthProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

public class AuthReqDTO {

    @Getter
    public static class TempToken {
        @NotNull
        @Schema(description = "토큰을 발급할 멤버 ID", example = "1")
        private Long memberId;
    }

    public record AppLogin(
            @NotNull
            @Schema(description = "소셜 로그인 제공자", example = "KAKAO")
            AuthProvider provider,

            @NotBlank
            @Schema(description = "앱이 provider SDK로 발급받은 토큰(카카오는 액세스 토큰, 구글/애플은 ID 토큰)")
            String token,

            @Schema(description = """
                    탈퇴 시 연결 끊기에 쓰는 1회용 code(선택). 애플은 authorizationCode, 구글은 serverAuthCode를 보냅니다.
                    카카오는 보내지 않습니다. 없거나 교환에 실패해도 로그인은 성공합니다.""")
            String authorizationCode
    ) {
    }

    public record AppReissue(
            @NotBlank
            @Schema(description = "앱 로그인 시 JSON 응답으로 받은 refreshToken")
            String refreshToken
    ) {
    }
}
