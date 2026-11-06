package com.example.plimap.domain.place.repository.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.example.plimap.domain.place.dto.PopularPlaceCandidate;
import com.example.plimap.support.PostgisContainerConfiguration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Import(PostgisContainerConfiguration.class)
@Transactional
class PopularPlaceQueryRepositoryIntegrationTest {

    private static final double LATITUDE = 37.5283;
    private static final double LONGITUDE = 126.9326;

    @Autowired
    private PopularPlaceQueryRepository popularPlaceQueryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void NEARBY는_500m_제한_없이_최근접_활성_PIN_장소를_조회한다() {
        Long boundaryId = insertPlace("경계 장소", 500.0, 0.0, false);
        Long outsideId = insertPlace("경계 밖", 500.01, 0.0, false);
        Long noActivePinId = insertPlace("활성 PIN 없음", 100.0, 0.0, false);
        Long deletedPlaceId = insertPlace("삭제 장소", 50.0, 0.0, true);
        insertPins(boundaryId, 1, false, false);
        insertPins(outsideId, 1, true, false);
        insertPins(noActivePinId, 1, true, true);
        insertPins(deletedPlaceId, 1, true, false);

        List<PopularPlaceCandidate> result =
                popularPlaceQueryRepository.findNearbyPopularPlaces(
                        1L,
                        LATITUDE,
                        LONGITUDE
                );

        assertThat(distanceFrom(boundaryId)).isCloseTo(500.0, within(0.001));
        assertThat(distanceFrom(outsideId)).isGreaterThan(500.0);
        assertThat(result).hasSize(2);
        assertThat(result).extracting(PopularPlaceCandidate::placeId)
                .containsExactly(boundaryId, outsideId);
        assertThat(result.getFirst().pinCount()).isEqualTo(1L);
        assertThat(result.getFirst().distanceMeters()).isCloseTo(500.0, within(0.001));
    }

    @Test
    void NEARBY는_반올림_전_실제거리_PIN수_placeId순으로_정렬한다() {
        Long roundedFartherId = insertPlace("100.4m", 100.4, 0.0, false);
        Long roundedNearerId = insertPlace("100.1m", 100.1, 0.0, false);
        Long morePinsId = insertPlace("동거리 PIN 2개", 200.0, 90.0, false);
        Long fewerPinsId = insertPlace("동거리 PIN 1개", 200.0, 90.0, false);
        Long firstId = insertPlace("ID 우선 1", 300.0, 180.0, false);
        Long secondId = insertPlace("ID 우선 2", 300.0, 180.0, false);
        insertPins(roundedFartherId, 1, true, false);
        insertPins(roundedNearerId, 1, true, false);
        insertPins(morePinsId, 2, true, false);
        insertPins(fewerPinsId, 1, true, false);
        insertPins(firstId, 1, true, false);
        insertPins(secondId, 1, true, false);

        List<PopularPlaceCandidate> result =
                popularPlaceQueryRepository.findNearbyPopularPlaces(
                        1L,
                        LATITUDE,
                        LONGITUDE
                );

        assertThat(Math.round(distanceFrom(roundedNearerId)))
                .isEqualTo(Math.round(distanceFrom(roundedFartherId)));
        assertThat(distanceFrom(roundedNearerId)).isLessThan(distanceFrom(roundedFartherId));
        assertThat(result).extracting(PopularPlaceCandidate::placeId)
                .containsExactly(
                        roundedNearerId,
                        roundedFartherId,
                        morePinsId,
                        fewerPinsId,
                        firstId,
                        secondId
                );
    }

    @Test
    void GLOBAL은_PIN수_실제거리_placeId순으로_정렬한다() {
        Long mostPinsId = insertPlace("PIN 3개", 400.0, 0.0, false);
        Long tiedFarId = insertPlace("PIN 2개 멂", 100.4, 0.0, false);
        Long tiedNearId = insertPlace("PIN 2개 가까움", 100.1, 0.0, false);
        Long firstId = insertPlace("ID 우선 1", 500.0, 90.0, false);
        Long secondId = insertPlace("ID 우선 2", 500.0, 90.0, false);
        Long deletedPlaceId = insertPlace("삭제 장소", 10.0, 0.0, true);
        Long deletedPinOnlyPlaceId = insertPlace("삭제 PIN만 있는 장소", 20.0, 0.0, false);
        insertPins(mostPinsId, 3, false, false);
        insertPins(tiedNearId, 2, false, false);
        insertPins(tiedFarId, 2, true, false);
        insertPins(firstId, 1, true, false);
        insertPins(secondId, 1, false, false);
        insertPins(deletedPlaceId, 4, true, false);
        insertPins(deletedPinOnlyPlaceId, 4, true, true);

        List<PopularPlaceCandidate> result =
                popularPlaceQueryRepository.findGlobalPopularPlaces(
                        1L,
                        LATITUDE,
                        LONGITUDE
                );

        assertThat(Math.round(distanceFrom(tiedNearId)))
                .isEqualTo(Math.round(distanceFrom(tiedFarId)));
        assertThat(distanceFrom(tiedNearId)).isLessThan(distanceFrom(tiedFarId));
        assertThat(result).extracting(PopularPlaceCandidate::placeId)
                .containsExactly(mostPinsId, tiedNearId, tiedFarId, firstId, secondId);
        assertThat(result).extracting(PopularPlaceCandidate::placeId)
                .doesNotContain(deletedPlaceId, deletedPinOnlyPlaceId);
        assertThat(result).extracting(PopularPlaceCandidate::pinCount)
                .containsExactly(3L, 2L, 2L, 1L, 1L);
    }

    @Test
    void 행정구역_단계별로_조건에_맞는_장소만_새로_조회한다() {
        Long region3Id = insertPlace(
                "역삼1동", 100.0, "1168010100", "서울특별시", "강남구", "역삼1동"
        );
        Long region2Id = insertPlace(
                "삼성1동", 200.0, "1168058000", "서울특별시", "강남구", "삼성1동"
        );
        Long region1Id = insertPlace(
                "여의동", 300.0, "1156054000", "서울특별시", "영등포구", "여의동"
        );
        Long globalId = insertPlace(
                "해운대", 400.0, "2635051000", "부산광역시", "해운대구", "우1동"
        );
        insertPins(region3Id, 1, true, false);
        insertPins(region2Id, 1, true, false);
        insertPins(region1Id, 1, true, false);
        insertPins(globalId, 1, true, false);

        List<PopularPlaceCandidate> region3 =
                popularPlaceQueryRepository.findRegion3PopularPlaces(
                        1L,
                        "1168010100", LATITUDE, LONGITUDE
                );
        List<PopularPlaceCandidate> region2 =
                popularPlaceQueryRepository.findRegion2PopularPlaces(
                        1L,
                        "서울특별시", "강남구", LATITUDE, LONGITUDE
                );
        List<PopularPlaceCandidate> region1 =
                popularPlaceQueryRepository.findRegion1PopularPlaces(
                        1L,
                        "서울특별시", LATITUDE, LONGITUDE
                );

        assertThat(region3).extracting(PopularPlaceCandidate::placeId)
                .containsExactly(region3Id);
        assertThat(region2).extracting(PopularPlaceCandidate::placeId)
                .containsExactly(region3Id, region2Id);
        assertThat(region1).extracting(PopularPlaceCandidate::placeId)
                .containsExactly(region3Id, region2Id, region1Id);
    }

    @Test
    void 각_scope는_DB에서_정렬한_뒤_최대_6개만_반환한다() {
        List<Long> idsByDistance = new ArrayList<>();
        for (int index = 1; index <= 7; index++) {
            Long placeId = insertPlace("장소 " + index, index * 10.0, 0.0, false);
            idsByDistance.add(placeId);
            insertPins(placeId, 1, true, false);
        }

        List<PopularPlaceCandidate> nearby =
                popularPlaceQueryRepository.findNearbyPopularPlaces(
                        1L,
                        LATITUDE,
                        LONGITUDE
                );
        List<PopularPlaceCandidate> global =
                popularPlaceQueryRepository.findGlobalPopularPlaces(
                        1L,
                        LATITUDE,
                        LONGITUDE
                );
        List<PopularPlaceCandidate> region3 =
                popularPlaceQueryRepository.findRegion3PopularPlaces(
                        1L,
                        "1168010100",
                        LATITUDE,
                        LONGITUDE
                );
        List<PopularPlaceCandidate> region2 =
                popularPlaceQueryRepository.findRegion2PopularPlaces(
                        1L,
                        "서울특별시",
                        "강남구",
                        LATITUDE,
                        LONGITUDE
                );
        List<PopularPlaceCandidate> region1 =
                popularPlaceQueryRepository.findRegion1PopularPlaces(
                        1L,
                        "서울특별시",
                        LATITUDE,
                        LONGITUDE
                );

        assertThat(nearby).hasSize(6);
        assertThat(global).hasSize(6);
        assertThat(region3).hasSize(6);
        assertThat(region2).hasSize(6);
        assertThat(region1).hasSize(6);
        assertThat(nearby).extracting(PopularPlaceCandidate::placeId)
                .containsExactlyElementsOf(idsByDistance.subList(0, 6));
        assertThat(global).extracting(PopularPlaceCandidate::placeId)
                .containsExactlyElementsOf(idsByDistance.subList(0, 6));
        assertThat(region3).extracting(PopularPlaceCandidate::placeId)
                .containsExactlyElementsOf(idsByDistance.subList(0, 6));
        assertThat(region2).extracting(PopularPlaceCandidate::placeId)
                .containsExactlyElementsOf(idsByDistance.subList(0, 6));
        assertThat(region1).extracting(PopularPlaceCandidate::placeId)
                .containsExactlyElementsOf(idsByDistance.subList(0, 6));
    }

    @Test
    void 신고한_PIN이나_회원의_PIN은_모든_인기_장소_범위에서_제외한다() {
        Long viewerId = insertMember();
        Long otherViewerId = insertMember();
        Long pinReportedOwnerId = insertMember();
        Long memberReportedOwnerId = insertMember();
        Long visibleOwnerId = insertMember();

        Long pinReportedPlaceId = insertPlace("신고한 PIN만 있는 장소", 10.0, 0.0, false);
        Long memberReportedPlaceId = insertPlace("신고한 회원 PIN만 있는 장소", 20.0, 0.0, false);
        Long mixedPlaceId = insertPlace("볼 수 있는 PIN도 있는 장소", 30.0, 0.0, false);
        Long sameOwnerPlaceId = insertPlace("같은 작성자의 다른 PIN", 40.0, 0.0, false);
        Long reportedPinId = insertPinForMember(pinReportedPlaceId, pinReportedOwnerId);
        insertPinForMember(memberReportedPlaceId, memberReportedOwnerId);
        insertPinForMember(mixedPlaceId, memberReportedOwnerId);
        insertPinForMember(mixedPlaceId, visibleOwnerId);
        insertPinForMember(sameOwnerPlaceId, pinReportedOwnerId);
        insertPinReport(viewerId, reportedPinId);
        insertMemberReport(viewerId, memberReportedOwnerId);

        List<List<PopularPlaceCandidate>> viewerResults = List.of(
                popularPlaceQueryRepository.findNearbyPopularPlaces(
                        viewerId, LATITUDE, LONGITUDE
                ),
                popularPlaceQueryRepository.findRegion3PopularPlaces(
                        viewerId, "1168010100", LATITUDE, LONGITUDE
                ),
                popularPlaceQueryRepository.findRegion2PopularPlaces(
                        viewerId, "서울특별시", "강남구", LATITUDE, LONGITUDE
                ),
                popularPlaceQueryRepository.findRegion1PopularPlaces(
                        viewerId, "서울특별시", LATITUDE, LONGITUDE
                ),
                popularPlaceQueryRepository.findGlobalPopularPlaces(
                        viewerId, LATITUDE, LONGITUDE
                )
        );

        for (List<PopularPlaceCandidate> result : viewerResults) {
            assertThat(result).extracting(PopularPlaceCandidate::placeId)
                    .containsExactlyInAnyOrder(mixedPlaceId, sameOwnerPlaceId)
                    .doesNotContain(pinReportedPlaceId, memberReportedPlaceId);
            assertThat(result).extracting(PopularPlaceCandidate::pinCount)
                    .containsOnly(1L);
        }

        List<PopularPlaceCandidate> otherViewerResults =
                popularPlaceQueryRepository.findGlobalPopularPlaces(
                        otherViewerId, LATITUDE, LONGITUDE
                );
        assertThat(otherViewerResults).extracting(PopularPlaceCandidate::placeId)
                .contains(pinReportedPlaceId, memberReportedPlaceId, mixedPlaceId, sameOwnerPlaceId);
        assertThat(otherViewerResults).filteredOn(
                candidate -> candidate.placeId().equals(mixedPlaceId)
        ).singleElement().extracting(PopularPlaceCandidate::pinCount).isEqualTo(2L);
    }

    @Test
    void 신고_제외_후에_정렬과_최대_6개_제한을_적용한다() {
        Long viewerId = insertMember();
        Long ownerId = insertMember();
        Long hiddenPlaceId = insertPlace("숨길 가장 가까운 장소", 10.0, 0.0, false);
        Long hiddenPinId = insertPinForMember(hiddenPlaceId, ownerId);
        insertPinReport(viewerId, hiddenPinId);

        List<Long> visiblePlaceIds = new ArrayList<>();
        for (int index = 1; index <= 7; index++) {
            Long placeId = insertPlace("보이는 장소 " + index, index * 20.0, 0.0, false);
            visiblePlaceIds.add(placeId);
            insertPinForMember(placeId, ownerId);
        }

        List<PopularPlaceCandidate> result =
                popularPlaceQueryRepository.findNearbyPopularPlaces(
                        viewerId, LATITUDE, LONGITUDE
                );

        assertThat(result).hasSize(6);
        assertThat(result).extracting(PopularPlaceCandidate::placeId)
                .containsExactlyElementsOf(visiblePlaceIds.subList(0, 6));
    }

    @Test
    void 신고가_10회_누적된_PIN만_있는_장소는_추천하지_않는다() {
        Long viewerId = insertMember();
        Long ownerId = insertMember();
        Long placeId = insertPlace("자동 숨김 PIN만 있는 장소", 10.0, 0.0, false);
        Long pinId = insertPinForMember(placeId, ownerId);
        jdbcTemplate.update("UPDATE pin SET report_count = 10 WHERE id = ?", pinId);

        List<PopularPlaceCandidate> result =
                popularPlaceQueryRepository.findGlobalPopularPlaces(
                        viewerId, LATITUDE, LONGITUDE
                );

        assertThat(result).isEmpty();
    }

    private Long insertMember() {
        return jdbcTemplate.queryForObject(
                "INSERT INTO member DEFAULT VALUES RETURNING id", Long.class
        );
    }

    private Long insertPinForMember(Long placeId, Long memberId) {
        Long trackId = jdbcTemplate.queryForObject("""
                INSERT INTO track (provider, provider_track_id, title, artist_name)
                VALUES ('TEST', ?, '테스트 곡', '테스트 가수')
                RETURNING id
                """, Long.class, "popular-reported-" + placeId + "-" + memberId);
        Long placeTrackId = jdbcTemplate.queryForObject("""
                INSERT INTO place_track (place_id, track_id)
                VALUES (?, ?)
                RETURNING id
                """, Long.class, placeId, trackId);
        return jdbcTemplate.queryForObject("""
                INSERT INTO pin (member_id, place_id, place_track_id, clip_start_ms, introduction)
                VALUES (?, ?, ?, 0, '')
                RETURNING id
                """, Long.class, memberId, placeId, placeTrackId);
    }

    private void insertPinReport(Long reporterId, Long pinId) {
        jdbcTemplate.update("""
                INSERT INTO report (reporter_member_id, reported_pin_id, category)
                VALUES (?, ?, 'ABUSE_OR_HATE_SPEECH')
                """, reporterId, pinId);
    }

    private void insertMemberReport(Long reporterId, Long memberId) {
        jdbcTemplate.update("""
                INSERT INTO report (reporter_member_id, reported_member_id, category)
                VALUES (?, ?, 'ABUSE_OR_HATE_SPEECH')
                """, reporterId, memberId);
    }

    private Long insertPlace(
            String name,
            double distanceMeters,
            double bearingDegrees,
            boolean deleted
    ) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO place (
                    name,
                    address,
                    source,
                    location,
                    administrative_region_code,
                    sido,
                    sigungu,
                    eup_myeon_dong,
                    deleted_at
                )
                VALUES (
                    ?,
                    '지번 주소',
                    'MAP_SELECTION',
                    ST_Project(
                        ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography,
                        ?,
                        radians(?)
                    ),
                    '1168010100',
                    '서울특별시',
                    '강남구',
                    '역삼1동',
                    CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE NULL END
                )
                RETURNING id
                """, Long.class,
                name,
                LONGITUDE,
                LATITUDE,
                distanceMeters,
                bearingDegrees,
                deleted);
    }

    private Long insertPlace(
            String name,
            double distanceMeters,
            String administrativeRegionCode,
            String sido,
            String sigungu,
            String eupMyeonDong
    ) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO place (
                    name,
                    address,
                    source,
                    location,
                    administrative_region_code,
                    sido,
                    sigungu,
                    eup_myeon_dong
                )
                VALUES (
                    ?,
                    '지번 주소',
                    'MAP_SELECTION',
                    ST_Project(
                        ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography,
                        ?,
                        radians(0)
                    ),
                    ?, ?, ?, ?
                )
                RETURNING id
                """, Long.class,
                name,
                LONGITUDE,
                LATITUDE,
                distanceMeters,
                administrativeRegionCode,
                sido,
                sigungu,
                eupMyeonDong);
    }

    private void insertPins(
            Long placeId,
            int count,
            boolean feedPublic,
            boolean deleted
    ) {
        Long trackId = jdbcTemplate.queryForObject("""
                INSERT INTO track (provider, provider_track_id, title, artist_name)
                VALUES ('TEST', ?, '테스트 곡', '테스트 가수')
                RETURNING id
                """, Long.class, "popular-track-" + placeId);
        Long placeTrackId = jdbcTemplate.queryForObject("""
                INSERT INTO place_track (place_id, track_id)
                VALUES (?, ?)
                RETURNING id
                """, Long.class, placeId, trackId);

        for (int index = 0; index < count; index++) {
            Long memberId = jdbcTemplate.queryForObject(
                    "INSERT INTO member DEFAULT VALUES RETURNING id",
                    Long.class
            );
            jdbcTemplate.update("""
                    INSERT INTO pin (
                        member_id,
                        place_id,
                        place_track_id,
                        clip_start_ms,
                        introduction,
                        is_feed_public,
                        deleted_at
                    )
                    VALUES (
                        ?, ?, ?, 0, '', ?,
                        CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE NULL END
                    )
                    """, memberId, placeId, placeTrackId, feedPublic, deleted);
        }
    }

    private Double distanceFrom(Long placeId) {
        return jdbcTemplate.queryForObject("""
                SELECT ST_Distance(
                    location,
                    ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography
                )
                FROM place
                WHERE id = ?
                """, Double.class, LONGITUDE, LATITUDE, placeId);
    }
}
