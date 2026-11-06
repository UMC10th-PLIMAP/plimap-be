package com.example.plimap.domain.pin.service.query.impl;

import com.example.plimap.domain.member.entity.Member;
import com.example.plimap.domain.member.service.query.MemberQueryService;
import com.example.plimap.domain.pin.dto.Pagination;
import com.example.plimap.domain.pin.dto.PlaceAccessToken;
import com.example.plimap.domain.pin.dto.PlacePinInfo;
import com.example.plimap.domain.pin.dto.request.PinRequest;
import com.example.plimap.domain.pin.dto.response.PinResponse;
import com.example.plimap.domain.pin.entity.Pin;
import com.example.plimap.domain.pin.entity.Tag;
import com.example.plimap.domain.pin.enums.AvailabilityStatus;
import com.example.plimap.domain.pin.enums.PinSortType;
import com.example.plimap.domain.pin.exception.PinErrorCode;
import com.example.plimap.domain.pin.exception.PinException;
import com.example.plimap.domain.pin.repository.PinLikeRepository;
import com.example.plimap.domain.pin.repository.PinRepository;
import com.example.plimap.domain.pin.repository.PlaceAccessTokenRepository;
import com.example.plimap.domain.pin.repository.query.ClusterAndPinQueryRepository;
import com.example.plimap.domain.pin.repository.query.PinQueryRepository;
import com.example.plimap.domain.pin.validator.PinLocationValidator;
import com.example.plimap.domain.place.entity.Place;
import com.example.plimap.domain.place.entity.PlaceSource;
import com.example.plimap.domain.track.dto.AlbumImage;
import com.example.plimap.domain.track.entity.PlaceTrack;
import com.example.plimap.domain.track.entity.Track;
import com.example.plimap.domain.track.service.query.PlaceTrackFinder;
import com.example.plimap.domain.track.service.query.PlaceTrackLikeQueryService;
import com.example.plimap.global.external.storage.ProfileImageStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PinQueryServiceImplTest {

    @InjectMocks
    private PinQueryServiceImpl pinQueryService;

    @Mock
    private PinQueryRepository pinQueryRepository;

    @Mock
    private PinRepository pinRepository;

    @Mock
    private PinLikeRepository pinLikeRepository;

    @Mock
    private ProfileImageStorage profileImageStorage;

    @Mock
    private PlaceTrackFinder placeTrackFinder;

    @Mock
    private MemberQueryService memberQueryService;

    @Mock
    private PlaceTrackLikeQueryService placeTrackLikeQueryService;

    @Mock
    private PlaceAccessTokenRepository placeAccessTokenRepository;

    @Mock
    private ClusterAndPinQueryRepository clusterAndPinRepository;

    @Spy
    private PinLocationValidator pinLocationValidator = new PinLocationValidator();

    Member member, member2;
    Place place;
    Tag tag1, tag2, tag3, tag4, tag5;
    PlaceTrack placeTrack;
    Pin pin;
    PinRequest.UserLocation request = PinRequest.UserLocation.builder()
            .userLatitude(37.123)
            .userLongitude(127.123)
            .build();

    Pagination<PinResponse.PinDetail> pagination =
            Pagination.<PinResponse.PinDetail>builder()
                    .data(List.of())
                    .nextCursor(null)
                    .hasNext(false)
                    .pageSize(10)
                    .build();

    GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    @BeforeEach
    void setup() {
        member = Member.builder()
                .name("이서윤")
                .nickname("이서")
                .introduction("안녕하세요")
                .profileImageObjectKey("members/12/b7c277d4-31d3-470b-8bb7-ec89c114016c.web")
                .build();

        member2 = Member.builder()
                .name("홍길동")
                .nickname("동길")
                .introduction("안녕하세요")
                .profileImageObjectKey("members/12/b7c277d4-31d3-470b-8bb7-ec89c114016c.web")
                .build();

        Point point = geometryFactory.createPoint(
                new Coordinate(127.020001338463, 37.5265858245219)
        );
        place = Place.builder()
                .name("한강공원")
                .address("서울 강남구 압구정동 387")
                .source(PlaceSource.MAP_SELECTION)
                .location(point)
                .build();

        tag1 = Tag.builder()
                .name("몽환")
                .displayOrder((short) 1)
                .build();

        tag2 = Tag.builder()
                .name("설렘")
                .displayOrder((short) 2)
                .build();

        tag3 = Tag.builder()
                .name("청량")
                .displayOrder((short) 2)
                .build();

        tag4 = Tag.builder()
                .name("신남")
                .displayOrder((short) 2)
                .build();

        tag5 = Tag.builder()
                .name("힙함")
                .displayOrder((short) 2)
                .build();

        Track track = Track.create(
                null,
                null,
                null,
                null,
                null,
                "album",
                "url_test",
                null
        );

        placeTrack = PlaceTrack.create(place, track);

        pin = Pin.builder()
                .member(member)
                .place(place)
                .placeTrack(placeTrack)
                .clipStartMs(0)
                .introduction("테스트 PIN")
                .isFeedPublic(true)
                .build();

        ReflectionTestUtils.setField(member, "id", 1L);
        ReflectionTestUtils.setField(member2, "id", 2L);
        ReflectionTestUtils.setField(place, "id", 1L);
        ReflectionTestUtils.setField(pin, "id", 1L);
    }


    @Test
    void 활성_PIN을_조회한다() {
        // given
        when(pinRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(pin));

        // when
        Pin result = pinQueryService.getActivePin(1L);

        // then
        assertThat(result).isSameAs(pin);
    }

    @Test
    void 존재하지_않거나_삭제된_PIN은_조회할_수_없다() {
        // given
        when(pinRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> pinQueryService.getActivePin(1L))
                .isInstanceOfSatisfying(PinException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(PinErrorCode.PIN_NOT_FOUND));
    }

    // validatePinAvailability 테스트
    @Test
    void 지도_선택위치_검증_등록가능시_CREATABLE_NEW_PLACE_상태를_반환한다() {
        PinRequest.PinAvailability request = PinRequest.PinAvailability.builder()
                .latitude(37.629000)
                .longitude(127.094000)
                .userLatitude(37.626144976334544)
                .userLongitude(127.0930202410747)
                .build();
        when(pinLocationValidator.calculateDistance(request.userLatitude(), request.userLongitude(), request.latitude(), request.longitude()))
                .thenReturn(328.98070823323764);

        PinResponse.PinAvailability result = pinQueryService.validatePinAvailability(request);
        assertThat(result.status())
                .isEqualTo(AvailabilityStatus.CREATABLE_NEW_PLACE);
        assertThat(result.registrable()).isTrue();
        assertThat(result.nearestPinDistanceMeters()).isNull();
    }

    @Test
    void 지도_선택위치_검증_500m_초과시_OUT_OF_RANGE_상태를_반환한다() {
        PinRequest.PinAvailability request = PinRequest.PinAvailability.builder()
                .latitude(37.5283)
                .longitude(126.9326)
                .userLatitude(37.626144976334544)
                .userLongitude(127.09302024107471)
                .build();
        when(pinLocationValidator.calculateDistance(request.userLatitude(), request.userLongitude(), request.latitude(), request.longitude()))
                .thenReturn(17838.988483971672);

        PinResponse.PinAvailability result = pinQueryService.validatePinAvailability(request);
        assertThat(result.status())
                .isEqualTo(AvailabilityStatus.OUT_OF_RANGE);
        assertThat(result.registrable()).isFalse();
        assertThat(result.nearestPinDistanceMeters()).isNull();

        verify(pinQueryRepository, never())
                .findNearestActivePinWithin10m(anyDouble(), anyDouble());
    }

    @Test
    void 지도_선택위치_검증_20m_이내_핀_존재시_TOO_CLOSE_TO_PIN_상태를_반환한다() {
        PinRequest.PinAvailability request = PinRequest.PinAvailability.builder()
                .latitude(37.5282)
                .longitude(126.9326)
                .userLatitude(37.528240)
                .userLongitude(126.932650)
                .build();
        when(pinLocationValidator.calculateDistance(request.userLatitude(), request.userLongitude(), request.latitude(), request.longitude()))
                .thenReturn(76.0836069534716);

        when(pinQueryRepository.findNearestActivePinWithin10m(request.latitude(), request.longitude()))
                .thenReturn(Optional.of(11.09875689));

        PinResponse.PinAvailability result = pinQueryService.validatePinAvailability(request);
        assertThat(result.status())
                .isEqualTo(AvailabilityStatus.TOO_CLOSE_TO_PIN);
        assertThat(result.registrable()).isFalse();
        assertThat(result.nearestPinDistanceMeters()).isNotNull();
    }

    @Test
    void 빈_장소_아이디목록_입력시_빈_Map을_반환한다() {
        Map<Long, PlacePinInfo> result = pinQueryService.findPinInfosByPlaceIds(List.of());
        assertThat(result).isEqualTo(Collections.emptyMap());
    }

    @Test
    void 내가_핀을_등록했다면_true를_반환한다() {
        // given
        when(pinRepository.existsByMemberAndPlaceAndDeletedAtIsNull(member, place))
                .thenReturn(true);

        // when
        boolean result = pinQueryService.validatePlacePinAccessByMember(member, place);

        // then
        assertThat(result).isTrue();
    }

    @Test
    void 핀_상세조회에_성공한다() {
        // given
        when(pinQueryRepository.getPinPreview(pin.getId(), 1L))
                .thenReturn(Optional.ofNullable(pin));

        // when
        PinResponse.PinPreview result = pinQueryService.getPinPreview(pin.getId(), 1L);

        // then
        verify(pinQueryRepository).getPinPreview(pin.getId(), 1L);
        assertThat(result.introduction()).isEqualTo(pin.getIntroduction());
        assertThat(result.clipStartMs()).isEqualTo(pin.getClipStartMs());
        assertThat(result.writerNickname()).isEqualTo(pin.getMember().getNickname());
        assertThat(result.writerProfileImage()).isEqualTo(profileImageStorage.getPublicUrlOrNull(pin.getMember().getProfileImageObjectKey()));
        assertThat(result.placeId()).isEqualTo(pin.getPlace().getId());
        assertThat(result.latitude()).isEqualTo(pin.getPlace().getLocation().getY());
        assertThat(result.longitude()).isEqualTo(pin.getPlace().getLocation().getX());
        assertThat(result.albumImageUrl()).isEqualTo(pin.getPlaceTrack().getTrack().getAlbumImageUrl());
    }

    @Test
    void 줌레벨이_13이하면_클러스터를_조회한다() {
        // given
        PinRequest.Viewport request = new PinRequest.Viewport(
                36.50, 126.90, 37.60, 127.35, 7
        );

        List<PinResponse.Cluster> previews = List.of(mock(PinResponse.Cluster.class));

        given(clusterAndPinRepository.findClusterListByViewport(any(), any(), anyInt(), any()))
                .willReturn(previews);

        // when
        PinResponse.ClusterAndPin result = pinQueryService.getClusterPinList(request,   null);

        // then
        verify(clusterAndPinRepository, never()).findPinPreviewListByViewport(any(), any(), isNull(Long.class));
        verify(clusterAndPinRepository).findClusterListByViewport(any(), any(), anyInt(), isNull(Long.class));

        assertThat(result.pins()).isNull();
        assertThat(result.clusters()).hasSize(1);
    }

    @Test
    void 줌레벨이_14이상_19이하이면_geohash기반_클러스터와_핀목록을_조회한다() {
        // given
        PinRequest.Viewport request = new PinRequest.Viewport(
                37.38,127.11, 37.40, 127.15, 17
        );

        PinResponse.ClusterAndPin clusterAndPin = mock(PinResponse.ClusterAndPin.class);

        given(clusterAndPinRepository.findGeohashClusterListByViewport(any(), any(), anyInt(), anyInt(),  any()))
                .willReturn(clusterAndPin);

        // when
        PinResponse.ClusterAndPin result = pinQueryService.getClusterPinList(request, null);

        // then
        assertThat(result).isSameAs(clusterAndPin);
        verify(clusterAndPinRepository).findGeohashClusterListByViewport(any(), any(), anyInt(), anyInt(), isNull(Long.class));
        verify(clusterAndPinRepository, never())
                .findPinPreviewListByViewport(any(), any(), isNull(Long.class));
        verify(clusterAndPinRepository, never())
                .findClusterListByViewport(any(), any(), anyInt(), isNull(Long.class));
    }

    @Test
    void 줌레벨이_20이상이면_핀목록을_조회한다() {
        // given
        PinRequest.Viewport request = new PinRequest.Viewport(
                37.38,127.11, 37.40, 127.15, 20
        );

        List<PinResponse.PinPreview> previews = List.of(mock(PinResponse.PinPreview.class));

        given(clusterAndPinRepository.findPinPreviewListByViewport(any(), any(), isNull(Long.class)))
                .willReturn(previews);

        // when
        PinResponse.ClusterAndPin result = pinQueryService.getClusterPinList(request, null);

        // then
        verify(clusterAndPinRepository).findPinPreviewListByViewport(any(), any(), isNull(Long.class));
        verify(clusterAndPinRepository, never())
                .findClusterListByViewport(any(), any(), anyInt(), isNull(Long.class));

        assertThat(result.pins()).hasSize(1);
        assertThat(result.clusters()).isNull();
    }

    @Test
    void 장소와_사용자_ID로_활성_핀_존재_여부를_조회한다() {
        // given
        Long placeId = 1L;
        Long memberId = 2L;

        when(pinQueryRepository.existsActivePinByPlaceIdAndMemberId(placeId, memberId))
                .thenReturn(true);

        // when
        boolean result = pinQueryService.existsActivePinByPlaceIdAndMemberId(placeId, memberId);

        // then
        assertThat(result).isTrue();
        verify(pinQueryRepository)
                .existsActivePinByPlaceIdAndMemberId(placeId, memberId);
        verifyNoMoreInteractions(pinQueryRepository);
    }

    @Test
    void 장소와_사용자_ID로_활성_PIN의_PlaceTrack_ID를_조회한다() {
        // given
        Long placeId = 1L;
        Long memberId = 2L;
        when(pinQueryRepository.findActivePlaceTrackIdByPlaceIdAndMemberId(
                placeId,
                memberId
        )).thenReturn(Optional.of(3L));

        // when
        Optional<Long> result =
                pinQueryService.findActivePlaceTrackIdByPlaceIdAndMemberId(
                        placeId,
                        memberId
                );

        // then
        assertThat(result).contains(3L);
        verify(pinQueryRepository).findActivePlaceTrackIdByPlaceIdAndMemberId(
                placeId,
                memberId
        );
    }

    @Test
    void 장소_아이디_목록으로_대표_pinTrack_이미지를_조회한다() {
        // given
        List<Long> placeIds = new ArrayList<>(List.of(1L,2L));
        when(pinQueryRepository.findRepresentativePlaceTracksByPlaceIds(placeIds, 1L))
                .thenReturn(
                        Map.of(
                                1L, AlbumImage.builder().albumImageUrl("url1").build(),
                                2L, AlbumImage.builder().albumImageUrl("url2").build()
                        )
                );

        // when
        Map<Long, AlbumImage> result = pinQueryService.findRepresentativePlaceTracksByPlaceIds(placeIds, 1L);

        // then
        assertThat(result.get(1L).albumImageUrl()).isEqualTo("url1");
        assertThat(result.get(2L).albumImageUrl()).isEqualTo("url2");
        verify(pinQueryRepository).findRepresentativePlaceTracksByPlaceIds(placeIds, 1L);
    }

    @Test
    void 회원이_좋아요한_핀_ID_목록을_조회한다() {
        // given
        when(pinLikeRepository.findPinIdsByMemberId(1L)).thenReturn(List.of(10L, 20L));

        // when
        List<Long> result = pinQueryService.findPinIdsLikedByMember(1L);

        // then
        assertThat(result).containsExactly(10L, 20L);
    }

    @Test
    void 반경_500m_이내이면_핀_목록_조회에_성공한다() {
        // given
        given(placeTrackFinder.getActivePlaceTrack(1L)).willReturn(placeTrack);
        given(pinLocationValidator.calculateDistance(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .willReturn(300.0);

        given(pinQueryRepository.findPinListByPlaceTrackIdAndSortType(
                anyLong(), any(), anyInt(), any(), anyLong()
        )).willReturn(pagination);

        // when
        Pagination<PinResponse.PinDetail> result =
                pinQueryService.findPinListByPlaceTrackIdAndSortType(
                        member, null, 10, PinSortType.LATEST, 1L, request, null);

        // then
        assertThat(result).isEqualTo(pagination);
    }

    @Test
    void 내가_등록한_핀이_있으면_핀_목록_조회에_성공한다() {
        // given
        given(placeTrackFinder.getActivePlaceTrack(1L)).willReturn(placeTrack);

        given(pinLocationValidator.calculateDistance(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .willReturn(700.0);

        given(pinRepository.existsByMemberAndPlaceAndDeletedAtIsNull(member, place))
                .willReturn(true);

        given(pinQueryRepository.findPinListByPlaceTrackIdAndSortType(
                anyLong(), any(), anyInt(), any(), anyLong()
        )).willReturn(pagination);

        // when
        Pagination<PinResponse.PinDetail> result =
                pinQueryService.findPinListByPlaceTrackIdAndSortType(
                        member, null, 10, PinSortType.LATEST, 1L, request, null);

        // then
        assertThat(result).isEqualTo(pagination);
    }

    @Test
    void 좋아요한_노래가_있으면_핀_목록_조회에_성공한다() {
        // given
        given(placeTrackFinder.getActivePlaceTrack(1L)).willReturn(placeTrack);

        given(pinLocationValidator.calculateDistance(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .willReturn(700.0);

        given(pinRepository.existsByMemberAndPlaceAndDeletedAtIsNull(member, place))
                .willReturn(false);

        given(placeTrackLikeQueryService.existsActivePlaceTrackLikedByMemberAtPlace(1L, place.getId()))
                .willReturn(true);

        given(pinQueryRepository.findPinListByPlaceTrackIdAndSortType(
                anyLong(), any(), anyInt(), any(), anyLong()
        )).willReturn(pagination);

        // when
        Pagination<PinResponse.PinDetail> result =
                pinQueryService.findPinListByPlaceTrackIdAndSortType(
                        member, null, 10, PinSortType.LATEST, 1L, request, null);

        // then
        assertThat(result).isEqualTo(pagination);
    }

    @Test
    void 유효한_친구_피드_토큰이_있으면_핀_목록_조회에_성공한다() {
        // given
        given(placeTrackFinder.getActivePlaceTrack(1L)).willReturn(placeTrack);

        given(pinLocationValidator.calculateDistance(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .willReturn(700.0);

        given(pinRepository.existsByMemberAndPlaceAndDeletedAtIsNull(member, place))
                .willReturn(false);

        given(placeTrackLikeQueryService.existsActivePlaceTrackLikedByMemberAtPlace(1L, place.getId()))
                .willReturn(false);

        given(placeAccessTokenRepository.findByToken("token"))
                .willReturn(Optional.of(new PlaceAccessToken(
                        1L,
                        place.getId(),
                        100L
                )));

        given(pinQueryRepository.findPinListByPlaceTrackIdAndSortType(
                anyLong(), any(), anyInt(), any(), anyLong()
        )).willReturn(pagination);

        // when
        Pagination<PinResponse.PinDetail> result =
                pinQueryService.findPinListByPlaceTrackIdAndSortType(
                        member, null, 10, PinSortType.LATEST, 1L, request, "token");

        // then
        assertThat(result).isEqualTo(pagination);
    }

    @Test
    void 모든_접근_조건을_만족하지_않으면_핀_목록_조회에_실패한다() {
        // given
        given(placeTrackFinder.getActivePlaceTrack(1L)).willReturn(placeTrack);

        given(pinLocationValidator.calculateDistance(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .willReturn(700.0);

        given(pinRepository.existsByMemberAndPlaceAndDeletedAtIsNull(member, place))
                .willReturn(false);

        given(placeTrackLikeQueryService.existsActivePlaceTrackLikedByMemberAtPlace(1L, place.getId()))
                .willReturn(false);

        given(placeAccessTokenRepository.findByToken("token"))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() ->
                pinQueryService.findPinListByPlaceTrackIdAndSortType(
                        member, null, 10, PinSortType.LATEST, 1L, request, "token"))
                .isInstanceOf(PinException.class)
                .hasMessage(PinErrorCode.PIN_ACCESS_DENIED.getMessage());
    }
}
