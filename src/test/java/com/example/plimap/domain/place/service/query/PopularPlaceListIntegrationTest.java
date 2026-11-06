package com.example.plimap.domain.place.service.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.example.plimap.domain.pin.repository.PinRepository;
import com.example.plimap.domain.pin.service.query.PinQueryService;
import com.example.plimap.domain.place.dto.response.PlaceResponse;
import com.example.plimap.domain.place.enums.PopularPlaceScope;
import com.example.plimap.domain.place.repository.PlaceBookmarkRepository;
import com.example.plimap.domain.place.repository.PlaceRepository;
import com.example.plimap.domain.track.repository.PlaceTrackRepository;
import com.example.plimap.domain.track.repository.TrackRepository;
import com.example.plimap.support.PostgisContainerConfiguration;
import com.example.plimap.support.RedisContainerConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Import({PostgisContainerConfiguration.class, RedisContainerConfiguration.class})
@Transactional
class PopularPlaceListIntegrationTest {

    private static final double LATITUDE = 37.5283;
    private static final double LONGITUDE = 126.9326;

    @Autowired
    private PlaceQueryService placeQueryService;

    @Autowired
    private PlaceRepository placeRepository;

    @Autowired
    private PlaceBookmarkRepository placeBookmarkRepository;

    @Autowired
    private PinRepository pinRepository;

    @Autowired
    private PlaceTrackRepository placeTrackRepository;

    @Autowired
    private TrackRepository trackRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private PinQueryService pinQueryService;

    @Test
    void 대표_이미지를_한_번의_배치_조회로_조립하고_조회_전후_데이터를_변경하지_않는다() {
        Long placeId = insertPlace("인기 장소", 100.0);
        Long placeTrackId = insertPlaceTrack(placeId, "https://image/representative");
        insertPin(placeId, placeTrackId, false);
        insertPin(placeId, placeTrackId, true);
        long placeCount = placeRepository.count();
        long bookmarkCount = placeBookmarkRepository.count();
        long pinCount = pinRepository.count();
        long placeTrackCount = placeTrackRepository.count();
        long trackCount = trackRepository.count();

        PlaceResponse.PopularListResult result = placeQueryService.getPopularPlaces(
                1L,
                PopularPlaceScope.NEARBY,
                LATITUDE,
                LONGITUDE
        );

        assertThat(result.items()).containsExactly(
                new PlaceResponse.PopularListItem(
                        placeId,
                        "인기 장소",
                        100,
                        2L,
                        "https://image/representative"
                )
        );
        verify(pinQueryService, times(1))
                .findRepresentativePlaceTracksByPlaceIds(List.of(placeId), 1L);
        assertThat(placeRepository.count()).isEqualTo(placeCount);
        assertThat(placeBookmarkRepository.count()).isEqualTo(bookmarkCount);
        assertThat(pinRepository.count()).isEqualTo(pinCount);
        assertThat(placeTrackRepository.count()).isEqualTo(placeTrackCount);
        assertThat(trackRepository.count()).isEqualTo(trackCount);
    }

    @Test
    void 신고한_PIN이나_회원의_PIN은_장소와_대표_이미지에서_제외한다() {
        Long viewerId = insertMember();
        Long reportedMemberId = insertMember();
        Long visibleMemberId = insertMember();
        Long pinOnlyMemberId = insertMember();
        Long mixedPlaceId = insertPlace("남은 PIN이 있는 장소", 100.0);
        Long reportedTrackId = insertPlaceTrack(
                mixedPlaceId, "https://image/reported", "reported"
        );
        Long visibleTrackId = insertPlaceTrack(
                mixedPlaceId, "https://image/visible", "visible"
        );
        insertPinForMember(mixedPlaceId, reportedTrackId, reportedMemberId, true);
        insertPinForMember(mixedPlaceId, visibleTrackId, visibleMemberId, false);
        jdbcTemplate.update(
                "UPDATE place_track SET like_count = 10 WHERE id = ?", reportedTrackId
        );
        jdbcTemplate.update("""
                INSERT INTO report (reporter_member_id, reported_member_id, category)
                VALUES (?, ?, 'ABUSE_OR_HATE_SPEECH')
                """, viewerId, reportedMemberId);

        Long hiddenPlaceId = insertPlace("신고한 PIN만 있는 장소", 50.0);
        Long hiddenTrackId = insertPlaceTrack(hiddenPlaceId, "https://image/hidden");
        Long hiddenPinId = insertPinForMember(
                hiddenPlaceId, hiddenTrackId, pinOnlyMemberId, true
        );
        jdbcTemplate.update("""
                INSERT INTO report (reporter_member_id, reported_pin_id, category)
                VALUES (?, ?, 'ABUSE_OR_HATE_SPEECH')
                """, viewerId, hiddenPinId);

        PlaceResponse.PopularListResult result = placeQueryService.getPopularPlaces(
                viewerId, PopularPlaceScope.NEARBY, LATITUDE, LONGITUDE
        );

        assertThat(result.items()).containsExactly(
                new PlaceResponse.PopularListItem(
                        mixedPlaceId,
                        "남은 PIN이 있는 장소",
                        100,
                        1L,
                        "https://image/visible"
                )
        );
        verify(pinQueryService, times(1))
                .findRepresentativePlaceTracksByPlaceIds(List.of(mixedPlaceId), viewerId);
    }

    private Long insertMember() {
        return jdbcTemplate.queryForObject(
                "INSERT INTO member DEFAULT VALUES RETURNING id", Long.class
        );
    }

    private Long insertPlace(String name, double distanceMeters) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO place (name, address, source, location)
                VALUES (
                    ?,
                    '지번 주소',
                    'MAP_SELECTION',
                    ST_Project(
                        ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography,
                        ?,
                        radians(0)
                    )
                )
                RETURNING id
                """, Long.class, name, LONGITUDE, LATITUDE, distanceMeters);
    }

    private Long insertPlaceTrack(Long placeId, String albumImageUrl) {
        return insertPlaceTrack(placeId, albumImageUrl, "default");
    }

    private Long insertPlaceTrack(Long placeId, String albumImageUrl, String suffix) {
        Long trackId = jdbcTemplate.queryForObject("""
                INSERT INTO track (
                    provider,
                    provider_track_id,
                    title,
                    artist_name,
                    album_image_url
                )
                VALUES ('TEST', ?, '테스트 곡', '테스트 가수', ?)
                RETURNING id
                """, Long.class, "popular-service-track-" + placeId + "-" + suffix, albumImageUrl);
        return jdbcTemplate.queryForObject("""
                INSERT INTO place_track (place_id, track_id)
                VALUES (?, ?)
                RETURNING id
                """, Long.class, placeId, trackId);
    }

    private void insertPin(Long placeId, Long placeTrackId, boolean feedPublic) {
        insertPinForMember(placeId, placeTrackId, insertMember(), feedPublic);
    }

    private Long insertPinForMember(
            Long placeId, Long placeTrackId, Long memberId, boolean feedPublic
    ) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO pin (
                    member_id,
                    place_id,
                    place_track_id,
                    clip_start_ms,
                    introduction,
                    is_feed_public
                )
                VALUES (?, ?, ?, 0, '', ?)
                RETURNING id
                """, Long.class, memberId, placeId, placeTrackId, feedPublic);
    }
}
