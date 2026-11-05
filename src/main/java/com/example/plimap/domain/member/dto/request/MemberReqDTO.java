package com.example.plimap.domain.member.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;

public class MemberReqDTO {

    @Getter
    public static class Onboarding {
        @NotBlank
        @Size(min = 2, max = 10)
        @Pattern(regexp = "^[가-힣A-Za-z0-9]+$")
        @Schema(description = "닉네임 (2~10자, 한글/영문/숫자)", example = "플리맵")
        private String nickname;
    }

    public record UpdateProfile(
            @Size(min = 2, max = 10)
            @Pattern(regexp = "^[가-힣A-Za-z0-9]+$")
            @Schema(description = "닉네임 (2~10자, 한글/영문/숫자). 값을 보내지 않으면 변경되지 않습니다.", example = "플리맵")
            String nickname,

            @Pattern(regexp = "^$|^[가-힣A-Za-z0-9]{2,7}$")
            @Schema(description = "이름 (2~7자, 한글/영문/숫자). 값을 보내지 않거나 null이면 변경되지 않고, 빈 문자열을 보내면 삭제됩니다.", example = "이예림")
            String name,

            @Size(max = 100)
            @Schema(description = "소개. 값을 보내지 않으면 변경되지 않습니다.", example = "플리맵 개발 중입니다.")
            String introduction
    ) {
    }

    public record Withdraw(
            @Schema(description = """
                    탈퇴 응답이 AUTH_APPLE_REAUTH_REQUIRED / AUTH_GOOGLE_REAUTH_REQUIRED였을 때만 보냅니다.
                    해당 provider로 다시 로그인해 받은 애플 authorizationCode 또는 구글 serverAuthCode입니다.""")
            String authorizationCode
    ) {
    }
}
