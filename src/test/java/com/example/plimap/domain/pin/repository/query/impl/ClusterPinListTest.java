package com.example.plimap.domain.pin.repository.query.impl;

import com.example.plimap.domain.member.entity.Member;
import com.example.plimap.domain.member.repository.MemberRepository;
import com.example.plimap.domain.pin.dto.response.PinResponse;
import com.example.plimap.domain.pin.entity.Pin;
import com.example.plimap.domain.pin.enums.ClusterLevel;
import com.example.plimap.domain.pin.repository.PinRepository;
import com.example.plimap.domain.pin.repository.query.ClusterAndPinQueryRepository;
import com.example.plimap.domain.pin.repository.query.PinQueryRepository;
import com.example.plimap.domain.place.entity.Place;
import com.example.plimap.domain.place.entity.PlaceBookmark;
import com.example.plimap.domain.place.entity.PlaceSource;
import com.example.plimap.domain.place.repository.PlaceBookmarkRepository;
import com.example.plimap.domain.place.repository.PlaceRepository;
import com.example.plimap.domain.track.dto.AlbumImage;
import com.example.plimap.domain.track.entity.PlaceTrack;
import com.example.plimap.domain.track.entity.Track;
import com.example.plimap.domain.track.repository.PlaceTrackRepository;
import com.example.plimap.domain.track.repository.TrackRepository;
import com.example.plimap.support.PostgisContainerConfiguration;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
@ActiveProfiles("test")
@Import(PostgisContainerConfiguration.class)
@Transactional
class ClusterPinListTest {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PlaceRepository placeRepository;

    @Autowired
    private PinRepository pinRepository;

    @Autowired
    private TrackRepository trackRepository;

    @Autowired
    private PlaceTrackRepository placeTrackRepository;

    @Autowired
    private PlaceBookmarkRepository placeBookmarkRepository;

    @Autowired
    private ClusterAndPinQueryRepository clusterAndPinRepository;

    @Autowired
    EntityManager entityManager;

    Pin pin1, pin2, pin3, pin4, pin5, pin6, pin7,
            pin8, pin9, pin10, pin11, pin12, pin13, pin14, pin15, pin16;
    Place place1, place2, place3, place4, place5, place6, place7, place8, place9;
    Member member1, member2, member3;
    PlaceTrack placeTrack1, placeTrack2, placeTrack3, placeTrack4, placeTrack5, placeTrack6,
            placeTrack7, placeTrack8, placeTrack9, placeTrack10, placeTrack11, placeTrack12;
    PlaceBookmark bookmark1,  bookmark2, bookmark3;
    GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    @Autowired
    private PinQueryRepository pinQueryRepository;

    @BeforeEach
    void setup() {
        member1 = createMember("이서윤", "이서");
        member2 = createMember("홍길동", "동길");
        member3 = createMember("테스트", "테스트");

        // REGION2 : 성남시
        place1 = createPlace(
                "판교역",
                "경기도 성남시 분당구 판교역로",
                "경기도",
                "성남시",
                "백현동",
                127.1112,
                37.3947
        );

        place2 = createPlace(
                "율동공원",
                "경기도 성남시 분당구",
                "경기도",
                "성남시",
                "서현동",
                127.1483,
                37.3820
        );

        // REGION2 : 수원시
        place3 = createPlace(
                "광교호수공원",
                "경기도 수원시 영통구",
                "경기도",
                "수원시",
                "영통구",
                127.0615,
                37.2865
        );

        // REGION1 fallback 테스트용(세종)
        place4 = createPlace(
                "의당면",
                "세종특별자치시 의당면",
                "세종특별자치시",
                null,
                "의당면",
                127.2587,
                36.5593
        );

        // REGION1 fallback 테스트용(세종)
        place5 = createPlace(
                "연서면",
                "세종특별자치시 연서면",
                "세종특별자치시",
                null,
                "연서면",
                127.2810,
                36.5900
        );

        // REGION3 테스트용(서울)
        place6 = createPlace(
                "서울숲",
                "서울특별시 성동구 성수동",
                "서울특별시",
                "성동구",
                "성수동",
                127.0372,
                37.5446
        );

        // REGION3 (부산)
        place7 = createPlace(
                "광안리해수욕장",
                "부산광역시 수영구 광안해변로",
                "부산광역시",
                "수영구",
                "광안동",
                129.1186,
                35.1532
        );

        // geohash 테스트용
        place8 = createPlace(
                "서울숲 카페",
                "...",
                "서울특별시",
                "성동구",
                "성수동",
                127.03745,
                37.54470
        );

        place9 = createPlace(
                "뚝섬역",
                "...",
                "서울특별시",
                "성동구",
                "성수동",
                127.0415,
                37.5470
        );

        Track track1 = Track.create(
                "provider",
                "providerTrackId",
                "title",
                "artist",
                null,
                "album",
                "url_test",
                null
        );

        Track track2 = Track.create(
                "provider2",
                "providerTrackId2",
                "title",
                "artist",
                null,
                "album2",
                "url_test",
                null
        );

        Track track3 = Track.create(
                "provider3",
                "providerTrackId3",
                "title",
                "artist",
                null,
                "album3",
                "url_test",
                null
        );

        placeTrack1 = PlaceTrack.create(place1, track1);
        placeTrack2 = PlaceTrack.create(place2, track1);
        placeTrack3 = PlaceTrack.create(place3, track2);
        placeTrack4 = PlaceTrack.create(place4, track2);
        placeTrack5 = PlaceTrack.create(place5, track3);
        placeTrack6 = PlaceTrack.create(place6, track3); // 좋아요 0개
        placeTrack7 = PlaceTrack.create(place6, track2); // 좋아요 1개
        placeTrack8 = PlaceTrack.create(place6, track1); // 좋아요 2개
        placeTrack9 = PlaceTrack.create(place7, track1); // 좋아요 0개, 핀 2개
        placeTrack10 = PlaceTrack.create(place7, track2); // 좋아요 1개, 핀 1개
        placeTrack11 = PlaceTrack.create(place8, track2);
        placeTrack12 = PlaceTrack.create(place9, track2);

        // 성남시 3개
        pin1 = createPin(member1, place1, placeTrack1);
        pin2 = createPin(member2, place1, placeTrack1);
        pin3 = createPin(member3, place2, placeTrack2);

        // 수원시 2개
        pin4 = createPin(member1, place3, placeTrack3);
        pin5 = createPin(member2, place3, placeTrack3);

        // 세종(의당면) 2개
        pin6 = createPin(member1, place4, placeTrack4);
        pin7 = createPin(member2, place4, placeTrack4);

        // 세종(연서면) 1개
        pin8 = createPin(member3, place5, placeTrack5);

        // 서울 성수동 3개
        pin9 = createPin(member1, place6, placeTrack6);
        pin10 = createPin(member2, place6, placeTrack7);
        pin11 = createPin(member3, place6, placeTrack8);
        pin15 = createPin(member1, place8, placeTrack11);
        pin16 = createPin(member1, place9, placeTrack12);

        // 부산 2개
        pin12 = createPin(member2, place7, placeTrack9);
        pin13 = createPin(member3, place7, placeTrack10);
        pin14 = createPin(member1, place7, placeTrack9);

        memberRepository.saveAll(List.of(member1, member2, member3));
        placeRepository.saveAll(List.of(place1, place2, place3, place4, place5, place6, place7, place8, place9));
        trackRepository.saveAll(List.of(track1, track2, track3));
        placeTrackRepository.saveAll(List.of(placeTrack1, placeTrack2, placeTrack3, placeTrack4, placeTrack5,
                placeTrack6, placeTrack7, placeTrack8, placeTrack9, placeTrack10, placeTrack11, placeTrack12));
        pinRepository.saveAll(List.of(pin1, pin2, pin3, pin4, pin5, pin6, pin7, pin8, pin9, pin10, pin11, pin12, pin13, pin14, pin15, pin16));

        bookmark1 = createPlaceBookmark(place1, member1.getId());
        bookmark2 = createPlaceBookmark(place3, member1.getId());
        bookmark3 = createPlaceBookmark(place4, member1.getId());
        placeBookmarkRepository.saveAll(List.of(bookmark1, bookmark2, bookmark3));

        placeTrack7.increaseLikeCount();
        placeTrack8.increaseLikeCount();
        placeTrack8.increaseLikeCount();
        placeTrack10.increaseLikeCount();

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void 줌레벨이_8이하면_시도_클러스트링을_반환한다() {
        // given
        Point minPoint = geometryFactory.createPoint(
                new Coordinate(126.90, 36.50)
        );

        Point maxPoint = geometryFactory.createPoint(
                new Coordinate(127.35, 37.60)
        );

        // when
        List<PinResponse.Cluster> result = clusterAndPinRepository.findClusterListByViewport(minPoint, maxPoint, 6, member1.getId());

        // then
        assertThat(result).hasSize(3); // 경기, 세종, 서울

        PinResponse.Cluster first = result.getFirst();
        assertThat(first.clusterLevel()).isEqualTo(ClusterLevel.REGION1);
        assertThat(first.regionName()).isEqualTo("경기도");
        assertThat(first.placeCount()).isEqualTo(3);
        assertThat(first.hasBookmarkedPlace()).isTrue();

        assertThat(first.latitude())
                .isCloseTo(37.3544, within(1e-6));
        assertThat(first.longitude())
                .isCloseTo(127.1070, within(1e-6));

        assertThat(first.bounds())
                .extracting(
                        PinResponse.Bound::southWestLat,
                        PinResponse.Bound::southWestLng,
                        PinResponse.Bound::northEastLat,
                        PinResponse.Bound::northEastLng
                )
                .containsExactly(
                        37.2865,
                        127.0615,
                        37.3947,
                        127.1483
                );
    }

    @Test
    void 줌레벨이_9이상_10이하면_시군구_클러스트링을_반환한다() {
        // given
        Point minPoint = geometryFactory.createPoint(
                new Coordinate(127.0615, 37.2865)
        );

        Point maxPoint = geometryFactory.createPoint(
                new Coordinate(127.1483, 37.3947)
        );

        // when
        List<PinResponse.Cluster> result = clusterAndPinRepository.findClusterListByViewport(minPoint, maxPoint, 9, member1.getId());

        // then
        assertThat(result).hasSize(2); // 성남, 수원

        PinResponse.Cluster first = result.getFirst();
        assertThat(first.clusterLevel()).isEqualTo(ClusterLevel.REGION2);
        assertThat(first.regionName()).isEqualTo("경기도 성남시");
        assertThat(first.placeCount()).isEqualTo(2);
        assertThat(first.hasBookmarkedPlace()).isTrue();

        assertThat(first.latitude())
                .isCloseTo(37.38835, within(1e-6));
        assertThat(first.longitude())
                .isCloseTo(127.12975, within(1e-6));

        assertThat(first.bounds())
                .extracting(
                        PinResponse.Bound::southWestLat,
                        PinResponse.Bound::southWestLng,
                        PinResponse.Bound::northEastLat,
                        PinResponse.Bound::northEastLng
                )
                .containsExactly(
                        37.3820,
                        127.1112,
                        37.3947,
                        127.1483
                );
    }

    @Test
    void 중간리전이_null이면_하위_레벨을_반환한다() {
        // given
        Point minPoint = geometryFactory.createPoint(
                new Coordinate(127.24, 36.54)
        );

        Point maxPoint = geometryFactory.createPoint(
                new Coordinate(127.30, 36.61)
        );

        // when
        List<PinResponse.Cluster> result = clusterAndPinRepository.findClusterListByViewport(minPoint, maxPoint, 9, member1.getId());

        // then
        assertThat(result).hasSize(2); // 의당면, 안서면

        assertThat(result)
                .extracting(PinResponse.Cluster::regionName)
                .containsExactlyInAnyOrder(
                        "세종특별자치시 의당면",
                        "세종특별자치시 연서면"
                );

        PinResponse.Cluster first = result.stream()
                .filter(c -> c.regionName().equals("세종특별자치시 의당면"))
                .findFirst()
                .orElseThrow();
        assertThat(first.clusterLevel()).isEqualTo(ClusterLevel.REGION3);
        assertThat(first.placeCount()).isEqualTo(1);
        assertThat(first.hasBookmarkedPlace()).isTrue();

        PinResponse.Cluster last = result.stream()
                .filter(c -> c.regionName().equals("세종특별자치시 연서면"))
                .findFirst()
                .orElseThrow();
        assertThat(last.clusterLevel()).isEqualTo(ClusterLevel.REGION3);
        assertThat(last.placeCount()).isEqualTo(1);
        assertThat(last.hasBookmarkedPlace()).isFalse();
    }

    @Test
    void 사용자_id가_null이면_hasBookmarkedPlace는_false로_반환한다() {
        // given
        Point minPoint = geometryFactory.createPoint(
                new Coordinate(127.24, 36.54)
        );

        Point maxPoint = geometryFactory.createPoint(
                new Coordinate(127.30, 36.61)
        );

        Point minPoint2 = geometryFactory.createPoint(
                new Coordinate(127.0315, 37.5395)
        );

        Point maxPoint2 = geometryFactory.createPoint(
                new Coordinate(127.0429, 37.5497)
        );

        // when
        List<PinResponse.Cluster> result = clusterAndPinRepository.findClusterListByViewport(minPoint, maxPoint, 9, null);
        PinResponse.ClusterAndPin result2 = clusterAndPinRepository.findGeohashClusterListByViewport(minPoint2, maxPoint2, 15, 7, null);

        // then
        assertThat(result.getFirst().hasBookmarkedPlace()).isFalse();
        assertThat(result.getLast().hasBookmarkedPlace()).isFalse();
        assertThat(result2.clusters().getFirst().hasBookmarkedPlace()).isFalse();
    }

    @Test
    void 줌레벨이_11이상_13이하면_읍면동_클러스트링을_반환한다() {
        // given
        Point minPoint = geometryFactory.createPoint(
                new Coordinate(127.1112, 37.3820)
        );

        Point maxPoint = geometryFactory.createPoint(
                new Coordinate(127.1483, 37.3947)
        );

        // when
        List<PinResponse.Cluster> result = clusterAndPinRepository.findClusterListByViewport(minPoint, maxPoint, 12, member1.getId());

        // then
        assertThat(result).hasSize(2); // 백현동, 서현동

        PinResponse.Cluster first = result.getFirst();
        assertThat(first.clusterLevel()).isEqualTo(ClusterLevel.REGION3);
        assertThat(first.regionName()).isEqualTo("경기도 성남시 백현동");
        assertThat(first.placeCount()).isEqualTo(1);
        assertThat(first.hasBookmarkedPlace()).isTrue();

        assertThat(first.latitude())
                .isCloseTo(37.3947, within(1e-6));
        assertThat(first.longitude())
                .isCloseTo(127.1112, within(1e-6));

        assertThat(first.bounds())
                .extracting(
                        PinResponse.Bound::southWestLat,
                        PinResponse.Bound::southWestLng,
                        PinResponse.Bound::northEastLat,
                        PinResponse.Bound::northEastLng
                )
                .containsExactly(
                        37.3947,
                        127.1112,
                        37.3947,
                        127.1112
                );
    }

    @Test
    void 줌레벨이_14이상_16이하이면_precision7의_geohash클러스트링과_개별핀을_반환한다() {
        // given
        Point minPoint = geometryFactory.createPoint(
                new Coordinate(127.0315, 37.5395)
        );

        Point maxPoint = geometryFactory.createPoint(
                new Coordinate(127.0429, 37.5497)
        );

        // when
        PinResponse.ClusterAndPin result = clusterAndPinRepository.findGeohashClusterListByViewport(minPoint, maxPoint, 15, 7, member1.getId());
        List<PinResponse.Cluster> clusters = result.clusters();
        List<PinResponse.PinPreview> pinPreviews = result.pins();

        // then
        assertThat(clusters).hasSize(1);
        assertThat(clusters.getFirst().placeCount()).isEqualTo(2); // place6, place8
        assertThat(pinPreviews).hasSize(1); // place9
        assertThat(pinPreviews.getFirst().placeId()).isEqualTo(place9.getId());
    }

    @Test
    void 줌레벨이_17이상_19이하이면_precision8의_geohash클러스트링과_개별핀을_반환한다() {
        // given
        Point minPoint = geometryFactory.createPoint(
                new Coordinate(127.0365, 37.5440)
        );

        Point maxPoint = geometryFactory.createPoint(
                new Coordinate(127.0390, 37.5460)
        );

        // when
        PinResponse.ClusterAndPin result = clusterAndPinRepository.findGeohashClusterListByViewport(minPoint, maxPoint, 18, 8, member1.getId());
        List<PinResponse.Cluster> clusters = result.clusters();
        List<PinResponse.PinPreview> pinPreviews = result.pins();

        // then
        assertThat(clusters).hasSize(0);
        assertThat(pinPreviews).hasSize(2); // place6, place8
        assertThat(pinPreviews)
                .extracting(PinResponse.PinPreview::placeId)
                .containsExactlyInAnyOrder(place6.getId(), place8.getId());
        assertThat(pinPreviews.getFirst().hasBookmarkedPlace()).isFalse();
        assertThat(pinPreviews.getLast().hasBookmarkedPlace()).isFalse();
    }

    @Test
    void 줌레벨이_20이상이면_핀목록을_반환한다() {
        // given
        Point minPoint = geometryFactory.createPoint(
                new Coordinate(127.11, 37.38)
        );

        Point maxPoint = geometryFactory.createPoint(
                new Coordinate(127.15, 37.40)
        );

        // when
        List<PinResponse.PinPreview> result = clusterAndPinRepository.findPinPreviewListByViewport(minPoint, maxPoint, member1.getId());

        // then
        assertThat(result).hasSize(2);
        assertThat(result)
                .extracting(PinResponse.PinPreview::placeId)
                .containsExactlyInAnyOrder(place1.getId(), place2.getId());

        assertThat(result)
                .filteredOn(pin -> pin.placeId().equals(place1.getId()))
                .extracting(PinResponse.PinPreview::hasBookmarkedPlace)
                .containsExactly(true);

        assertThat(result)
                .filteredOn(pin -> pin.placeId().equals(place2.getId()))
                .extracting(PinResponse.PinPreview::hasBookmarkedPlace)
                .containsExactly(false);

        assertThat(result)
                .filteredOn(pin -> pin.placeId().equals(place2.getId()))
                .extracting(PinResponse.PinPreview::writerId)
                .containsExactly(pin3.getMember().getId());
    }

    @Test
    void 트랙이_다른_핀이_여러개일_시_좋아요가_가장_높은것을_반환한다() {
        // given
        Point minPoint = geometryFactory.createPoint(
                new Coordinate(127.0250, 37.5300)
        );

        Point maxPoint = geometryFactory.createPoint(
                new Coordinate(127.0750, 37.5600)
        );

        // when
        List<PinResponse.PinPreview> result = clusterAndPinRepository.findPinPreviewListByViewport(minPoint, maxPoint, member1.getId());

        // then
        assertThat(result).hasSize(3);
        PinResponse.PinPreview first = result.getFirst();
        assertThat(first.albumImageUrl()).isEqualTo("album");
    }

    @Test
    void 삭제처리된_핀은_제외하고_가장_높은것을_반환한다() {
        // given
        Point minPoint = geometryFactory.createPoint(
                new Coordinate(127.0250, 37.5300)
        );

        Point maxPoint = geometryFactory.createPoint(
                new Coordinate(127.0750, 37.5600)
        );
        // when
        List<PinResponse.PinPreview> result = clusterAndPinRepository.findPinPreviewListByViewport(minPoint, maxPoint, member1.getId());

        // then
        assertThat(result).hasSize(3);
        PinResponse.PinPreview first = result.getFirst();
        assertThat(first.albumImageUrl()).isEqualTo("album");

        // when
        Pin managedPin = pinRepository.findById(pin11.getId()).orElseThrow();

        managedPin.delete(); // 좋아요가 가장 높은 핀 삭제
        entityManager.flush();
        entityManager.clear();

        List<PinResponse.PinPreview> result2 = clusterAndPinRepository.findPinPreviewListByViewport(minPoint, maxPoint, member1.getId());

        // then
        assertThat(result2).hasSize(3);
        PinResponse.PinPreview first2 = result2.getFirst();
        assertThat(first2.albumImageUrl()).isEqualTo("album2");
    }

    // findRepresentativePlaceTracksByPlaceIds
    @Test
    void 장소별_대표_PlaceTrack은_좋아요가_가장_많은_트랙을_선택한다() {
        // given
        List<Long> placeIds = new ArrayList<>(List.of(place6.getId(), place5.getId()));

        // when
        Map<Long, AlbumImage> result = pinQueryRepository.findRepresentativePlaceTracksByPlaceIds(placeIds, 1L);

        // then
        assertThat(result.size()).isEqualTo(2);
        assertThat(result.get(place6.getId()).albumImageUrl())
                .isEqualTo(placeTrack8.getTrack().getAlbumImageUrl());
        assertThat(result.get(place5.getId()).albumImageUrl())
                .isEqualTo(placeTrack5.getTrack().getAlbumImageUrl());
    }

    @Test
    void 삭제된_핀만_존재하는_PlaceTrack은_대표에서_제외한다() {
        // given
        List<Long> placeIds = new ArrayList<>(List.of(place6.getId(), place7.getId()));

        // when
        Map<Long, AlbumImage> result = pinQueryRepository.findRepresentativePlaceTracksByPlaceIds(placeIds, 1L);

        // then
        assertThat(result.get(place7.getId()).albumImageUrl())
                .isEqualTo(placeTrack10.getTrack().getAlbumImageUrl());

        // when
        Pin managedPin = pinRepository.findById(pin13.getId()).orElseThrow();

        managedPin.delete();
        entityManager.flush();
        entityManager.clear();

        Map<Long, AlbumImage> result2 = pinQueryRepository.findRepresentativePlaceTracksByPlaceIds(placeIds, 1L);

        // then
        assertThat(result2.get(place7.getId()).albumImageUrl())
                .isEqualTo(placeTrack9.getTrack().getAlbumImageUrl());
    }

    @Test
    void 좋아요_수가_같으면_핀_개수_기준으로_반환한다() {
        // given
        List<Long> placeIds = new ArrayList<>(List.of(place6.getId(), place7.getId()));

        // when
        PlaceTrack managedPlaceTrack =
                placeTrackRepository.findById(placeTrack9.getId()).orElseThrow();
        managedPlaceTrack.increaseLikeCount();

        entityManager.flush();
        entityManager.clear();
        Map<Long, AlbumImage> result = pinQueryRepository.findRepresentativePlaceTracksByPlaceIds(placeIds, 1L);

        // then
        assertThat(result.get(place7.getId()).albumImageUrl())
                .isEqualTo(placeTrack9.getTrack().getAlbumImageUrl());
    }

    private Member createMember(String name, String nickname) {
        return Member.builder()
                .name(name)
                .nickname(nickname)
                .introduction("안녕하세요")
                .profileImageObjectKey("members/12/b7c277d4-31d3-470b-8bb7-ec89c114016c.webp")
                .build();
    }

    private Place createPlace(String name, String address, String sido, String sigungu, String eupMyeonDong, double lng, double lat) {
        return Place.builder()
                .name(name)
                .address(address)
                .sido(sido)
                .sigungu(sigungu)
                .eupMyeonDong(eupMyeonDong)
                .source(PlaceSource.MAP_SELECTION)
                .location(geometryFactory.createPoint(new Coordinate(lng, lat)))
                .build();
    }

    private Pin createPin(Member member, Place place, PlaceTrack placeTrack) {
        return createPin(member, place, placeTrack, true);
    }

    private Pin createPin(Member member, Place place, PlaceTrack placeTrack, boolean feedPublic) {
        return Pin.builder()
                .member(member)
                .place(place)
                .placeTrack(placeTrack)
                .clipStartMs(70000)
                .introduction("good")
                .isFeedPublic(feedPublic)
                .build();
    }

    private PlaceBookmark createPlaceBookmark(Place place, Long memberId) {
        return PlaceBookmark.builder()
                .place(place)
                .memberId(memberId)
                .build();
    }
}
