package com.example.plimap.domain.member.controller.docs;

import static com.example.plimap.global.swagger.CommonSwaggerErrorExamples.JSON_MESSAGE_SEPARATOR;
import static com.example.plimap.global.swagger.CommonSwaggerErrorExamples.JSON_PREFIX;
import static com.example.plimap.global.swagger.CommonSwaggerErrorExamples.JSON_SUFFIX;

final class MemberSwaggerErrorExamples {

    static final String MEMBER_NOT_FOUND =
            JSON_PREFIX + "MEMBER_NOT_FOUND"
                    + JSON_MESSAGE_SEPARATOR + "존재하지 않는 사용자입니다." + JSON_SUFFIX;
    static final String CANNOT_VIEW_SELF_PROFILE =
            JSON_PREFIX + "MEMBER_CANNOT_VIEW_SELF_PROFILE"
                    + JSON_MESSAGE_SEPARATOR
                    + "본인 프로필은 내 프로필 조회 API를 이용해 주세요."
                    + JSON_SUFFIX;
    static final String NICKNAME_FORBIDDEN_WORD =
            JSON_PREFIX + "MEMBER_NICKNAME_FORBIDDEN_WORD"
                    + JSON_MESSAGE_SEPARATOR + "사용할 수 없는 닉네임입니다." + JSON_SUFFIX;
    static final String APPLE_REAUTH_REQUIRED =
            JSON_PREFIX + "AUTH_APPLE_REAUTH_REQUIRED"
                    + JSON_MESSAGE_SEPARATOR + "탈퇴하려면 Apple 로그인을 한 번 더 진행해 주세요." + JSON_SUFFIX;
    static final String GOOGLE_REAUTH_REQUIRED =
            JSON_PREFIX + "AUTH_GOOGLE_REAUTH_REQUIRED"
                    + JSON_MESSAGE_SEPARATOR + "탈퇴하려면 Google 로그인을 한 번 더 진행해 주세요." + JSON_SUFFIX;
    static final String SOCIAL_UNLINK_FAILED =
            JSON_PREFIX + "AUTH_SOCIAL_UNLINK_FAILED"
                    + JSON_MESSAGE_SEPARATOR + "소셜 로그인 연결 해제 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."
                    + JSON_SUFFIX;
    static final String NICKNAME_DUPLICATE =
            JSON_PREFIX + "MEMBER_NICKNAME_DUPLICATE"
                    + JSON_MESSAGE_SEPARATOR + "이미 사용 중인 닉네임입니다." + JSON_SUFFIX;
    static final String INVALID_PROFILE_IMAGE =
            JSON_PREFIX + "MEMBER_INVALID_PROFILE_IMAGE"
                    + JSON_MESSAGE_SEPARATOR + "유효하지 않은 프로필 이미지입니다." + JSON_SUFFIX;
    static final String PROFILE_IMAGE_UPLOAD_FAILED =
            JSON_PREFIX + "MEMBER_PROFILE_IMAGE_UPLOAD_FAILED"
                    + JSON_MESSAGE_SEPARATOR + "프로필 이미지 업로드에 실패했습니다." + JSON_SUFFIX;
    static final String PROFILE_IMAGE_NOT_FOUND =
            JSON_PREFIX + "MEMBER_PROFILE_IMAGE_NOT_FOUND"
                    + JSON_MESSAGE_SEPARATOR + "이미 프로필 이미지가 없습니다." + JSON_SUFFIX;
    static final String CANNOT_FOLLOW_SELF =
            JSON_PREFIX + "MEMBER_CANNOT_FOLLOW_SELF"
                    + JSON_MESSAGE_SEPARATOR + "자기 자신을 팔로우할 수 없습니다." + JSON_SUFFIX;
    static final String ALREADY_FOLLOWING =
            JSON_PREFIX + "MEMBER_ALREADY_FOLLOWING"
                    + JSON_MESSAGE_SEPARATOR + "이미 팔로우 중인 사용자입니다." + JSON_SUFFIX;
    static final String CANNOT_UNFOLLOW_SELF =
            JSON_PREFIX + "MEMBER_CANNOT_UNFOLLOW_SELF"
                    + JSON_MESSAGE_SEPARATOR + "자기 자신을 언팔로우할 수 없습니다." + JSON_SUFFIX;
    static final String NOT_FOLLOWING =
            JSON_PREFIX + "MEMBER_NOT_FOLLOWING"
                    + JSON_MESSAGE_SEPARATOR + "팔로우 중이 아닌 사용자입니다." + JSON_SUFFIX;

    private MemberSwaggerErrorExamples() {
    }
}
