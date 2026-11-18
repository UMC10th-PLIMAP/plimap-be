package com.example.plimap.domain.place.dto.response;

import com.example.plimap.domain.place.entity.Place;
import com.example.plimap.domain.place.entity.PlaceSource;
import com.example.plimap.domain.place.enums.MapSelectionStatus;
import com.example.plimap.domain.place.enums.PopularPlaceScopeLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

public final class PlaceResponse {

    private PlaceResponse() {
    }

    @Schema(name = "PlaceDetailResponse")
    public record Detail(
            @Schema(description = "장소 ID", example = "1")
            Long placeId,
            @Schema(description = "장소명", example = "한강")
            String placeName,
            @Schema(description = "장소 카테고리", example = "공원", nullable = true)
            String category,
            @Schema(description = "지번 주소", example = "서울특별시 영등포구 여의도동")
            String address,
            @Schema(
                    description = "도로명 주소. 없는 경우 null",
                    example = "서울특별시 영등포구 여의동로",
                    nullable = true
            )
            String roadAddress,
            @Schema(description = "장소 위도", example = "37.5283")
            Double latitude,
            @Schema(description = "장소 경도", example = "126.9326")
            Double longitude,
            @Schema(description = "사용자 현재 위치 기준 거리(m)", example = "470")
            Integer distanceMeters,
            @Schema(description = "사용자가 500m 이내에 있는지 여부", example = "true")
            boolean withinAccessRange,
            @Schema(description = "전체 활성 PIN 존재 여부", example = "true")
            boolean hasPin,
            @Schema(
                    description = "최초 활성 PIN 작성자 닉네임. 활성 PIN이 없으면 null이며, 작성자가 탈퇴한 경우 플리맵사용자",
                    example = "홍길동",
                    nullable = true
            )
            String firstPinCreatorNickname,
            @Schema(description = "전체 활성 PIN 개수", example = "3")
            Long pinCount,
            @Schema(description = "인증 사용자의 장소 북마크 여부", example = "false")
            boolean bookmarkedByMe,
            @Schema(description = "인증 사용자가 직접 등록한 활성 PIN 존재 여부", example = "true")
            boolean pinnedByMe
    ) {
    }

    @Schema(name = "PlaceBookmarkResult")
    public record BookmarkResult(
            @Schema(description = "장소 ID", example = "1")
            Long placeId,
            @Schema(description = "인증 사용자의 장소 북마크 여부", example = "true")
            boolean bookmarkedByMe
    ) {
    }

    @Schema(name = "PlaceBookmarkListResponse")
    public record BookmarkListResult(
            @Schema(description = "저장한 장소 목록")
            List<BookmarkListItem> items
    ) {

        public BookmarkListResult {
            items = List.copyOf(items);
        }
    }

    @Schema(name = "PlaceBookmarkListItem")
    public record BookmarkListItem(
            @Schema(description = "장소 ID", example = "1")
            Long placeId,
            @Schema(description = "장소명", example = "물빛무대 앞 광장")
            String placeName,
            @Schema(
                    description = "최초 활성 PIN 작성자 닉네임. 활성 PIN이 없으면 null",
                    example = "홍길동",
                    nullable = true
            )
            String firstPinCreatorNickname,
            @Schema(description = "사용자 현재 위치 기준 거리(m)", example = "470")
            Integer distanceMeters
    ) {
    }

    @Schema(name = "PlacePopularListResponse")
    public record PopularListResult(
            @Schema(
                    description = "실제 적용 범위. NEARBY이면 null",
                    allowableValues = {"REGION3", "REGION2", "REGION1", "GLOBAL"},
                    nullable = true
            )
            PopularPlaceScopeLevel scopeLevel,
            @Schema(
                    description = "실제 적용 범위명. NEARBY이면 null",
                    example = "역삼1동",
                    nullable = true
            )
            String scopeName,
            @Schema(description = "인기 장소 목록")
            List<PopularListItem> items
    ) {

        public PopularListResult {
            items = List.copyOf(items);
        }
    }

    @Schema(name = "PlacePopularListItem")
    public record PopularListItem(
            @Schema(description = "장소 ID", example = "1")
            Long placeId,
            @Schema(description = "장소명", example = "뚝섬한강공원")
            String placeName,
            @Schema(description = "사용자 현재 위치 기준 반올림 거리(m)", example = "50")
            Integer distanceMeters,
            @Schema(description = "요청자에게 조회 가능한 활성 PIN 수", example = "30")
            Long pinCount,
            @Schema(
                    description = "대표 PlaceTrack의 앨범 이미지 URL. 없으면 null",
                    example = "https://example.com/album.jpg",
                    nullable = true
            )
            String representativeImageUrl
    ) {
    }

    public record SearchResult(
            @Schema(description = "장소 검색 결과 목록")
            List<SearchItem> items
    ) {

        public SearchResult {
            items = List.copyOf(items);
        }
    }

    public record SearchItem(
            @Schema(
                    description = "검색 결과 유형",
                    example = "PLACE",
                    allowableValues = {"PLACE", "ADDRESS"}
            )
            String resultType,
            @Schema(description = "장소 검색 provider", example = "KAKAO")
            String provider,
            @Schema(
                    description = "provider가 제공하는 장소 ID. ADDRESS 결과는 null",
                    example = "26338954",
                    nullable = true
            )
            String providerPlaceId,
            @Schema(
                    description = "장소명. ADDRESS 결과는 roadAddress, address 순으로 결정",
                    example = "한강"
            )
            String placeName,
            @Schema(
                    description = "장소 카테고리. ADDRESS 결과는 null",
                    example = "여행 > 관광,명소 > 공원",
                    nullable = true
            )
            String category,
            @Schema(description = "지번 주소", example = "서울특별시 영등포구 여의도동")
            String address,
            @Schema(
                    description = "도로명 주소. 없는 경우 null",
                    example = "서울특별시 영등포구 여의동로",
                    nullable = true
            )
            String roadAddress,
            @Schema(description = "장소 위도", example = "37.5283")
            Double latitude,
            @Schema(description = "장소 경도", example = "126.9326")
            Double longitude,
            @Schema(description = "사용자 현재 위치 기준 거리(m)", example = "470")
            Integer distanceMeters,
            @Schema(description = "활성 PIN 존재 여부", example = "true")
            boolean hasPin,
            @Schema(description = "최초 PIN 작성자 닉네임", example = "홍길동", nullable = true)
            String firstPinCreatorNickname
    ) {
    }

    public record SearchHistoryResult(
            @Schema(description = "최근 검색 장소 목록")
            List<SearchHistoryItem> items
    ) {

        public SearchHistoryResult {
            items = List.copyOf(items);
        }
    }

    public record SearchHistoryItem(
            @Schema(description = "최근 검색 이력 ID", example = "10")
            Long historyId,
            @Schema(description = "장소 ID", example = "1")
            Long placeId,
            @Schema(description = "장소명", example = "한강")
            String placeName,
            @Schema(description = "장소 카테고리", example = "공원", nullable = true)
            String category,
            @Schema(description = "전체 지번 주소", example = "서울특별시 영등포구 여의도동")
            String address,
            @Schema(
                    description = "전체 도로명 주소. 없는 경우 null",
                    example = "서울특별시 영등포구 여의동로",
                    nullable = true
            )
            String roadAddress,
            @Schema(description = "장소 위도", example = "37.5283")
            Double latitude,
            @Schema(description = "장소 경도", example = "126.9326")
            Double longitude,
            @Schema(description = "사용자 현재 위치 기준 거리(m)", example = "470")
            Integer distanceMeters,
            @Schema(description = "활성 PIN 존재 여부", example = "true")
            boolean hasPin,
            @Schema(description = "최초 활성 PIN 작성자 닉네임", nullable = true)
            String firstPinCreatorNickname,
            @Schema(description = "최근 장소 선택 시각", example = "2026-07-05T12:30:00Z")
            Instant selectedAt
    ) {
    }

    @Schema(name = "PlaceSelectionResponse")
    public record Selection(
            @Schema(description = "장소 ID", example = "1")
            Long placeId,
            @Schema(description = "장소명", example = "한강")
            String placeName,
            @Schema(description = "축약하지 않은 전체 지번 주소", example = "서울특별시 영등포구 여의도동")
            String address,
            @Schema(
                    description = "축약하지 않은 전체 도로명 주소. 없는 경우 null이며 address를 사용",
                    example = "서울특별시 영등포구 여의동로",
                    nullable = true
            )
            String roadAddress,
            @Schema(
                    description = "장소 생성 출처",
                    example = "PLACE_SEARCH",
                    allowableValues = {"PLACE_SEARCH", "ADDRESS_SEARCH", "MAP_SELECTION"}
            )
            PlaceSource source,
            @Schema(description = "사용자 현재 위치 기준 거리(m)", example = "470")
            Integer distanceMeters,
            @Schema(description = "사용자가 500m 이내에 있는지 여부", example = "true")
            boolean withinAccessRange,
            @Schema(description = "활성 PIN 존재 여부", example = "true")
            boolean hasPin,
            @Schema(
                    description = "최초 활성 PIN 작성자 닉네임",
                    example = "홍길동",
                    nullable = true
            )
            String firstPinCreatorNickname,
            @Schema(description = "활성 PIN 개수", example = "3")
            Long pinCount,
            @Schema(description = "인증 사용자의 장소 북마크 여부", example = "false")
            boolean bookmarkedByMe
    ) {
    }

    @Schema(
            name = "PlaceMapSelectionDecisionResponse",
            description = "MAP_SELECTION_CONFIRMED이면 mapSelection만, "
                    + "PLACE_SEARCH_RECOMMENDED이면 recommendedPlace만, "
                    + "PLACE_SEARCH_REQUIRED이면 buildingName만 반환합니다."
    )
    public record MapSelectionResult(
            @Schema(
                    description = "지도 선택 장소 판정 상태",
                    example = "MAP_SELECTION_CONFIRMED",
                    allowableValues = {
                            "MAP_SELECTION_CONFIRMED",
                            "PLACE_SEARCH_RECOMMENDED",
                            "PLACE_SEARCH_REQUIRED"
                    }
            )
            MapSelectionStatus status,
            @Schema(
                    description = "MAP_SELECTION_CONFIRMED일 때만 반환하는 확정 장소",
                    nullable = true
            )
            MapSelection mapSelection,
            @Schema(
                    description = "PLACE_SEARCH_RECOMMENDED일 때만 반환하는 추천 장소",
                    nullable = true
            )
            RecommendedPlace recommendedPlace,
            @Schema(
                    description = "PLACE_SEARCH_REQUIRED일 때만 반환하는 검색 건물명",
                    nullable = true
            )
            String buildingName
    ) {

        public static MapSelectionResult confirmed(Place place) {
            return new MapSelectionResult(
                    MapSelectionStatus.MAP_SELECTION_CONFIRMED,
                    MapSelection.from(place),
                    null,
                    null
            );
        }

        public static MapSelectionResult recommended(Place place, int distanceMeters) {
            return new MapSelectionResult(
                    MapSelectionStatus.PLACE_SEARCH_RECOMMENDED,
                    null,
                    RecommendedPlace.from(place, distanceMeters),
                    null
            );
        }

        public static MapSelectionResult searchRequired(String buildingName) {
            return new MapSelectionResult(
                    MapSelectionStatus.PLACE_SEARCH_REQUIRED,
                    null,
                    null,
                    buildingName
            );
        }
    }

    @Schema(name = "PlaceConfirmedMapSelection")
    public record MapSelection(
            @Schema(description = "장소 ID", example = "12")
            Long placeId,
            @Schema(
                    description = "백엔드가 전체 주소에서 국가명과 시/도명을 제외해 생성한 "
                            + "MAP_SELECTION 화면 표시용 축약 주소",
                    example = "영등포구 여의동로 123-4"
            )
            String placeName,
            @Schema(
                    description = "장소 생성 출처",
                    example = "MAP_SELECTION",
                    type = "string",
                    allowableValues = "MAP_SELECTION"
            )
            PlaceSource source,
            @Schema(description = "장소 위도", example = "37.5283")
            Double latitude,
            @Schema(description = "장소 경도", example = "126.9326")
            Double longitude
    ) {

        public static MapSelection from(Place place) {
            return new MapSelection(
                    place.getId(),
                    place.getName(),
                    place.getSource(),
                    place.getLocation().getY(),
                    place.getLocation().getX()
            );
        }
    }

    @Schema(name = "PlaceRecommendedPlace")
    public record RecommendedPlace(
            @Schema(description = "장소 ID", example = "10")
            Long placeId,
            @Schema(description = "장소명", example = "카카오 판교아지트")
            String placeName,
            @Schema(description = "장소 카테고리", example = "기업", nullable = true)
            String category,
            @Schema(description = "전체 지번 주소", example = "경기도 성남시 분당구 백현동 532")
            String address,
            @Schema(
                    description = "전체 도로명 주소. 없으면 null",
                    example = "경기도 성남시 분당구 판교역로 166",
                    nullable = true
            )
            String roadAddress,
            @Schema(
                    description = "장소 생성 출처",
                    example = "PLACE_SEARCH",
                    type = "string",
                    allowableValues = "PLACE_SEARCH"
            )
            PlaceSource source,
            @Schema(description = "장소 위도", example = "37.3947")
            Double latitude,
            @Schema(description = "장소 경도", example = "127.1112")
            Double longitude,
            @Schema(description = "선택 좌표와 장소 사이 거리(m)", example = "12")
            Integer distanceMeters
    ) {

        public static RecommendedPlace from(Place place, int distanceMeters) {
            return new RecommendedPlace(
                    place.getId(),
                    place.getName(),
                    place.getCategory(),
                    place.getAddress(),
                    place.getRoadAddress(),
                    place.getSource(),
                    place.getLocation().getY(),
                    place.getLocation().getX(),
                    distanceMeters
            );
        }
    }
}
