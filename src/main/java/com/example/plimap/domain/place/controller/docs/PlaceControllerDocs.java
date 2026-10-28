package com.example.plimap.domain.place.controller.docs;

import com.example.plimap.domain.auth.entity.AuthMember;
import com.example.plimap.domain.place.dto.request.PlaceRequest;
import com.example.plimap.domain.place.dto.response.PlaceResponse;
import com.example.plimap.global.apiPayload.ApiResponse;
import com.example.plimap.global.swagger.CommonSwaggerErrorExamples;
import com.example.plimap.global.swagger.ErrorApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@Tag(name = "Place", description = "장소 API")
public interface PlaceControllerDocs {

    @Operation(
            summary = "인기 장소 목록 조회",
            description = "NEARBY는 반경 제한 없이 활성 PIN이 있는 활성 장소를 실제 거리 ASC, "
                    + "활성 PIN 수 DESC, placeId ASC 순으로 최대 6개 조회합니다. "
                    + "GLOBAL은 Kakao 행정동(H) 결과를 기준으로 "
                    + "REGION3, REGION2, REGION1, GLOBAL 순서로 범위를 확장하며, 각 단계 결과가 "
                    + "6개 미만이면 이전 결과를 버리고 상위 범위를 다시 조회합니다. H 결과가 "
                    + "없으면 전국으로 조회합니다. 각 단계는 활성 PIN 수 DESC, 실제 거리 ASC, "
                    + "placeId ASC 순으로 최대 6개를 조회하며 전국 결과는 6개 미만이어도 "
                    + "반환합니다. scopeLevel과 scopeName은 실제 적용 범위이며 NEARBY에서는 "
                    + "null입니다. (Figma 기준 화면: HM-01-01)"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "인기 장소 목록 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "scope 또는 현재 위치 검증에 실패한 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "COMMON_400_VALIDATION_FAILED_SCOPE",
                                            summary = "조회 범위 검증 실패",
                                            value = PlaceSwaggerErrorExamples.SCOPE_VALIDATION_FAILED
                                    ),
                                    @ExampleObject(
                                            name = "COMMON_400_VALIDATION_FAILED_LOCATION",
                                            summary = "현재 위치 검증 실패",
                                            value = PlaceSwaggerErrorExamples.VALIDATION_FAILED
                                    )
                            }
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
                    responseCode = "502",
                    description = "Kakao 행정구역 변환 연동에 실패한 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = @ExampleObject(
                                    name = "PLACE_EXTERNAL_API_ERROR",
                                    summary = "외부 장소 API 연동 실패",
                                    value = PlaceSwaggerErrorExamples.PLACE_EXTERNAL_API_ERROR
                            )
                    )),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "504",
                    description = "Kakao 행정구역 변환 응답이 지연된 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = @ExampleObject(
                                    name = "PLACE_EXTERNAL_API_TIMEOUT",
                                    summary = "외부 장소 API 응답 지연",
                                    value = PlaceSwaggerErrorExamples.PLACE_EXTERNAL_API_TIMEOUT
                            )
                    ))
    })
    ResponseEntity<ApiResponse<PlaceResponse.PopularListResult>> getPopularPlaces(
            @AuthenticationPrincipal AuthMember currentMember,
            @Parameter(
                    description = "조회 범위",
                    required = true,
                    example = "NEARBY",
                    schema = @io.swagger.v3.oas.annotations.media.Schema(
                            allowableValues = {"NEARBY", "GLOBAL"}
                    )
            )
            @NotNull(message = "조회 범위가 올바르지 않습니다.")
            @Pattern(
                    regexp = "NEARBY|GLOBAL",
                    message = "조회 범위가 올바르지 않습니다."
            )
            String scope,
            @Parameter(description = "사용자 현재 위도", required = true, example = "37.5283")
            @NotNull(message = PlaceRequest.INVALID_LOCATION_MESSAGE)
            @DecimalMin(value = "-90", message = PlaceRequest.INVALID_LOCATION_MESSAGE)
            @DecimalMax(value = "90", message = PlaceRequest.INVALID_LOCATION_MESSAGE)
            Double latitude,
            @Parameter(description = "사용자 현재 경도", required = true, example = "126.9326")
            @NotNull(message = PlaceRequest.INVALID_LOCATION_MESSAGE)
            @DecimalMin(value = "-180", message = PlaceRequest.INVALID_LOCATION_MESSAGE)
            @DecimalMax(value = "180", message = PlaceRequest.INVALID_LOCATION_MESSAGE)
            Double longitude
    );

    @Operation(
            summary = "저장한 장소 목록 조회",
            description = "인증 사용자가 저장한 활성 장소 중 현재 위치에서 정확히 500m "
                    + "이내인 장소를 거리, 북마크 생성 시각, 장소 ID 순으로 최대 9개 "
                    + "조회합니다. 활성 PIN이 없으면 최초 작성자 닉네임은 null입니다. "
                    + "(Figma 기준 화면: HM-01)"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "저장한 장소 목록 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "현재 위치가 누락되었거나 범위 검증에 실패한 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = @ExampleObject(
                                    name = "COMMON_400_VALIDATION_FAILED",
                                    summary = "현재 위치 검증 실패",
                                    value = PlaceSwaggerErrorExamples.VALIDATION_FAILED
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
    ResponseEntity<ApiResponse<PlaceResponse.BookmarkListResult>> getPlaceBookmarks(
            @AuthenticationPrincipal AuthMember currentMember,
            @Parameter(description = "사용자 현재 위도", required = true, example = "37.5283")
            @NotNull(message = PlaceRequest.INVALID_LOCATION_MESSAGE)
            @DecimalMin(value = "-90", message = PlaceRequest.INVALID_LOCATION_MESSAGE)
            @DecimalMax(value = "90", message = PlaceRequest.INVALID_LOCATION_MESSAGE)
            Double latitude,
            @Parameter(description = "사용자 현재 경도", required = true, example = "126.9326")
            @NotNull(message = PlaceRequest.INVALID_LOCATION_MESSAGE)
            @DecimalMin(value = "-180", message = PlaceRequest.INVALID_LOCATION_MESSAGE)
            @DecimalMax(value = "180", message = PlaceRequest.INVALID_LOCATION_MESSAGE)
            Double longitude
    );

    @Operation(
            summary = "장소 상세 조회",
            description = "활성 장소의 기본 정보와 현재 위치 기준 거리, 전체 활성 PIN 집계와 최초 활성 PIN 작성자, "
                    + "인증 사용자의 북마크 및 활성 PIN 등록 여부를 조회합니다. "
                    + "장소별 곡 접근 가능 여부는 장소별 곡 목록 API의 "
                    + "isTrackDetailAccessible을 사용합니다. "
                    + "(Figma 기준 화면: MP-02-01, MP-02-02)"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "장소 상세 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "현재 위치가 누락되었거나 범위 검증에 실패한 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = @ExampleObject(
                                    name = "COMMON_400_VALIDATION_FAILED",
                                    summary = "현재 위치 검증 실패",
                                    value = PlaceSwaggerErrorExamples.VALIDATION_FAILED
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
                    responseCode = "404",
                    description = "존재하지 않거나 삭제된 장소를 조회한 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = @ExampleObject(
                                    name = "PLACE_NOT_FOUND",
                                    summary = "활성 장소를 찾을 수 없음",
                                    value = PlaceSwaggerErrorExamples.PLACE_NOT_FOUND
                            )
                    ))
    })
    ResponseEntity<ApiResponse<PlaceResponse.Detail>> getPlaceDetail(
            @AuthenticationPrincipal AuthMember currentMember,
            @Parameter(description = "장소 ID", required = true, example = "1")
            Long placeId,
            @Parameter(description = "사용자 현재 위도", required = true, example = "37.5283")
            @NotNull(message = PlaceRequest.INVALID_LOCATION_MESSAGE)
            @DecimalMin(value = "-90", message = PlaceRequest.INVALID_LOCATION_MESSAGE)
            @DecimalMax(value = "90", message = PlaceRequest.INVALID_LOCATION_MESSAGE)
            Double latitude,
            @Parameter(description = "사용자 현재 경도", required = true, example = "126.9326")
            @NotNull(message = PlaceRequest.INVALID_LOCATION_MESSAGE)
            @DecimalMin(value = "-180", message = PlaceRequest.INVALID_LOCATION_MESSAGE)
            @DecimalMax(value = "180", message = PlaceRequest.INVALID_LOCATION_MESSAGE)
            Double longitude
    );

    @Operation(
            summary = "장소 북마크 등록",
            description = "인증 사용자가 활성 장소를 북마크합니다. 현재 위치 및 상세 조회 권한과 "
                    + "관계없이 처리하며, 이미 북마크한 장소도 중복 행 없이 성공합니다. "
                    + "(Figma 기준 화면: MP-02-01, MP-02-02)"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "장소 북마크 등록 성공"),
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
                    responseCode = "404",
                    description = "존재하지 않거나 삭제된 장소를 북마크하려는 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = @ExampleObject(
                                    name = "PLACE_NOT_FOUND",
                                    summary = "활성 장소를 찾을 수 없음",
                                    value = PlaceSwaggerErrorExamples.PLACE_NOT_FOUND
                            )
                    ))
    })
    ResponseEntity<ApiResponse<PlaceResponse.BookmarkResult>> bookmarkPlace(
            @AuthenticationPrincipal AuthMember currentMember,
            @Parameter(description = "장소 ID", required = true, example = "1")
            Long placeId
    );

    @Operation(
            summary = "장소 북마크 삭제",
            description = "인증 사용자가 소유한 활성 장소의 북마크를 삭제합니다. 현재 위치 및 "
                    + "상세 조회 권한과 관계없이 처리하며, 미등록 상태도 성공합니다. "
                    + "(Figma 기준 화면: MP-01-01, MP-02-01, MP-02-02)"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "장소 북마크 삭제 성공"),
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
                    responseCode = "404",
                    description = "존재하지 않거나 삭제된 장소의 북마크를 삭제하려는 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = @ExampleObject(
                                    name = "PLACE_NOT_FOUND",
                                    summary = "활성 장소를 찾을 수 없음",
                                    value = PlaceSwaggerErrorExamples.PLACE_NOT_FOUND
                            )
                    ))
    })
    ResponseEntity<ApiResponse<PlaceResponse.BookmarkResult>> deletePlaceBookmark(
            @AuthenticationPrincipal AuthMember currentMember,
            @Parameter(description = "장소 ID", required = true, example = "1")
            Long placeId
    );

    @Operation(
            summary = "장소 검색",
            description = "Kakao Local REST API로 주소를 먼저 검색하고, 주소 결과가 없으면 "
                    + "키워드 검색 결과를 Kakao 정확도순으로 반환합니다. 현재 위치는 "
                    + "결과별 거리 계산에 사용하며 검색 반경을 제한하지 않습니다. "
                    + "검색 결과는 저장하지 않습니다. "
                    + "(Figma 기준 화면: MP-02-01, MP-02-01-b, PN-02-01, PN-02-01-b)"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "장소 검색 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "검색어 또는 현재 위치 검증에 실패한 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "PLACE_SEARCH_KEYWORD_REQUIRED",
                                            summary = "검색어 누락",
                                            value = PlaceSwaggerErrorExamples.PLACE_SEARCH_KEYWORD_REQUIRED
                                    ),
                                    @ExampleObject(
                                            name = "PLACE_CURRENT_LOCATION_REQUIRED",
                                            summary = "현재 위치 누락",
                                            value = PlaceSwaggerErrorExamples.PLACE_CURRENT_LOCATION_REQUIRED
                                    ),
                                    @ExampleObject(
                                            name = "COMMON_400_VALIDATION_FAILED",
                                            summary = "현재 위치 범위 검증 실패",
                                            value = PlaceSwaggerErrorExamples.VALIDATION_FAILED
                                    )
                            }
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
                    responseCode = "502",
                    description = "Kakao 주소 또는 장소 검색 연동에 실패한 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = @ExampleObject(
                                    name = "PLACE_EXTERNAL_API_ERROR",
                                    summary = "외부 장소 API 연동 실패",
                                    value = PlaceSwaggerErrorExamples.PLACE_EXTERNAL_API_ERROR
                            )
                    )),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "504",
                    description = "Kakao 주소 또는 장소 검색 응답이 지연된 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = @ExampleObject(
                                    name = "PLACE_EXTERNAL_API_TIMEOUT",
                                    summary = "외부 장소 API 응답 지연",
                                    value = PlaceSwaggerErrorExamples.PLACE_EXTERNAL_API_TIMEOUT
                            )
                    ))
    })
    ResponseEntity<ApiResponse<PlaceResponse.SearchResult>> searchPlaces(
            @Parameter(description = "검색어", required = true, example = "한강")
            String keyword,
            @Parameter(description = "사용자 현재 위도", required = true, example = "37.5283")
            @DecimalMin(value = "-90", message = PlaceRequest.INVALID_LOCATION_MESSAGE)
            @DecimalMax(value = "90", message = PlaceRequest.INVALID_LOCATION_MESSAGE)
            Double latitude,
            @Parameter(description = "사용자 현재 경도", required = true, example = "126.9326")
            @DecimalMin(value = "-180", message = PlaceRequest.INVALID_LOCATION_MESSAGE)
            @DecimalMax(value = "180", message = PlaceRequest.INVALID_LOCATION_MESSAGE)
            Double longitude
    );

    @Operation(
            summary = "검색 장소 선택",
            description = "Kakao 장소 또는 주소 검색 결과를 PLACE_SEARCH/ADDRESS_SEARCH "
                    + "Place와 매핑하거나 새 Place로 생성하고 주소·거리·PIN·북마크 정보를 "
                    + "반환합니다. ADDRESS는 providerPlaceId/category가 null이며 전체 지번 "
                    + "주소 기준으로 재사용합니다. "
                    + "선택 성공 시 인증 사용자의 최근 검색 장소 이력을 저장합니다. "
                    + "(Figma 기준 화면: MP-02-02-a, MP-02-02-b, MP-02-02-c, "
                    + "MP-02-02-d, PN-02-03-a)"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "검색 장소 선택 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "요청 본문 형식 또는 필수 장소 정보 검증에 실패한 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "COMMON_400_MALFORMED_JSON",
                                            summary = "잘못된 JSON 본문",
                                            value = CommonSwaggerErrorExamples.MALFORMED_JSON
                                    ),
                                    @ExampleObject(
                                            name = "PLACE_SELECTION_INVALID",
                                            summary = "장소 선택 정보 불일치",
                                            value = PlaceSwaggerErrorExamples.PLACE_SELECTION_INVALID
                                    )
                            }
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
    ResponseEntity<ApiResponse<PlaceResponse.Selection>> selectSearchPlace(
            @AuthenticationPrincipal AuthMember currentMember,
            PlaceRequest.Selection request
    );

    @Operation(
            summary = "최근 검색 장소 목록 조회",
            description = "장소 선택 성공 시 저장된 인증 사용자의 최근 검색 장소를 "
                    + "최신 선택순으로 최대 5개 조회합니다. "
                    + "Soft Delete된 Place의 이력은 제외하며 과거 이력으로 채우지 않습니다. "
                    + "(Figma 기준 화면: MP-02-01, PN-02-01)"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "최근 검색 장소 목록 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "현재 위치 검증에 실패한 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = @ExampleObject(
                                    name = "COMMON_400_VALIDATION_FAILED",
                                    summary = "현재 위치 검증 실패",
                                    value = PlaceSwaggerErrorExamples.VALIDATION_FAILED
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
    ResponseEntity<ApiResponse<PlaceResponse.SearchHistoryResult>> getSearchHistories(
            @AuthenticationPrincipal AuthMember currentMember,
            @Parameter(description = "사용자 현재 위도", required = true, example = "37.5283")
            @NotNull(message = PlaceRequest.INVALID_LOCATION_MESSAGE)
            @DecimalMin(value = "-90", message = PlaceRequest.INVALID_LOCATION_MESSAGE)
            @DecimalMax(value = "90", message = PlaceRequest.INVALID_LOCATION_MESSAGE)
            Double latitude,
            @Parameter(description = "사용자 현재 경도", required = true, example = "126.9326")
            @NotNull(message = PlaceRequest.INVALID_LOCATION_MESSAGE)
            @DecimalMin(value = "-180", message = PlaceRequest.INVALID_LOCATION_MESSAGE)
            @DecimalMax(value = "180", message = PlaceRequest.INVALID_LOCATION_MESSAGE)
            Double longitude
    );

    @Operation(
            summary = "최근 검색 장소 삭제",
            description = "인증 사용자가 소유한 최근 검색 장소 이력을 삭제합니다. "
                    + "이력 삭제는 Place에 영향을 주지 않으며 과거 이력을 다시 채우지 않습니다. "
                    + "(Figma 기준 화면: MP-02-01, PN-02-01)"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "최근 검색 장소 삭제 성공"),
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
                    responseCode = "404",
                    description = "이력이 없거나 다른 사용자가 소유한 이력인 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = @ExampleObject(
                                    name = "PLACE_SEARCH_HISTORY_NOT_FOUND",
                                    summary = "소유한 최근 검색 이력을 찾을 수 없음",
                                    value = PlaceSwaggerErrorExamples.PLACE_SEARCH_HISTORY_NOT_FOUND
                            )
                    ))
    })
    ResponseEntity<ApiResponse<Void>> deleteSearchHistory(
            @AuthenticationPrincipal AuthMember currentMember,
            @Parameter(description = "최근 검색 이력 ID", required = true, example = "10")
            Long historyId
    );

    @Operation(
            summary = "지도 선택 장소 확정",
            description = "지도에서 선택한 좌표를 판정하여 기존 PLACE_SEARCH Place를 추천하거나, "
                    + "건물명으로 장소 검색을 유도하거나, MAP_SELECTION Place를 확정합니다. "
                    + "MAP_SELECTION 장소명은 요청으로 받지 않고 전체 주소에서 국가명과 시/도명을 "
                    + "제외해 백엔드에서 생성합니다. "
                    + "(Figma 기준 화면: PN-02-03-b)"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "지도 선택 장소 확정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "요청 본문 형식 또는 좌표 검증에 실패한 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "COMMON_400_VALIDATION_FAILED",
                                            summary = "좌표 검증 실패",
                                            value = PlaceSwaggerErrorExamples.VALIDATION_FAILED
                                    ),
                                    @ExampleObject(
                                            name = "COMMON_400_MALFORMED_JSON",
                                            summary = "잘못된 JSON 본문",
                                            value = CommonSwaggerErrorExamples.MALFORMED_JSON
                                    )
                            }
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
                    responseCode = "502",
                    description = "Kakao 장소 검색 서비스 연동에 실패한 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = @ExampleObject(
                                    name = "PLACE_EXTERNAL_API_ERROR",
                                    summary = "외부 장소 API 연동 실패",
                                    value = PlaceSwaggerErrorExamples.PLACE_EXTERNAL_API_ERROR
                            )
                    )),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "504",
                    description = "Kakao 장소 검색 서비스 응답이 지연된 경우",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorApiResponse.class),
                            examples = @ExampleObject(
                                    name = "PLACE_EXTERNAL_API_TIMEOUT",
                                    summary = "외부 장소 API 응답 지연",
                                    value = PlaceSwaggerErrorExamples.PLACE_EXTERNAL_API_TIMEOUT
                            )
                    ))
    })
    ResponseEntity<ApiResponse<PlaceResponse.MapSelectionResult>> confirmMapSelection(
            @Valid PlaceRequest.MapSelection request
    );
}
