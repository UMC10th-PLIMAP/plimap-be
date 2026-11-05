package com.example.plimap.domain.pin.repository.query.impl;

import com.example.plimap.domain.member.entity.Member;
import com.example.plimap.domain.member.entity.MemberFollow;
import com.example.plimap.domain.member.repository.MemberFollowRepository;
import com.example.plimap.domain.member.repository.MemberRepository;
import com.example.plimap.domain.pin.dto.Pagination;
import com.example.plimap.domain.pin.dto.PlacePinInfo;
import com.example.plimap.domain.pin.dto.ReportedPinInfo;
import com.example.plimap.domain.pin.dto.request.PinRequest;
import com.example.plimap.domain.pin.dto.response.PinResponse;
import com.example.plimap.domain.pin.entity.Pin;
import com.example.plimap.domain.pin.enums.PinReportFilter;
import com.example.plimap.domain.pin.enums.PinSortType;
import com.example.plimap.domain.pin.repository.PinRepository;
import com.example.plimap.domain.pin.repository.query.PinQueryRepository;
import com.example.plimap.domain.place.entity.Place;
import com.example.plimap.domain.place.entity.PlaceSource;
import com.example.plimap.domain.place.repository.PlaceRepository;
import com.example.plimap.domain.report.entity.Report;
import com.example.plimap.domain.report.enums.ReportCategory;
import com.example.plimap.domain.report.repository.ReportRepository;
import com.example.plimap.domain.track.entity.PlaceTrack;
import com.example.plimap.domain.track.entity.Track;
import com.example.plimap.domain.track.repository.PlaceTrackRepository;
import com.example.plimap.domain.track.repository.TrackRepository;
import com.example.plimap.global.external.storage.ProfileImageStorage;
import com.example.plimap.support.PostgisContainerConfiguration;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(PostgisContainerConfiguration.class)
@Transactional
class PinQueryRepositoryImplTest {

    @Autowired
    private PinQueryRepository pinQueryRepository;

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
    private MemberFollowRepository memberFollowIdRepository;

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private ProfileImageStorage profileImageStorage;

    @Autowired
    EntityManager entityManager;

    @Autowired
    JdbcTemplate jdbcTemplate;

    Pin pin1, pin2, pin3, pin4, pin5, deletedPin, pin7, pin8, pin9;
    Place place1, place2, place3, place4, deletedPlace;
    Member member1, member2, member3, deletedMember;
    Report report;
    PlaceTrack placeTrack1, placeTrack3, placeTrack4, placeTrack5;
    GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
    PinRequest.UserLocation request = PinRequest.UserLocation.builder()
            .userLatitude(37.5283)
            .userLongitude(126.9326)
            .build();

    @BeforeEach
    void setup() {
        member1 = createMember("이서윤", "이서");
        member2 = createMember("홍길동", "동길");
        member3 = createMember("테스트", "테스트");
        deletedMember = createMember("삭제멤", "삭제멤");

        place1 = createPlace(
                "여의도 한강공원",
                "서울 영등포구 여의동로 330",
                126.9326,
                37.5283
        );

        place2 = createPlace(
                "서울숲",
                "서울 성동구 뚝섬로 273",
                127.0372,
                37.5446
        );

        place3 = createPlace(
                "남산서울타워",
                "서울 용산구 남산공원길 105",
                126.9882,
                37.5512
        );

        place4 = createPlace(
                "석촌호수",
                "서울특별시 송파구 잠실동",
                127.1025,
                37.5125
        );

        deletedPlace = createPlace(
                "석촌호수2",
                "서울특별시 송파구 잠실동22",
                127.102,
                37.512
        );

        Track track = Track.create(
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

        placeTrack1 = PlaceTrack.create(place1, track);
        PlaceTrack placeTrack2 = PlaceTrack.create(place2, track);
        placeTrack3 = PlaceTrack.create(place4, track2);
        placeTrack4 = PlaceTrack.create(deletedPlace, track2);
        placeTrack5 = PlaceTrack.create(place3, track2);

        pin1 = createPin(member1, place1, placeTrack1);
        pin2 = createPin(member2, place1, placeTrack1);
        pin3 = createPin(member2, place2, placeTrack2);
        pin4 = createPin(member2, place4, placeTrack3);
        pin5 = createPin(member1, place4, placeTrack3);
        deletedPin = createPin(member3, place1, placeTrack1);
        pin7 = createPin(member3, place2, placeTrack2, false);
        pin8 = createPin(member3, deletedPlace, placeTrack4);
        pin9 = createPin(deletedMember, place2, placeTrack2);

        report = Report.createPinReport(member2 ,pin5, ReportCategory.COMMERCIAL_OR_PROMOTIONAL, null);

        memberRepository.saveAll(List.of(member1, member2, member3, deletedMember));
        MemberFollow memberFollow12 = MemberFollow.create(member1, member2);
        MemberFollow memberFollow23 = MemberFollow.create(member2, member3);
        MemberFollow memberFollow1d = MemberFollow.create(member1, deletedMember);

        memberFollowIdRepository.saveAll(List.of(memberFollow12, memberFollow23, memberFollow1d));
        placeRepository.saveAll(List.of(place1, place2, place3, place4, deletedPlace));
        trackRepository.saveAll(List.of(track, track2));
        placeTrackRepository.saveAll(List.of(placeTrack1, placeTrack2, placeTrack3, placeTrack4, placeTrack5));
        pinRepository.saveAll(List.of(pin1, pin2, pin3, pin4, pin5, deletedPin, pin7, pin8, pin9));
        reportRepository.save(report);

        increaseLike(pin1, 3);
        increaseLike(pin2, 2);

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void 현위치로부터_10m_이내_핀이_있으면_거리를_반환한다() {
        Optional<Double> distance =
                pinQueryRepository.findNearestActivePinWithin10m(37.528345,126.932600);

        assertThat(distance).isPresent();
        assertThat(distance.get()).isLessThan(10);
    }

    @Test
    void 현위치로부터_10m_이내_핀이_없으면_OptionalEmpty를_반환한다() {
        Optional<Double> distance =
                pinQueryRepository.findNearestActivePinWithin10m(37.6000,127.1000);

        assertThat(distance).isEmpty();
    }

    @Test
    void 장소_ID_목록에_해당하는_장소의_핀등록_여부와_첫_등록자_닉네임을_반환한다() {
        List<Long> placeIdList = List.of(
                place1.getId(),
                place2.getId(),
                place3.getId()
        );
        Map<Long, PlacePinInfo> result = pinQueryRepository.findPinInfosByPlaceIds(placeIdList);

        assertThat(result.get(place1.getId()).hasPin()).isTrue();
        assertThat(result.get(place1.getId()).firstPinCreatorNickname()).isEqualTo("이서");
        assertThat(result.get(place1.getId()).pinCount()).isEqualTo(3L);
        assertThat(result.get(place2.getId()).hasPin()).isTrue();
        assertThat(result.get(place2.getId()).firstPinCreatorNickname()).isEqualTo("동길");
        assertThat(result.get(place2.getId()).pinCount()).isEqualTo(3L);
        assertThat(result.get(place3.getId()).hasPin()).isFalse();
        assertThat(result.get(place3.getId()).firstPinCreatorNickname()).isNull();
        assertThat(result.get(place3.getId()).pinCount()).isEqualTo(0L);
    }

    @Test
    void 최초_등록_핀이_삭제되면_다음_활성_핀_등록자의_닉네임을_반환한다() {
        List<Long> placeIdList = List.of(
                place1.getId(),
                place2.getId(),
                place3.getId()
        );
        Pin managedPin = pinRepository.findById(pin1.getId()).orElseThrow();
        managedPin.delete();
        entityManager.flush();
        entityManager.clear();

        Map<Long, PlacePinInfo> result = pinQueryRepository.findPinInfosByPlaceIds(placeIdList);

        assertThat(result.get(place1.getId()).hasPin()).isTrue();
        assertThat(result.get(place1.getId()).firstPinCreatorNickname()).isEqualTo("동길");
        assertThat(result.get(place1.getId()).pinCount()).isEqualTo(2L);
    }

    @Test
    void 피드정보를_커서기반_페이지네이션으로_조회한다() {
        Pagination<PinResponse.Feed> response = pinQueryRepository.findFeedListByMemberId(member2.getId(), null, null, 2, request);

        assertThat(response.data().size()).isEqualTo(2);
        assertThat(response.hasNext()).isTrue();
        assertThat(Long.parseLong(response.nextCursor().split("/")[1])).isEqualTo(pin3.getId());
        String nextCursor = response.nextCursor();
        Pagination<PinResponse.Feed> response2 = pinQueryRepository.findFeedListByMemberId(member2.getId(), null, nextCursor, 2, request);

        assertThat(response2.data().size()).isEqualTo(1);
        assertThat(response2.hasNext()).isFalse();
        assertThat(response2.nextCursor()).isNull();
    }

    @Test
    void 전달한_커서_기반으로_피드정보를_조회한다() {
        String cursor = "%s/%d".formatted(
                pin3.getCreatedAt(),
                pin3.getId()
        );
        Pagination<PinResponse.Feed> response = pinQueryRepository.findFeedListByMemberId(member2.getId(), null, cursor, 2, request);
        assertThat(response.data().size()).isEqualTo(1);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    void 추가된_장소_기반_피드_정보를_조회한다() {
        Pagination<PinResponse.Feed> response = pinQueryRepository.findFeedListByMemberId(member2.getId(), null, null, 2, request);

        assertThat(response.data().size()).isEqualTo(2);
        assertThat(response.hasNext()).isTrue();
        assertThat(Long.parseLong(response.nextCursor().split("/")[1])).isEqualTo(pin3.getId());

        PinResponse.Feed last = response.data().getLast();
        assertThat(last.placeName()).isEqualTo(place2.getName());
        assertThat(last.pinCount()).isEqualTo(3L);
        assertThat(last.distanceFromUser())
                .isBetween(9350, 9450);
    }


    @Test
    void 장소가_존재하지_않으면_피드_조회에서_해당_장소의_핀들을_제외한다() {
        // 삭제 전
        Pagination<PinResponse.Feed> response = pinQueryRepository.findFeedListByMemberId(member3.getId(), null, null, 2, request);
        assertThat(response.data().size()).isEqualTo(2);

        // when
        Place managedPlace = placeRepository.findById(deletedPlace.getId()).orElseThrow();

        managedPlace.delete();
        entityManager.flush();
        entityManager.clear();

        // then
        Pagination<PinResponse.Feed> response2 = pinQueryRepository.findFeedListByMemberId(member3.getId(), null, null, 2, request);
        assertThat(response2.data().size()).isEqualTo(1);
    }

    @Test
    void 내_핀_목록을_커서기반_페이지네이션으로_조회한다() {
        Pagination<PinResponse.MyPin> response = pinQueryRepository.findMyPinList(member2.getId(), null, 2 );

        assertThat(response.data().size()).isEqualTo(2);
        assertThat(response.hasNext()).isTrue();
        assertThat(Long.parseLong(response.nextCursor().split("/")[1])).isEqualTo(pin3.getId());
        String nextCursor = response.nextCursor();
        Pagination<PinResponse.MyPin> response2 = pinQueryRepository.findMyPinList(member2.getId(), nextCursor, 2 );

        assertThat(response2.data().size()).isEqualTo(1);
        assertThat(response2.hasNext()).isFalse();
        assertThat(response2.nextCursor()).isNull();
    }

    @Test
    void 전달한_커서_기반으로_내_핀_목록을_조회한다() {
        String cursor = "%s/%d".formatted(
                pin3.getCreatedAt(),
                pin3.getId()
        );
        Pagination<PinResponse.MyPin> response = pinQueryRepository.findMyPinList(member2.getId(), cursor, 2 );
        assertThat(response.data().size()).isEqualTo(1);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    void 팔로우한_사용자가_해당_장소에_핀을_등록했다면_true를_반환한다() {
        // given
        memberFollowIdRepository.save(
                MemberFollow.create(member1, member2)
        );

        // when
        Boolean result =
                pinQueryRepository.existsPinByMemberFollowAndPlace(
                        member1.getId(),
                        place2.getId()
                );

        // then
        assertThat(result).isTrue();
    }

    @Test
    void 팔로우한_사용자가_해당_장소에_핀을_등록하지_않았다면_false를_반환한다() {
        // given
        memberFollowIdRepository.save(
                MemberFollow.create(member1, member2)
        );

        // when
        Boolean result =
                pinQueryRepository.existsPinByMemberFollowAndPlace(
                        member1.getId(),
                        place3.getId()
                );

        // then
        assertThat(result).isFalse();
        Boolean response = pinQueryRepository.existsPinByMemberFollowAndPlace(member1.getId(), place3.getId());
        assertThat(response).isFalse();

    }

    @Test
    void 특정_장소에_대한_핀_목록을_좋아요순으로_페이지네이션으로_조회한다() {
        Pagination<PinResponse.PinDetail> response = pinQueryRepository.findPinListByPlaceTrackIdAndSortType(member2.getId(), null, 1 , PinSortType.POPULAR, placeTrack1.getId());

        assertThat(response.data().size()).isEqualTo(1);
        assertThat(response.hasNext()).isTrue();
        assertThat(Integer.parseInt(response.nextCursor().split("/")[0])).isEqualTo(3);
        assertThat(Long.parseLong(response.nextCursor().split("/")[1])).isEqualTo(pin1.getId());

        String nextCursor = response.nextCursor();

        Pagination<PinResponse.PinDetail> response2 = pinQueryRepository.findPinListByPlaceTrackIdAndSortType(member2.getId(), nextCursor, 2, PinSortType.POPULAR, placeTrack1.getId());
        assertThat(response2.data().size()).isEqualTo(2);
        assertThat(response2.hasNext()).isFalse();
        assertThat(response2.data().getFirst().pinId()).isEqualTo(pin2.getId());
        assertThat(response2.nextCursor()).isNull();
    }


    @Test
    void 특정_장소에_대한_핀_목록을_최신순으로_페이지네이션으로_조회한다() {
        Pagination<PinResponse.PinDetail> response = pinQueryRepository.findPinListByPlaceTrackIdAndSortType(member2.getId(), null, 1 , PinSortType.LATEST, placeTrack1.getId());

        assertThat(response.data().size()).isEqualTo(1);
        assertThat(response.hasNext()).isTrue();
        assertThat(Long.parseLong(response.nextCursor().split("/")[1])).isEqualTo(deletedPin.getId());
        String nextCursor = response.nextCursor();
        Pagination<PinResponse.PinDetail> response2 = pinQueryRepository.findPinListByPlaceTrackIdAndSortType(member2.getId(), nextCursor, 2, PinSortType.LATEST, placeTrack1.getId());

        assertThat(response2.data().size()).isEqualTo(2);
        assertThat(response2.hasNext()).isFalse();
        assertThat(response2.nextCursor()).isNull();
    }

    @Test
    void 사용자에_따라_pinByMe가_달라진다() {
        Pagination<PinResponse.PinDetail> response = pinQueryRepository.findPinListByPlaceTrackIdAndSortType(member2.getId(), null, 1 , PinSortType.POPULAR, placeTrack1.getId());
        PinResponse.PinDetail last = response.data().getLast();
        assertThat(last.pinByMe()).isFalse();

        Pagination<PinResponse.PinDetail> response2 = pinQueryRepository.findPinListByPlaceTrackIdAndSortType(member1.getId(), null, 1 ,  PinSortType.POPULAR, placeTrack1.getId());
        PinResponse.PinDetail last2 = response2.data().getLast();
        assertThat(last2.pinByMe()).isTrue();
    }

    @Test
    void 내가_신고한_핀은_제외하고_조회한다() {
        // given
        Long reportedId = member2.getId();

        // when
        Pagination<PinResponse.PinDetail> response = pinQueryRepository.findPinListByPlaceTrackIdAndSortType(reportedId, null, 5 , null, placeTrack3.getId());

        // then
        assertThat(response.data().size()).isEqualTo(1);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
        assertThat(response.data())
                .extracting(PinResponse.PinDetail::pinId)
                .doesNotContain(pin5.getId())
                .containsExactly(pin4.getId());
    }

    @Test
    void 다른_회원이_신고한_핀은_포함하여_조회한다() {
        // given
        Long nonReportedId = member1.getId();

        // when
        Pagination<PinResponse.PinDetail> response = pinQueryRepository.findPinListByPlaceTrackIdAndSortType(nonReportedId, null, 5 ,  PinSortType.POPULAR, placeTrack3.getId());

        // then
        assertThat(response.data().size()).isEqualTo(2);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
        assertThat(response.data())
                .extracting(PinResponse.PinDetail::pinId)
                .containsExactly(pin5.getId(), pin4.getId());
    }

    @Test
    void 피드_조회시_내가_신고한_핀은_제외된다() {
        Pagination<PinResponse.Feed> response =
                pinQueryRepository.findFeedListByMemberId(member1.getId(), member2.getId(), null, 10, request);

        assertThat(response.data())
                .extracting(PinResponse.Feed::pinId)
                .doesNotContain(pin5.getId())
                .contains(pin1.getId());
    }

    @Test
    void 피드_조회시_다른_사람이_신고한_핀은_보인다() {
        Pagination<PinResponse.Feed> response =
                pinQueryRepository.findFeedListByMemberId(member1.getId(), member1.getId(), null, 10, request);

        assertThat(response.data())
                .extracting(PinResponse.Feed::pinId)
                .contains(pin5.getId());
    }

    @Test
    void 피드_조회시_신고누적_10회_이상인_핀은_전원에게_숨겨진다() {
        increaseReportCount(pin1, 10);
        entityManager.flush();
        entityManager.clear();

        Pagination<PinResponse.Feed> response =
                pinQueryRepository.findFeedListByMemberId(member1.getId(), null, null, 10, request);

        assertThat(response.data())
                .extracting(PinResponse.Feed::pinId)
                .doesNotContain(pin1.getId());
    }

    @Test
    void 내_핀_목록에서_신고누적_10회_이상인_핀은_본인에게도_숨겨진다() {
        increaseReportCount(pin1, 10);
        entityManager.flush();
        entityManager.clear();

        Pagination<PinResponse.MyPin> response =
                pinQueryRepository.findMyPinList(member1.getId(), null, 10);

        assertThat(response.data())
                .extracting(PinResponse.MyPin::pinId)
                .doesNotContain(pin1.getId());
    }

    @Test
    void 미리보기_조회시_내가_신고한_핀은_숨겨진다() {
        Optional<Pin> result = pinQueryRepository.getPinPreview(pin5.getId(), member2.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void 미리보기_조회시_다른_사람이_신고한_핀은_보인다() {
        Optional<Pin> result = pinQueryRepository.getPinPreview(pin5.getId(), member1.getId());

        assertThat(result).isPresent();
    }

    @Test
    void 미리보기_조회시_신고누적_10회_이상인_핀은_전원에게_숨겨진다() {
        increaseReportCount(pin1, 10);
        entityManager.flush();
        entityManager.clear();

        Optional<Pin> result = pinQueryRepository.getPinPreview(pin1.getId(), member1.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void 전달한_커서_기반으로_특정_장소에_대한_핀_목록을_조회한다() {
        String cursor = "%s/%d".formatted(
                pin2.getCreatedAt(),
                pin2.getId()
        );
        Pagination<PinResponse.PinDetail> response = pinQueryRepository.findPinListByPlaceTrackIdAndSortType(member2.getId(), cursor, 2, null, placeTrack1.getId());
        assertThat(response.data().size()).isEqualTo(1);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    void 존재하는_핀이면_조회된다() {
        Optional<Pin> result = pinQueryRepository.getPinPreview(pin1.getId(), null);

        assertThat(result).isPresent();
    }

    @Test
    void 존재하지_않는_핀이면_Optional_empty를_반환한다() {
        Optional<Pin> result = pinQueryRepository.getPinPreview(999L, null);

        assertThat(result).isEmpty();
    }

    @Test
    void 장소에_사용자가_등록한_핀이_존재하면_true를_반환한다() {
        boolean result = pinQueryRepository.existsActivePinByPlaceIdAndMemberId(place1.getId(), member1.getId());

        assertThat(result).isTrue();
    }

    @Test
    void 장소에_사용자가_등록한_핀이_존재하지_않으면_false를_반환한다() {
        boolean result = pinQueryRepository.existsActivePinByPlaceIdAndMemberId(place2.getId(), member1.getId());

        assertThat(result).isFalse();
    }

    @Test
    void 장소에_사용자가_등록한_활성핀이_존재하지_않으면_false를_반환한다() {
        // 삭제 전
        assertThat(
                pinQueryRepository.existsActivePinByPlaceIdAndMemberId(place1.getId(), member3.getId())
        ).isTrue();

        // when
        Pin managedPin = pinRepository.findById(deletedPin.getId()).orElseThrow();

        managedPin.delete();
        entityManager.flush();
        entityManager.clear();

        // then
        assertThat(
                pinQueryRepository.existsActivePinByPlaceIdAndMemberId(place1.getId(), member3.getId())
        ).isFalse();
    }

    @Test
    void 피드_비공개_상태_핀이어도_존재하면_true를_반환한다() {
        boolean result = pinQueryRepository.existsActivePinByPlaceIdAndMemberId(place2.getId(), member3.getId());

        assertThat(result).isTrue();
    }

    @Test
    void 장소가_존재하지_않으면_장소에_사용자가_등록한_핀_조회에서_false를_반환한다() {
        // 삭제 전
        assertThat(
                pinQueryRepository.existsActivePinByPlaceIdAndMemberId(deletedPlace.getId(), member3.getId())
        ).isTrue();

        // when
        Place managedPlace = placeRepository.findById(deletedPlace.getId()).orElseThrow();

        managedPlace.delete();
        entityManager.flush();
        entityManager.clear();

        // then
        assertThat(
                pinQueryRepository.existsActivePinByPlaceIdAndMemberId(deletedPlace.getId(), member3.getId())
        ).isFalse();
    }

    @Test
    void 회원이__존재하지_않으면_장소에_사용자가_등록한_핀_조회에서_false를_반환한다() {
        // 삭제 전
        assertThat(
                pinQueryRepository.existsActivePinByPlaceIdAndMemberId(place2.getId(), deletedMember.getId())
        ).isTrue();

        // when
        Member managedMember = memberRepository.findById(deletedMember.getId()).orElseThrow();

        managedMember.delete();
        entityManager.flush();
        entityManager.clear();

        // then
        assertThat(
                pinQueryRepository.existsActivePinByPlaceIdAndMemberId(place2.getId(), deletedMember.getId())
        ).isFalse();
    }

    @Test
    void 장소와_사용자의_활성_PIN에_연결된_PlaceTrack_ID를_반환한다() {
        Optional<Long> result =
                pinQueryRepository.findActivePlaceTrackIdByPlaceIdAndMemberId(
                        place1.getId(),
                        member1.getId()
                );

        assertThat(result).contains(placeTrack1.getId());
    }

    @Test
    void 장소에_사용자의_PIN이_없으면_빈_Optional을_반환한다() {
        Optional<Long> result =
                pinQueryRepository.findActivePlaceTrackIdByPlaceIdAndMemberId(
                        place3.getId(),
                        member1.getId()
                );

        assertThat(result).isEmpty();
    }

    @Test
    void 삭제된_PIN만_존재하면_빈_Optional을_반환한다() {
        Pin managedPin = pinRepository.findById(deletedPin.getId()).orElseThrow();
        managedPin.delete();
        entityManager.flush();
        entityManager.clear();

        Optional<Long> result =
                pinQueryRepository.findActivePlaceTrackIdByPlaceIdAndMemberId(
                        place1.getId(),
                        member3.getId()
                );

        assertThat(result).isEmpty();
    }

    @Test
    void 다른_사용자의_활성_PIN만_존재하면_빈_Optional을_반환한다() {
        Optional<Long> result =
                pinQueryRepository.findActivePlaceTrackIdByPlaceIdAndMemberId(
                        place2.getId(),
                        member1.getId()
                );

        assertThat(result).isEmpty();
    }


    // getFriendRecentPinList
    @Test
    void 내가_팔로우한_사람의_24시간_이내_등록_핀을_조회한다() {
        // when
        Pagination<PinResponse.FriendPin> result = pinQueryRepository.getFriendRecentPinList(member1.getId(), null, 10);
        PinResponse.FriendPin first = result.data().getFirst();

        // then
        assertThat(result.data().size()).isEqualTo(4);
        assertThat(first.pinId()).isEqualTo(pin9.getId());
        assertThat(first.writerNickname()).isEqualTo(deletedMember.getNickname());
        assertThat(first.writerProfileImage()).isEqualTo(profileImageStorage.getPublicUrlOrNull(deletedMember.getProfileImageObjectKey()));
        assertThat(first.placeName()).isEqualTo(pin9.getPlace().getName());
        assertThat(first.latitude()).isEqualTo(pin9.getPlace().getLocation().getY());
        assertThat(first.longitude()).isEqualTo(pin9.getPlace().getLocation().getX());
    }

    @Test
    void 피드공개_설정이_false면_등록_핀을_조회하지_않는다() {
        // when
        Pagination<PinResponse.FriendPin> result = pinQueryRepository.getFriendRecentPinList(member2.getId(), null, 10);
        PinResponse.FriendPin first = result.data().getFirst();

        // then
        assertThat(result.data().size()).isEqualTo(2);
        assertThat(first.pinId()).isEqualTo(pin8.getId());
        assertThat(first.writerNickname()).isEqualTo(member3.getNickname());
        assertThat(first.writerProfileImage()).isEqualTo(profileImageStorage.getPublicUrlOrNull(member3.getProfileImageObjectKey()));
        assertThat(first.placeName()).isEqualTo(pin8.getPlace().getName());
        assertThat(first.latitude()).isEqualTo(pin8.getPlace().getLocation().getY());
        assertThat(first.longitude()).isEqualTo(pin8.getPlace().getLocation().getX());
    }

    @Test
    void _24시간_내에_등록한_핀만_조회한다() throws SQLException {
        // given
        Instant now = Instant.now();

        jdbcTemplate.update(
                "UPDATE pin SET created_at = ? WHERE id = ?",
                Timestamp.from(now.minus(25, ChronoUnit.HOURS)),
                pin2.getId()
        );

        // when
        Pagination<PinResponse.FriendPin> result = pinQueryRepository.getFriendRecentPinList(member1.getId(), null, 10);
        PinResponse.FriendPin last = result.data().getLast();

        // then
        assertThat(result.data().size()).isEqualTo(3);
        assertThat(last.pinId()).isEqualTo(pin3.getId());
        assertThat(last.writerNickname()).isEqualTo(member2.getNickname());
        assertThat(last.writerProfileImage()).isEqualTo(profileImageStorage.getPublicUrlOrNull(member2.getProfileImageObjectKey()));
        assertThat(last.placeName()).isEqualTo(pin3.getPlace().getName());
        assertThat(last.latitude()).isEqualTo(pin3.getPlace().getLocation().getY());
        assertThat(last.longitude()).isEqualTo(pin3.getPlace().getLocation().getX());
    }

    @Test
    void _24시간_내에_등록한_핀이_하나도_없으면_그냥_최신핀을_조회한다() {
        // given
        Instant now = Instant.now();

        jdbcTemplate.update(
                "UPDATE pin SET created_at = ? WHERE id = ?",
                Timestamp.from(now.minus(26, ChronoUnit.HOURS)),
                pin8.getId()
        );

        jdbcTemplate.update(
                "UPDATE pin SET created_at = ? WHERE id = ?",
                Timestamp.from(now.minus(27, ChronoUnit.HOURS)),
                deletedPin.getId()
        );

        // when
        Pagination<PinResponse.FriendPin> result = pinQueryRepository.getFriendRecentPinList(member2.getId(), null, 10);
        PinResponse.FriendPin last = result.data().getLast();

        // then
        assertThat(result.data().size()).isEqualTo(2);
        assertThat(result.nextCursor()).isNull();
        assertThat(result.hasNext()).isFalse();
        assertThat(last.pinId()).isEqualTo(deletedPin.getId());
        assertThat(last.writerNickname()).isEqualTo(member3.getNickname());
        assertThat(last.writerProfileImage()).isEqualTo(profileImageStorage.getPublicUrlOrNull(member3.getProfileImageObjectKey()));
        assertThat(last.placeName()).isEqualTo(deletedPin.getPlace().getName());
        assertThat(last.latitude()).isEqualTo(deletedPin.getPlace().getLocation().getY());
        assertThat(last.longitude()).isEqualTo(deletedPin.getPlace().getLocation().getX());
    }

    @Test
    void 홈화면_피드_조회시_내가_차단한_사용자의_모든_핀은_제외된다() {
        // given
        reportRepository.save(
                Report.createPinReport(
                        member1,
                        pin2,
                        ReportCategory.COMMERCIAL_OR_PROMOTIONAL,
                        null
                )
        );

        // when
        Pagination<PinResponse.Feed> response =
                pinQueryRepository.findFeedListByMemberId(
                        member1.getId(), null, null, 10, request
                );

        // then
        assertThat(response.data())
                .extracting(PinResponse.Feed::pinId)
                .doesNotContain(
                        pin2.getId(),
                        pin3.getId(),
                        pin4.getId()
                );
    }

    @Test
    void 홈화면_피드_조회시_내가_차단한_핀은_제외된다() {
        // given
        reportRepository.save(
                Report.createPinReport(
                        member1,
                        pin2,
                        ReportCategory.COMMERCIAL_OR_PROMOTIONAL,
                        null
                )
        );

        // when
        Pagination<PinResponse.Feed> response =
                pinQueryRepository.findFeedListByMemberId(
                        member1.getId(), null, null, 10, request
                );

        // then
        assertThat(response.data())
                .extracting(PinResponse.Feed::pinId)
                .doesNotContain(pin2.getId())
                .contains(pin1.getId());
    }

    @Test
    void 특정_사용자가_작성한_핀_개수를_반환한다() {
        // when
        long countMember1Pin = pinQueryRepository.countPinsByMemberId(member1.getId());
        long countMember2Pin = pinQueryRepository.countPinsByMemberId(member2.getId());
        long countMember3Pin = pinQueryRepository.countPinsByMemberId(member3.getId());

        // then
        assertThat(countMember1Pin).isEqualTo(2);
        assertThat(countMember2Pin).isEqualTo(3);
        assertThat(countMember3Pin).isEqualTo(2);
    }

    @Test
    void 신고_누적_핀_목록을_필터로_구분해서_조회한다() {
        // given
        Pin lowReportPin = createPin(member1, place3, placeTrack5);
        Pin highReportPin = createPin(member2, place3, placeTrack5);
        pinRepository.saveAll(List.of(lowReportPin, highReportPin));
        increaseReportCount(lowReportPin, 3);
        increaseReportCount(highReportPin, 12);
        entityManager.flush();
        entityManager.clear();

        // when
        Page<ReportedPinInfo> allPage =
                pinQueryRepository.findReportedPins(PinReportFilter.ALL, PageRequest.of(0, 10));
        Page<ReportedPinInfo> autoHiddenPage =
                pinQueryRepository.findReportedPins(PinReportFilter.AUTO_HIDDEN, PageRequest.of(0, 10));
        Page<ReportedPinInfo> belowThresholdPage =
                pinQueryRepository.findReportedPins(PinReportFilter.BELOW_THRESHOLD, PageRequest.of(0, 10));

        // then
        assertThat(allPage.getTotalElements()).isEqualTo(2);
        assertThat(allPage.getContent()).extracting(ReportedPinInfo::pinId)
                .containsExactly(highReportPin.getId(), lowReportPin.getId());

        ReportedPinInfo topInfo = allPage.getContent().get(0);
        assertThat(topInfo.reportCount()).isEqualTo(12);
        assertThat(topInfo.authorMemberId()).isEqualTo(member2.getId());
        assertThat(topInfo.authorNickname()).isEqualTo(member2.getNickname());
        assertThat(topInfo.pinTitle()).isEqualTo(placeTrack5.getTrack().getTitle());
        assertThat(topInfo.pinLocation()).isEqualTo(place3.getName());

        assertThat(autoHiddenPage.getContent()).extracting(ReportedPinInfo::pinId)
                .containsExactly(highReportPin.getId());
        assertThat(belowThresholdPage.getContent()).extracting(ReportedPinInfo::pinId)
                .containsExactly(lowReportPin.getId());
    }

    @Test
    void 신고_누적_핀_목록은_페이지네이션을_지원한다() {
        // given
        Pin pinA = createPin(member1, place3, placeTrack5);
        Pin pinB = createPin(member2, place3, placeTrack5);
        Pin pinC = createPin(member3, place3, placeTrack5);
        pinRepository.saveAll(List.of(pinA, pinB, pinC));
        increaseReportCount(pinA, 1);
        increaseReportCount(pinB, 2);
        increaseReportCount(pinC, 3);
        entityManager.flush();
        entityManager.clear();

        // when
        Page<ReportedPinInfo> firstPage =
                pinQueryRepository.findReportedPins(PinReportFilter.ALL, PageRequest.of(0, 2));
        Page<ReportedPinInfo> secondPage =
                pinQueryRepository.findReportedPins(PinReportFilter.ALL, PageRequest.of(1, 2));

        // then
        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getContent()).extracting(ReportedPinInfo::pinId)
                .containsExactly(pinC.getId(), pinB.getId());
        assertThat(secondPage.getContent()).extracting(ReportedPinInfo::pinId)
                .containsExactly(pinA.getId());
    }

    private Member createMember(String name, String nickname) {
        return Member.builder()
                .name(name)
                .nickname(nickname)
                .introduction("안녕하세요")
                .profileImageObjectKey("members/12/b7c277d4-31d3-470b-8bb7-ec89c114016c.webp")
                .build();
    }

    private Place createPlace(String name, String address, double lng, double lat) {
        return Place.builder()
                .name(name)
                .address(address)
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

    private void increaseLike(Pin pin, int count) {
        for (int i = 0; i < count; i++) {
            pinRepository.increaseLikeCount(pin.getId());
        }
    }

    private void increaseReportCount(Pin pin, int count) {
        for (int i = 0; i < count; i++) {
            pinRepository.increaseReportCount(pin.getId());
        }
    }
}
