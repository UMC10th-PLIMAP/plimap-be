package com.example.plimap.domain.member.controller.docs;

import com.example.plimap.domain.auth.entity.AuthMember;
import com.example.plimap.domain.member.dto.Pagination;
import com.example.plimap.domain.member.dto.request.MemberReqDTO;
import com.example.plimap.domain.member.dto.response.MemberResponse;
import com.example.plimap.global.apiPayload.ApiResponse;
import com.example.plimap.global.swagger.CommonSwaggerErrorExamples;
import com.example.plimap.global.swagger.ErrorApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Member", description = "회원 API")
public interface MemberControllerDocs {

    @Operation(
            summary = "닉네임 중복 확인",
            description = """
                    온보딩/프로필 수정 전에 닉네임 사용 가능 여부를 확인합니다.

                    `available`이 false이면 `reason`으로 실패 사유를 알려줍니다.
                    - TOO_SHORT: 두 글자 이상 입력해 주세요.
                    - TOO_LONG: 최대 10자까지만 입력할 수 있어요.
                    - INVALID_FORMAT: 한글, 영문, 숫자만 사용 가능하며 공백은 포함할 수 없어요.
                    - FORBIDDEN_WORD: 부적절하거나 사용할 수 없는 단어가 포함되어 있어요.
                    - DUPLICATE: 이미 사용 중인 닉네임이에요.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    content = @Content(examples = {
                    @ExampleObject(name = "사용 가능", value = """
                            {
                              "isSuccess": true,
                              "code": "MEMBER_NICKNAME_CHECKED_SUCCESS",
                              "message": "닉네임 사용 가능 여부를 조회했습니다.",
                              "result": { "nickname": "예림", "available": true, "reason": null }
                            }
                            """),
                    @ExampleObject(name = "이미 사용 중", value = """
                            {
                              "isSuccess": true,
                              "code": "MEMBER_NICKNAME_CHECKED_SUCCESS",
                              "message": "닉네임 사용 가능 여부를 조회했습니다.",
                              "result": { "nickname": "예림", "available": false, "reason": "DUPLICATE" }
                            }
                            """)
                    })),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "필수 닉네임 파라미터가 누락된 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = @ExampleObject(
                                    name = "COMMON_400_MISSING_PARAMETER",
                                    summary = "닉네임 파라미터 누락",
                                    value = CommonSwaggerErrorExamples.MISSING_PARAMETER
                            )
                    )),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 정보가 없거나 유효하지 않은 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = @ExampleObject(
                                    name = "COMMON_401_UNAUTHORIZED",
                                    summary = "인증 필요",
                                    value = CommonSwaggerErrorExamples.UNAUTHORIZED
                            )
                    ))
    })
    ApiResponse<MemberResponse.NicknameCheck> checkNickname(String nickname);

    @Operation(
            summary = "내 프로필 조회",
            description = """
                    로그인한 회원 자신의 프로필 정보를 조회합니다. 팔로워/팔로잉 수, 내가 작성한 핀 개수(pinCount)를 함께 반환합니다. pinCount는 삭제되지 않고 피드에 공개된 핀만 집계한 값입니다.

                    정지(SUSPENDED)·탈퇴(WITHDRAWN) 회원도 조회할 수 있으며, 이 경우 status/suspendedUntil/withdrawalReason/reasonCategory/reasonDetail로 제재 상태·해제일·사유를 함께 확인할 수 있습니다. 제재 이력이 전혀 없는 ACTIVE 회원은 suspendedUntil/withdrawalReason/reasonCategory/reasonDetail이 모두 null입니다.

                    penaltyPoint는 누적 벌점(절대 리셋되지 않음)이고, lastPenaltyPeriod는 가장 최근 제재 시 적용된 기간(ONE_DAY/THREE_DAYS/FIVE_DAYS/PERMANENT)입니다. 한 번도 제재받은 적 없는 회원은 penaltyPoint가 0, lastPenaltyPeriod가 null입니다. 정지가 해제(liftSuspension)되어 ACTIVE로 복귀한 회원은 suspendedUntil만 null로 초기화될 뿐, penaltyPoint/lastPenaltyPeriod/reasonCategory/reasonDetail은 마지막 제재 이력이 그대로 남아있습니다.
                    """
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "로그인한 회원을 찾을 수 없는 경우",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ErrorApiResponse.class),
                    examples = @ExampleObject(
                            name = "MEMBER_NOT_FOUND",
                            summary = "회원 없음",
                            value = MemberSwaggerErrorExamples.MEMBER_NOT_FOUND
                    )
            ))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "인증 정보가 없거나 유효하지 않은 경우",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ErrorApiResponse.class),
                    examples = @ExampleObject(
                            name = "COMMON_401_UNAUTHORIZED",
                            summary = "인증 필요",
                            value = CommonSwaggerErrorExamples.UNAUTHORIZED
                    )
            ))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "요청 성공")
    ApiResponse<MemberResponse.MyProfile> getMyProfile(AuthMember authMember);

    @Operation(
            summary = "다른 사용자 프로필 조회",
            description = """
                    경로의 memberId에 해당하는 회원의 프로필을 조회합니다. 팔로워/팔로잉 수, 로그인한 나(요청자, 뷰어) 기준의 양방향 팔로우 정보, 이 회원이 작성한 핀 개수(pinCount)를 함께 반환합니다. pinCount는 삭제되지 않고 피드에 공개된 핀만 집계한 값입니다. 본인의 memberId로는 조회할 수 없으며, 내 프로필 조회는 `GET /api/v1/members/me`를 이용해야 합니다.

                    - isFollowing: 내가 이 회원을 팔로우하고 있는지
                    - isFollowingViewer: 이 회원이 나를 팔로우하고 있는지

                    클라이언트에서 버튼 상태를 표시할 때는 다음 우선순위로 판단합니다.
                    - isFollowing=true: "팔로잉" (이미 내가 팔로우 중)
                    - isFollowing=false, isFollowingViewer=true: "맞팔로우" (상대가 나를 팔로우 중이므로 팔로우하면 맞팔이 됨)
                    - 둘 다 false: "팔로우" (아무 관계 없음)
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "본인 ID로 다른 사용자 프로필 조회를 요청한 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = @ExampleObject(
                                    name = "MEMBER_CANNOT_VIEW_SELF_PROFILE",
                                    summary = "본인 프로필 조회 API 오사용",
                                    value = MemberSwaggerErrorExamples.CANNOT_VIEW_SELF_PROFILE
                            )
                    )),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "조회할 수 있는 활성 회원을 찾을 수 없는 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = @ExampleObject(
                                    name = "MEMBER_NOT_FOUND",
                                    summary = "회원 없음",
                                    value = MemberSwaggerErrorExamples.MEMBER_NOT_FOUND
                            )
                    )),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 정보가 없거나 유효하지 않은 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = @ExampleObject(
                                    name = "COMMON_401_UNAUTHORIZED",
                                    summary = "인증 필요",
                                    value = CommonSwaggerErrorExamples.UNAUTHORIZED
                            )
                    )),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "요청 성공")
    })
    ApiResponse<MemberResponse.OtherProfile> getOtherProfile(AuthMember authMember, Long memberId);

    @Operation(
            summary = "내 프로필 수정",
            description = "닉네임, 이름, 소개를 수정합니다. 요청에 포함하지 않거나 null을 보낸 필드는 변경되지 않습니다. 이름은 빈 문자열(\"\")을 보내면 삭제됩니다. 프로필 이미지는 `POST /api/v1/members/me/profile-image`를 이용해 주세요."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "요청 본문 검증에 실패했거나 사용할 수 없는 닉네임인 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "COMMON_400_VALIDATION_FAILED",
                                            summary = "요청 값 검증 실패",
                                            value = CommonSwaggerErrorExamples.VALIDATION_FAILED
                                    ),
                                    @ExampleObject(
                                            name = "COMMON_400_MALFORMED_JSON",
                                            summary = "잘못된 JSON 본문",
                                            value = CommonSwaggerErrorExamples.MALFORMED_JSON
                                    ),
                                    @ExampleObject(
                                            name = "MEMBER_NICKNAME_FORBIDDEN_WORD",
                                            summary = "사용 불가 닉네임",
                                            value = MemberSwaggerErrorExamples.NICKNAME_FORBIDDEN_WORD
                                    )
                            }
                    )),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "로그인한 회원을 찾을 수 없는 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = @ExampleObject(
                                    name = "MEMBER_NOT_FOUND",
                                    summary = "회원 없음",
                                    value = MemberSwaggerErrorExamples.MEMBER_NOT_FOUND
                            )
                    )),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "변경할 닉네임이 이미 사용 중인 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = @ExampleObject(
                                    name = "MEMBER_NICKNAME_DUPLICATE",
                                    summary = "닉네임 중복",
                                    value = MemberSwaggerErrorExamples.NICKNAME_DUPLICATE
                            )
                    )),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 정보가 없거나 유효하지 않은 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = @ExampleObject(
                                    name = "COMMON_401_UNAUTHORIZED",
                                    summary = "인증 필요",
                                    value = CommonSwaggerErrorExamples.UNAUTHORIZED
                            )
                    )),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "요청 성공")
    })
    ApiResponse<MemberResponse.Profile> updateProfile(AuthMember authMember, @Valid MemberReqDTO.UpdateProfile request);

    @Operation(
            summary = "프로필 이미지 업로드",
            description = """
                    로그인한 회원 자신의 프로필 이미지를 업로드합니다. multipart/form-data의 `image` 파트로 WebP 이미지 파일을 전달해 주세요.

                    - 이미지는 클라이언트에서 WebP로 인코딩해 전달해야 합니다(서버는 별도로 포맷을 변환하지 않습니다).
                    - 파일 크기는 5MB를 초과할 수 없습니다.
                    - 이미 프로필 이미지가 있던 회원이 다시 업로드하면 기존 이미지는 새 이미지로 교체되고, 이전 이미지는 스토리지에서 삭제됩니다.
                    - 응답의 imageUrl로 즉시 접근 가능한 공개 URL을 반환합니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "이미지 파트가 없거나 WebP 이미지가 유효하지 않은 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "COMMON_400_MISSING_PARAMETER",
                                            summary = "이미지 파트 누락",
                                            value = CommonSwaggerErrorExamples.MISSING_PARAMETER
                                    ),
                                    @ExampleObject(
                                            name = "MEMBER_INVALID_PROFILE_IMAGE",
                                            summary = "유효하지 않은 프로필 이미지",
                                            value = MemberSwaggerErrorExamples.INVALID_PROFILE_IMAGE
                                    )
                            }
                    )),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "로그인한 회원을 찾을 수 없는 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = @ExampleObject(
                                    name = "MEMBER_NOT_FOUND",
                                    summary = "회원 없음",
                                    value = MemberSwaggerErrorExamples.MEMBER_NOT_FOUND
                            )
                    )),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "413",
                    description = "이미지 파일이 허용된 크기를 초과한 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = @ExampleObject(
                                    name = "COMMON_413_CONTENT_TOO_LARGE",
                                    summary = "파일 크기 초과",
                                    value = CommonSwaggerErrorExamples.CONTENT_TOO_LARGE
                            )
                    )),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "502",
                    description = "프로필 이미지 스토리지 업로드에 실패한 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = @ExampleObject(
                                    name = "MEMBER_PROFILE_IMAGE_UPLOAD_FAILED",
                                    summary = "이미지 스토리지 업로드 실패",
                                    value = MemberSwaggerErrorExamples.PROFILE_IMAGE_UPLOAD_FAILED
                            )
                    )),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 정보가 없거나 유효하지 않은 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = @ExampleObject(
                                    name = "COMMON_401_UNAUTHORIZED",
                                    summary = "인증 필요",
                                    value = CommonSwaggerErrorExamples.UNAUTHORIZED
                            )
                    )),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "요청 성공")
    })
    ApiResponse<MemberResponse.ProfileImage> uploadProfileImage(AuthMember authMember, MultipartFile image);

    @Operation(
            summary = "프로필 이미지 제거",
            description = """
                    로그인한 회원 자신의 프로필 이미지를 제거하고 기본(없음) 상태로 되돌립니다.

                    - 이미 프로필 이미지가 없는 상태에서 호출하면 404로 실패합니다.
                    """
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "회원 또는 삭제할 프로필 이미지를 찾을 수 없는 경우",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ErrorApiResponse.class),
                    examples = {
                            @ExampleObject(
                                    name = "MEMBER_NOT_FOUND",
                                    summary = "회원 없음",
                                    value = MemberSwaggerErrorExamples.MEMBER_NOT_FOUND
                            ),
                            @ExampleObject(
                                    name = "MEMBER_PROFILE_IMAGE_NOT_FOUND",
                                    summary = "삭제할 프로필 이미지 없음",
                                    value = MemberSwaggerErrorExamples.PROFILE_IMAGE_NOT_FOUND
                            )
                    }
            ))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "인증 정보가 없거나 유효하지 않은 경우",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ErrorApiResponse.class),
                    examples = @ExampleObject(
                            name = "COMMON_401_UNAUTHORIZED",
                            summary = "인증 필요",
                            value = CommonSwaggerErrorExamples.UNAUTHORIZED
                    )
            ))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "요청 성공")
    ApiResponse<Void> removeProfileImage(AuthMember authMember);

    @Operation(
            summary = "팔로우",
            description = "경로의 memberId에 해당하는 회원을 팔로우합니다. 자기 자신은 팔로우할 수 없고, 이미 팔로우 중이면 실패합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "자기 자신을 팔로우하려는 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = @ExampleObject(
                                    name = "MEMBER_CANNOT_FOLLOW_SELF",
                                    summary = "자기 자신 팔로우",
                                    value = MemberSwaggerErrorExamples.CANNOT_FOLLOW_SELF
                            )
                    )),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "팔로우 대상 회원을 찾을 수 없는 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = @ExampleObject(
                                    name = "MEMBER_NOT_FOUND",
                                    summary = "회원 없음",
                                    value = MemberSwaggerErrorExamples.MEMBER_NOT_FOUND
                            )
                    )),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "이미 팔로우 중인 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = @ExampleObject(
                                    name = "MEMBER_ALREADY_FOLLOWING",
                                    summary = "이미 팔로우 중",
                                    value = MemberSwaggerErrorExamples.ALREADY_FOLLOWING
                            )
                    )),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 정보가 없거나 유효하지 않은 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = @ExampleObject(
                                    name = "COMMON_401_UNAUTHORIZED",
                                    summary = "인증 필요",
                                    value = CommonSwaggerErrorExamples.UNAUTHORIZED
                            )
                    )),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "요청 성공")
    })
    ApiResponse<Void> follow(AuthMember authMember, Long memberId);

    @Operation(
            summary = "언팔로우",
            description = "경로의 memberId에 해당하는 회원을 언팔로우합니다. 자기 자신은 언팔로우할 수 없고, 팔로우 중이 아니면 실패합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "자기 자신을 언팔로우하려는 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = @ExampleObject(
                                    name = "MEMBER_CANNOT_UNFOLLOW_SELF",
                                    summary = "자기 자신 언팔로우",
                                    value = MemberSwaggerErrorExamples.CANNOT_UNFOLLOW_SELF
                            )
                    )),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "팔로우 관계를 찾을 수 없는 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = @ExampleObject(
                                    name = "MEMBER_NOT_FOLLOWING",
                                    summary = "팔로우 관계 없음",
                                    value = MemberSwaggerErrorExamples.NOT_FOLLOWING
                            )
                    )),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 정보가 없거나 유효하지 않은 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = @ExampleObject(
                                    name = "COMMON_401_UNAUTHORIZED",
                                    summary = "인증 필요",
                                    value = CommonSwaggerErrorExamples.UNAUTHORIZED
                            )
                    )),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "요청 성공")
    })
    ApiResponse<Void> unfollow(AuthMember authMember, Long memberId);

    @Operation(
            summary = "팔로워 목록 조회",
            description = """
                    경로의 memberId에 해당하는 회원을 팔로우하는 회원 목록을 최신순으로 조회합니다.

                    커서 기반 페이지네이션을 사용합니다. 첫 페이지는 cursor 없이 요청하고, 이후에는 응답의 nextCursor를 그대로 다음 요청의 cursor로 전달합니다. pageSize는 1~50 사이여야 하며 기본값은 10입니다.

                    각 항목은 목록 대상(memberId)이 아니라 로그인한 나(요청자, 뷰어) 기준의 양방향 팔로우 정보를 담습니다. memberId 본인의 목록을 조회하는 경우가 아니라면(제3자가 다른 회원의 목록을 조회하는 경우) 두 값 모두 의미가 있습니다.
                    - isFollowing: 내가 이 사람을 팔로우하고 있는지
                    - isFollowingViewer: 이 사람이 나를 팔로우하고 있는지

                    클라이언트에서 버튼 상태를 표시할 때는 다음 우선순위로 판단합니다.
                    - isFollowing=true: "팔로잉" (이미 내가 팔로우 중)
                    - isFollowing=false, isFollowingViewer=true: "맞팔로우" (상대가 나를 팔로우 중이므로 팔로우하면 맞팔이 됨)
                    - 둘 다 false: "팔로우" (아무 관계 없음)
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "페이지 크기 검증에 실패했거나 커서가 올바르지 않은 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "COMMON_400_VALIDATION_FAILED",
                                            summary = "페이지 크기 검증 실패",
                                            value = CommonSwaggerErrorExamples.VALIDATION_FAILED
                                    ),
                                    @ExampleObject(
                                            name = "COMMON_400_INVALID_CURSOR",
                                            summary = "잘못된 커서",
                                            value = CommonSwaggerErrorExamples.INVALID_CURSOR
                                    )
                            }
                    )),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "목록 대상 회원을 찾을 수 없는 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = @ExampleObject(
                                    name = "MEMBER_NOT_FOUND",
                                    summary = "회원 없음",
                                    value = MemberSwaggerErrorExamples.MEMBER_NOT_FOUND
                            )
                    )),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 정보가 없거나 유효하지 않은 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = @ExampleObject(
                                    name = "COMMON_401_UNAUTHORIZED",
                                    summary = "인증 필요",
                                    value = CommonSwaggerErrorExamples.UNAUTHORIZED
                            )
                    )),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "요청 성공")
    })
    ApiResponse<Pagination<MemberResponse.FollowerItem>> getFollowers(
            AuthMember authMember,
            Long memberId,
            @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
            @Max(value = 50, message = "페이지 크기는 50 이하여야 합니다.")
            Integer pageSize,
            String cursor
    );

    @Operation(
            summary = "팔로잉 목록 조회",
            description = """
                    경로의 memberId에 해당하는 회원이 팔로우하는 회원 목록을 최신순으로 조회합니다.

                    커서 기반 페이지네이션을 사용합니다. 첫 페이지는 cursor 없이 요청하고, 이후에는 응답의 nextCursor를 그대로 다음 요청의 cursor로 전달합니다. pageSize는 1~50 사이여야 하며 기본값은 10입니다.

                    각 항목은 목록 대상(memberId)이 아니라 로그인한 나(요청자, 뷰어) 기준의 양방향 팔로우 정보를 담습니다. memberId 본인의 목록을 조회하는 경우가 아니라면(제3자가 다른 회원의 목록을 조회하는 경우) 두 값 모두 의미가 있습니다.
                    - isFollowing: 내가 이 사람을 팔로우하고 있는지 (memberId 본인의 팔로잉 목록을 조회하는 경우 항상 true입니다)
                    - isFollowingViewer: 이 사람이 나를 팔로우하고 있는지

                    클라이언트에서 버튼 상태를 표시할 때는 다음 우선순위로 판단합니다.
                    - isFollowing=true: "팔로잉" (이미 내가 팔로우 중)
                    - isFollowing=false, isFollowingViewer=true: "맞팔로우" (상대가 나를 팔로우 중이므로 팔로우하면 맞팔이 됨)
                    - 둘 다 false: "팔로우" (아무 관계 없음)
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "페이지 크기 검증에 실패했거나 커서가 올바르지 않은 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "COMMON_400_VALIDATION_FAILED",
                                            summary = "페이지 크기 검증 실패",
                                            value = CommonSwaggerErrorExamples.VALIDATION_FAILED
                                    ),
                                    @ExampleObject(
                                            name = "COMMON_400_INVALID_CURSOR",
                                            summary = "잘못된 커서",
                                            value = CommonSwaggerErrorExamples.INVALID_CURSOR
                                    )
                            }
                    )),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "목록 대상 회원을 찾을 수 없는 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = @ExampleObject(
                                    name = "MEMBER_NOT_FOUND",
                                    summary = "회원 없음",
                                    value = MemberSwaggerErrorExamples.MEMBER_NOT_FOUND
                            )
                    )),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 정보가 없거나 유효하지 않은 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = @ExampleObject(
                                    name = "COMMON_401_UNAUTHORIZED",
                                    summary = "인증 필요",
                                    value = CommonSwaggerErrorExamples.UNAUTHORIZED
                            )
                    )),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "요청 성공")
    })
    ApiResponse<Pagination<MemberResponse.FollowingItem>> getFollowing(
            AuthMember authMember,
            Long memberId,
            @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
            @Max(value = 50, message = "페이지 크기는 50 이하여야 합니다.")
            Integer pageSize,
            String cursor
    );

    @Operation(
            summary = "친구 찾기 검색",
            description = """
                    닉네임 또는 이름에 keyword가 포함된 활성 회원을 검색합니다. 검색 결과는 다음 우선순위로 정렬됩니다.

                    1. 팔로우 여부(1순위): 내(요청자)가 아직 팔로우하지 않은 회원이 이미 팔로우 중인 회원(맞팔 포함)보다 항상 위에 노출됩니다.
                    2. 닉네임 일치도(2순위): 닉네임이 keyword로 시작하면 가장 높은 우선순위, keyword를 포함만 하면 그다음, 둘 다 아니면 가장 낮은 우선순위입니다. 닉네임 일치도는 이름 일치도보다 항상 먼저 비교됩니다(닉네임 점수가 둘 다 0이어도 마찬가지입니다).
                    3. 이름 일치도(3순위, 닉네임 일치도가 같을 때만 비교): 닉네임과 동일한 기준(시작 > 포함 > 불일치)으로 비교합니다. 이름이 없는 회원은 이름 불일치와 동일하게 취급되어, 이름이 없다는 이유만으로 이름이 있는 회원보다 밀리지 않습니다.
                    4. 그 외에는 가입일 최신순으로 정렬하며, 가입일까지 같으면 회원 ID 내림차순으로 정렬합니다.

                    keyword를 비우거나 공백만 입력하면 닉네임/이름 일치 여부를 따지지 않고 전체 활성 회원 목록을 위 1·4번 기준으로만 정렬해 반환합니다. keyword를 입력했는데 닉네임과 이름 모두 일치하지 않는 회원은 결과에서 제외됩니다.

                    로그인한 나(요청자) 자신은 검색 결과에서 제외됩니다. 정지/탈퇴 회원, 신고 누적 회원, 내가 신고한 회원도 제외됩니다.

                    커서 기반 페이지네이션을 사용합니다. 첫 페이지는 cursor 없이 요청하고, 이후에는 응답의 nextCursor를 그대로 다음 요청의 cursor로 전달합니다. pageSize는 1~50 사이여야 하며 기본값은 10입니다.

                    isFollowing은 내가 이 회원을 팔로우하고 있는지, isFollowingViewer는 이 회원이 나를 팔로우하고 있는지를 나타냅니다.
                    """
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "페이지 크기 검증에 실패했거나 커서가 올바르지 않은 경우",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ErrorApiResponse.class),
                    examples = {
                            @ExampleObject(
                                    name = "COMMON_400_VALIDATION_FAILED",
                                    summary = "페이지 크기 검증 실패",
                                    value = CommonSwaggerErrorExamples.VALIDATION_FAILED
                            ),
                            @ExampleObject(
                                    name = "COMMON_400_INVALID_CURSOR",
                                    summary = "잘못된 커서",
                                    value = CommonSwaggerErrorExamples.INVALID_CURSOR
                            )
                    }
            ))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "인증 정보가 없거나 유효하지 않은 경우",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ErrorApiResponse.class),
                    examples = @ExampleObject(
                            name = "COMMON_401_UNAUTHORIZED",
                            summary = "인증 필요",
                            value = CommonSwaggerErrorExamples.UNAUTHORIZED
                    )
            ))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "요청 성공")
    ApiResponse<Pagination<MemberResponse.SearchItem>> searchMembers(
            AuthMember authMember,
            String keyword,
            @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
            @Max(value = 50, message = "페이지 크기는 50 이하여야 합니다.")
            Integer pageSize,
            String cursor
    );

    @Operation(
            summary = "회원 탈퇴",
            description = """
                    로그인한 회원 자신을 자발적으로 탈퇴 처리합니다.

                    - 닉네임은 `플리맵사용자{memberId}`로 마스킹되고(원래 닉네임은 관리자 확인용으로 보존), 프로필 이미지와 자기소개는 삭제됩니다.
                    - 좋아요 목록과 등록한 핀은 유지됩니다.
                    - 팔로우/팔로워 관계는 양방향 모두 삭제됩니다.
                    - 소셜 계정 연동 정보는 삭제되어, 이후 동일한 소셜 계정으로 재가입할 수 있습니다.
                    - 처리 후 현재 세션의 액세스/리프레시 토큰을 무효화하고 로그아웃 쿠키를 반환합니다.
                    - 탈퇴 전에 소셜 로그인 연결을 끊습니다. 카카오는 서버가 바로 끊고, 애플/구글은 로그인 때 저장한 토큰으로 끊습니다.
                    - 애플/구글 연결을 끊지 못하면(저장된 토큰 없음·만료) 409 `AUTH_APPLE_REAUTH_REQUIRED` / `AUTH_GOOGLE_REAUTH_REQUIRED`를 반환하고 탈퇴하지 않습니다. 앱은 해당 provider로 다시 로그인해 받은 code를 요청 바디 `authorizationCode`에 담아 다시 호출합니다.
                    - 요청 바디는 재시도할 때만 보내며, 처음 요청은 바디 없이 호출합니다.
                    - 웹에서 `AUTH_GOOGLE_REAUTH_REQUIRED`를 받으면 `/oauth/authorization/google?reauth=true`로 구글 로그인을 다시 진행합니다. 이때만 동의 화면이 표시되고 새 refresh token이 저장되므로, 돌아온 뒤 바디 없이 다시 호출합니다. 재로그인 전후 회원이 다르면(다른 구글 계정 선택) 탈퇴를 이어가지 않아야 합니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "탈퇴할 활성 회원을 찾을 수 없는 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = @ExampleObject(
                                    name = "MEMBER_NOT_FOUND",
                                    summary = "회원 없음",
                                    value = MemberSwaggerErrorExamples.MEMBER_NOT_FOUND
                            )
                    )),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "탈퇴 회원 마스킹 닉네임이 중복됐거나, 소셜 연결을 끊으려면 재로그인이 필요한 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "MEMBER_NICKNAME_DUPLICATE",
                                            summary = "마스킹 닉네임 중복",
                                            value = MemberSwaggerErrorExamples.NICKNAME_DUPLICATE
                                    ),
                                    @ExampleObject(
                                            name = "AUTH_APPLE_REAUTH_REQUIRED",
                                            summary = "애플 재로그인 필요",
                                            value = MemberSwaggerErrorExamples.APPLE_REAUTH_REQUIRED
                                    ),
                                    @ExampleObject(
                                            name = "AUTH_GOOGLE_REAUTH_REQUIRED",
                                            summary = "구글 재로그인 필요",
                                            value = MemberSwaggerErrorExamples.GOOGLE_REAUTH_REQUIRED
                                    )
                            }
                    )),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "502",
                    description = "애플/구글 연결 해제 요청이 네트워크·서버 오류로 실패한 경우(재시도 가능)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = @ExampleObject(
                                    name = "AUTH_SOCIAL_UNLINK_FAILED",
                                    summary = "소셜 연결 해제 실패",
                                    value = MemberSwaggerErrorExamples.SOCIAL_UNLINK_FAILED
                            )
                    )),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 정보가 없거나 유효하지 않은 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = @ExampleObject(
                                    name = "COMMON_401_UNAUTHORIZED",
                                    summary = "인증 필요",
                                    value = CommonSwaggerErrorExamples.UNAUTHORIZED
                            )
                    )),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "요청 성공")
    })
    ApiResponse<Void> withdraw(
            AuthMember authMember,
            MemberReqDTO.Withdraw withdrawRequest,
            HttpServletRequest request,
            HttpServletResponse response
    );
}
