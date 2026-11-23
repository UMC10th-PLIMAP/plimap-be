package com.example.plimap.domain.pin.controller.docs;

import com.example.plimap.domain.auth.entity.AuthMember;
import com.example.plimap.domain.pin.dto.Pagination;
import com.example.plimap.domain.pin.dto.request.PinRequest;
import com.example.plimap.domain.pin.dto.response.PinResponse;
import com.example.plimap.domain.pin.enums.PinSortType;
import com.example.plimap.global.apiPayload.ApiResponse;
import com.example.plimap.global.swagger.CommonSwaggerErrorExamples;
import com.example.plimap.global.swagger.ErrorApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

public interface PinControllerDocs {

    @Operation(
            summary = "PIN 등록",
            description = "해당 pin, 장소, 노래 정보를 저장합니다.(Figma 기준 화면: PN-03-02)"
    )
    @ApiResponses({
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
                    responseCode = "400",
                    description = "요청 본문·위치 검증에 실패했거나 선택 곡 정보가 올바르지 않은 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "COMMON_400_VALIDATION_FAILED",
                                            summary = "요청 값 검증 실패",
                                            value = PinSwaggerErrorExamples.LOCATION_VALIDATION_FAILED
                                    ),
                                    @ExampleObject(
                                            name = "COMMON_400_MALFORMED_JSON",
                                            summary = "잘못된 JSON 본문",
                                            value = CommonSwaggerErrorExamples.MALFORMED_JSON
                                    ),
                                    @ExampleObject(
                                            name = "PIN_LOCATION_DISTANCE_INVALID",
                                            summary = "장소 반경 밖에서 PIN 등록",
                                            value = PinSwaggerErrorExamples.LOCATION_DISTANCE_INVALID
                                    ),
                                    @ExampleObject(
                                            name = "TRACK_YOUTUBE_VIDEO_ID_NOT_FOUND",
                                            summary = "선택 곡 YouTube 영상 ID 없음",
                                            value = PinSwaggerErrorExamples.YOUTUBE_VIDEO_ID_NOT_FOUND
                                    )
                            }
                    )),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "장소·태그 또는 선택 곡 캐시를 찾을 수 없는 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "PLACE_NOT_FOUND",
                                            summary = "장소 없음",
                                            value = PinSwaggerErrorExamples.PLACE_NOT_FOUND
                                    ),
                                    @ExampleObject(
                                            name = "TAG_NOT_FOUND",
                                            summary = "태그 없음",
                                            value = PinSwaggerErrorExamples.TAG_NOT_FOUND
                                    ),
                                    @ExampleObject(
                                            name = "TRACK_SELECTED_TRACK_CACHE_NOT_FOUND",
                                            summary = "선택 곡 캐시 없음",
                                            value = PinSwaggerErrorExamples.SELECTED_TRACK_CACHE_NOT_FOUND
                                    )
                            }
                    )),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "같은 회원이 해당 장소에 이미 PIN을 등록한 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = @ExampleObject(
                                    name = "PIN_MEMBER_PIN_ALREADY_EXISTS",
                                    summary = "장소별 회원 PIN 중복",
                                    value = PinSwaggerErrorExamples.MEMBER_PIN_ALREADY_EXISTS
                            )
                    )),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "선택 곡 캐시 조회 기능을 사용할 수 없는 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = @ExampleObject(
                                    name = "TRACK_SELECTED_TRACK_CACHE_READER_NOT_AVAILABLE",
                                    summary = "선택 곡 캐시 조회 기능 없음",
                                    value = PinSwaggerErrorExamples.SELECTED_TRACK_CACHE_READER_NOT_AVAILABLE
                            )
                    ))
    })
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201",
            description = "PIN 등록 성공")
    public ResponseEntity<ApiResponse<PinResponse.Summary>> createPin(
            @AuthenticationPrincipal AuthMember currentMember,
            @RequestBody @Valid PinRequest.Create request
    );

    @Operation(
            summary = "지도 선택 위치 검증",
            description = "해당 위치에 핀을 등록할 수 있는지 검증합니다. (Figma 기준 화면: PN-02-02)"
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "선택 위치 또는 사용자 현재 위치 검증에 실패한 경우",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ErrorApiResponse.class),
                    examples = @ExampleObject(
                            name = "COMMON_400_VALIDATION_FAILED",
                            summary = "위치 검증 실패",
                            value = PinSwaggerErrorExamples.LOCATION_VALIDATION_FAILED
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
    public ResponseEntity<ApiResponse<PinResponse.PinAvailability>> validatePinAvailability(
            @ModelAttribute @Valid PinRequest.PinAvailability request
    ) ;

    @Operation(
            summary = "PIN 수정",
            description = "핀 내용을 수정합니다. (Figma 기준 화면: PN-01-03)"
    )
    @ApiResponses({
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
                    responseCode = "400",
                    description = "요청 본문 검증에 실패했거나 수정할 내용이 없는 경우",
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
                                            name = "PIN_NOT_CHANGED",
                                            summary = "수정사항 없음",
                                            value = PinSwaggerErrorExamples.PIN_NOT_CHANGED
                                    )
                            }
                    )),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "PIN 작성자가 아닌 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = @ExampleObject(
                                    name = "PIN_INVALID_PIN_OWNER",
                                    summary = "PIN 수정 권한 없음",
                                    value = PinSwaggerErrorExamples.INVALID_PIN_OWNER
                            )
                    )),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "PIN 또는 변경할 태그를 찾을 수 없는 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "PIN_NOT_FOUND",
                                            summary = "PIN 없음",
                                            value = PinSwaggerErrorExamples.PIN_NOT_FOUND
                                    ),
                                    @ExampleObject(
                                            name = "TAG_NOT_FOUND",
                                            summary = "태그 없음",
                                            value = PinSwaggerErrorExamples.TAG_NOT_FOUND
                                    ),
                                    @ExampleObject(
                                            name = "TAG_SIZE_OVER_RANGE",
                                            summary = "태그 개수 초과",
                                            value = PinSwaggerErrorExamples.TAG_SIZE_OVER_RANGE
                                    )
                            }
                    ))
    })
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "요청 성공")
    public ResponseEntity<ApiResponse<PinResponse.UpdatedPin>> updatePin(
            @AuthenticationPrincipal AuthMember currentMember,
            @RequestBody @Valid PinRequest.Update request,
            @PathVariable Long pinId
    );

    @Operation(
            summary = "PIN 삭제",
            description = "핀 내용을 삭제합니다. (Figma 기준 화면: PN-01-03)"
    )
    @ApiResponses({
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
                    responseCode = "403",
                    description = "PIN 작성자가 아닌 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = @ExampleObject(
                                    name = "PIN_INVALID_PIN_OWNER",
                                    summary = "PIN 삭제 권한 없음",
                                    value = PinSwaggerErrorExamples.INVALID_PIN_OWNER
                            )
                    )),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "PIN을 찾을 수 없는 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = @ExampleObject(
                                    name = "PIN_NOT_FOUND",
                                    summary = "PIN 없음",
                                    value = PinSwaggerErrorExamples.PIN_NOT_FOUND
                            )
                    ))
    })
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "요청 성공")
    public ResponseEntity<ApiResponse<Void>> deletePin(
            @AuthenticationPrincipal AuthMember currentMember,
            @PathVariable Long pinId
    );

    @Operation(
            summary = "PIN 좋아요 등록",
            description = "사용자의 핀 좋아요(따봉)을 등록합니다. (Figma 기준 화면: PN-01-03)"
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "PIN을 찾을 수 없는 경우",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ErrorApiResponse.class),
                    examples = @ExampleObject(
                            name = "PIN_NOT_FOUND",
                            summary = "PIN 없음",
                            value = PinSwaggerErrorExamples.PIN_NOT_FOUND
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
    public ResponseEntity<ApiResponse<PinResponse.LikeCount>> createPinLike(
            @AuthenticationPrincipal AuthMember currentMember,
            @PathVariable Long pinId
    );

    @Operation(
            summary = "PIN 좋아요 삭제",
            description = "사용자의 핀 좋아요(따봉)을 삭제합니다. (Figma 기준 화면: PN-01-03)"
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "PIN을 찾을 수 없는 경우",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ErrorApiResponse.class),
                    examples = @ExampleObject(
                            name = "PIN_NOT_FOUND",
                            summary = "PIN 없음",
                            value = PinSwaggerErrorExamples.PIN_NOT_FOUND
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
    public ResponseEntity<ApiResponse<PinResponse.LikeCount>> deletePinLike(
            @AuthenticationPrincipal AuthMember currentMember,
            @PathVariable Long pinId
    );

    @Operation(
            summary = "내 피드 목록 조회",
            description = "내 피드 목록을 조회합니다. (Figma 기준 화면: FD-01-01)"
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "페이지 크기·위치 검증에 실패했거나 커서가 올바르지 않은 경우",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ErrorApiResponse.class),
                    examples = {
                            @ExampleObject(
                                    name = "COMMON_400_VALIDATION_FAILED_PAGE_SIZE",
                                    summary = "페이지 크기 검증 실패",
                                    value = PinSwaggerErrorExamples.PAGE_SIZE_VALIDATION_FAILED
                            ),
                            @ExampleObject(
                                    name = "COMMON_400_VALIDATION_FAILED_LOCATION",
                                    summary = "위치 검증 실패",
                                    value = PinSwaggerErrorExamples.LOCATION_VALIDATION_FAILED
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
    public ResponseEntity<ApiResponse<Pagination<PinResponse.Feed>>> getMyFeedList(
            @AuthenticationPrincipal AuthMember currentMember,
            @RequestParam(required = false, defaultValue = "10")
            @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
            @Max(value = 50, message = "페이지 크기는 50 이하여야 합니다.")
            Integer pageSize,
            @RequestParam(required = false) String cursor,
            @Valid @ModelAttribute PinRequest.UserLocation request
    );

    @Operation(
            summary = "타인 피드 목록 조회",
            description = "타인 피드 목록을 조회합니다. 내가 신고한 PIN은 목록에서 제외됩니다. (Figma 기준 화면: FD-02-01)"
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "페이지 크기·위치 검증에 실패했거나 커서가 올바르지 않은 경우",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ErrorApiResponse.class),
                    examples = {
                            @ExampleObject(
                                    name = "COMMON_400_VALIDATION_FAILED_PAGE_SIZE",
                                    summary = "페이지 크기 검증 실패",
                                    value = PinSwaggerErrorExamples.PAGE_SIZE_VALIDATION_FAILED
                            ),
                            @ExampleObject(
                                    name = "COMMON_400_VALIDATION_FAILED_LOCATION",
                                    summary = "위치 검증 실패",
                                    value = PinSwaggerErrorExamples.LOCATION_VALIDATION_FAILED
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
    public ResponseEntity<ApiResponse<Pagination<PinResponse.Feed>>> getMemberFeedList(
            @AuthenticationPrincipal AuthMember currentMember,
            @PathVariable Long memberId,
            @RequestParam(required = false, defaultValue = "10")
            @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
            @Max(value = 50, message = "페이지 크기는 50 이하여야 합니다.")
            Integer pageSize,
            @RequestParam(required = false) String cursor,
            @Valid @ModelAttribute PinRequest.UserLocation request
    );

    @Operation(
            summary = "내가 작성한 PIN 목록 조회",
            description = "내가 작성한 PIN 목록 조회합니다. (Figma 기준 화면: FD-01-03-b2)"
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
                                    value = PinSwaggerErrorExamples.PAGE_SIZE_VALIDATION_FAILED
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
    public ResponseEntity<ApiResponse<Pagination<PinResponse.MyPin>>> getMyPinList(
            @AuthenticationPrincipal AuthMember currentMember,
            @RequestParam(required = false, defaultValue = "10")
            @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
            @Max(value = 50, message = "페이지 크기는 50 이하여야 합니다.")
            Integer pageSize,
            @RequestParam(required = false) String cursor
    );

    @Operation(
            summary = "특정 장소 노래의 PIN 목록 조회",
            description = "특정 장소 노래의 PIN 목록을 조회합니다. (Figma 기준 화면: PN-01-03)"
    )
    @ApiResponses({
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
                    responseCode = "400",
                    description = "페이지·정렬·위치 요청 값 또는 커서가 올바르지 않은 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "COMMON_400_VALIDATION_FAILED",
                                            summary = "페이지 크기 또는 위치 검증 실패",
                                            value = PinSwaggerErrorExamples.PAGE_SIZE_VALIDATION_FAILED
                                    ),
                                    @ExampleObject(
                                            name = "COMMON_400_TYPE_MISMATCH",
                                            summary = "정렬 타입 오류",
                                            value = CommonSwaggerErrorExamples.TYPE_MISMATCH
                                    ),
                                    @ExampleObject(
                                            name = "COMMON_400_INVALID_CURSOR",
                                            summary = "잘못된 커서",
                                            value = CommonSwaggerErrorExamples.INVALID_CURSOR
                                    )
                            }
                    )),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "거리·내 PIN·좋아요·임시 토큰 중 접근 조건을 충족하지 못한 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = @ExampleObject(
                                    name = "PIN_ACCESS_DENIED",
                                    summary = "장소별 곡 PIN 접근 권한 없음",
                                    value = PinSwaggerErrorExamples.PIN_ACCESS_DENIED
                            )
                    )),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "장소별 곡을 찾을 수 없는 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = @ExampleObject(
                                    name = "TRACK_PLACE_TRACK_NOT_FOUND",
                                    summary = "장소별 곡 없음",
                                    value = PinSwaggerErrorExamples.PLACE_TRACK_NOT_FOUND
                            )
                    ))
    })
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "요청 성공")
    public ResponseEntity<ApiResponse<Pagination<PinResponse.PinDetail>>> getPlaceTrackPinList(
            @AuthenticationPrincipal AuthMember currentMember,
            @RequestParam(required = false, defaultValue = "10")
            @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
            @Max(value = 50, message = "페이지 크기는 50 이하여야 합니다.")
            Integer pageSize,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false, defaultValue = "LATEST")
            PinSortType pinSortType,
            @PathVariable Long placeTrackId,
            @ModelAttribute @Valid PinRequest.UserLocation request,
            @RequestHeader(value = "Place-Access-Token", required = false) String token
    );

    @Operation(
            summary = "PIN 상세 보기",
            description = "특정 PIN을 조회합니다. 대표핀 대신 해당 핀의 미리보기를 보여줘야할 때 사용한다.(Figma 기준 화면: FD-01-04)"
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "조회 가능한 PIN을 찾을 수 없는 경우",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ErrorApiResponse.class),
                    examples = @ExampleObject(
                            name = "PIN_NOT_FOUND",
                            summary = "PIN 없음",
                            value = PinSwaggerErrorExamples.PIN_NOT_FOUND
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
    public ResponseEntity<ApiResponse<PinResponse.PinPreview>> getPinPreview(
            @AuthenticationPrincipal AuthMember currentMember,
            @PathVariable Long pinId
    );

    @Operation(
            summary = "지도 viewport PIN 클러스터 조회",
            description = "장소별로 지도에 표시할 대표 핀 1개씩만 반환한다. (Figma 기준 화면: MP-01-01)"
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "viewport 좌표 또는 확대 단계 검증에 실패한 경우",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ErrorApiResponse.class),
                    examples = @ExampleObject(
                            name = "COMMON_400_VALIDATION_FAILED",
                            summary = "viewport 검증 실패",
                            value = PinSwaggerErrorExamples.LOCATION_VALIDATION_FAILED
                    )
            ))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "요청 성공")
    public ResponseEntity<ApiResponse<PinResponse.ClusterAndPin>> getClusterPinList(
            @AuthenticationPrincipal AuthMember currentMember,
            @Valid @ModelAttribute PinRequest.Viewport request
    );

    @Operation(
            summary = "내 친구 최근 핀 목록 조회",
            description = "친구가 24시간 내에 등록한 피드공개 상태 핀을 조회한다. (Figma 기준 화면: 홈화면 친구찾기)"
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
                                    value = PinSwaggerErrorExamples.PAGE_SIZE_VALIDATION_FAILED
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
    public ResponseEntity<ApiResponse<Pagination<PinResponse.FriendPin>>> getFriendRecentPinList(
            @AuthenticationPrincipal AuthMember currentMember,
            @RequestParam(required = false, defaultValue = "10")
            @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
            @Max(value = 50, message = "페이지 크기는 50 이하여야 합니다.")
            Integer pageSize,
            @RequestParam(required = false) String cursor
    );

    @Operation(
            summary = "내 친구 피드 접근 권한 요청",
            description = "친구 피드에서 특정 핀을 선택한 경우, 해당 장소의 곡 상세(PIN 목록/Track 상세) 조회를 위한 임시 접근 권한을 발급한다. (Figma 기준 화면: FD-02-01)"
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "친구가 등록한 PIN이 있는 장소가 아닌 경우",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ErrorApiResponse.class),
                    examples = @ExampleObject(
                            name = "PIN_FRIEND_PIN_ACCESS_DENIED",
                            summary = "친구 피드 장소 접근 권한 없음",
                            value = PinSwaggerErrorExamples.FRIEND_PIN_ACCESS_DENIED
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
    public ResponseEntity<ApiResponse<PinResponse.PlaceAccessToken>> createPlaceAccessToken(
            @AuthenticationPrincipal AuthMember currentMember,
            @PathVariable Long placeId
    );

    @Operation(
            summary = "최신 등록 핀 목록 조회",
            description = "최근 등록된 핀 목록을 조회한다. (Figma 기준 화면: 홈화면 확정 (11.08))"
    )
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
            responseCode = "400",
            description = "페이지 크기 검증에 실패했거나 커서가 올바르지 않은 경우",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ErrorApiResponse.class),
                    examples = {
                            @ExampleObject(
                                    name = "COMMON_400_VALIDATION_FAILED",
                                    summary = "페이지 크기 검증 실패",
                                    value = PinSwaggerErrorExamples.PAGE_SIZE_VALIDATION_FAILED
                            ),
                            @ExampleObject(
                                    name = "COMMON_400_INVALID_CURSOR",
                                    summary = "잘못된 커서",
                                    value = CommonSwaggerErrorExamples.INVALID_CURSOR
                            )
                    }
            ))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "요청 성공")
    public ResponseEntity<ApiResponse<Pagination<PinResponse.Feed>>> getHomeRecentPinList(
            @AuthenticationPrincipal AuthMember currentMember,
            @RequestParam(required = false, defaultValue = "10")
            @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
            @Max(value = 50, message = "페이지 크기는 50 이하여야 합니다.")
            Integer pageSize,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Boolean within500m,
            @Valid @ModelAttribute PinRequest.UserLocation request
    );
}
