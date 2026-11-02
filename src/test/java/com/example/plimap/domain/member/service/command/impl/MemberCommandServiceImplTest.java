package com.example.plimap.domain.member.service.command.impl;

import com.example.plimap.domain.auth.exception.AuthErrorCode;
import com.example.plimap.domain.auth.exception.AuthException;
import com.example.plimap.domain.auth.service.command.SocialAccountCommandService;
import com.example.plimap.domain.member.dto.request.MemberReqDTO;
import com.example.plimap.domain.member.dto.response.MemberResponse;
import com.example.plimap.domain.member.entity.Member;
import com.example.plimap.domain.member.entity.MemberFollow;
import com.example.plimap.domain.member.entity.MemberFollowId;
import com.example.plimap.domain.member.enums.MemberStatus;
import com.example.plimap.domain.member.enums.SuspensionPeriod;
import com.example.plimap.domain.member.enums.WithdrawalReason;
import com.example.plimap.domain.member.event.MemberWithdrawnEvent;
import com.example.plimap.domain.member.exception.MemberErrorCode;
import com.example.plimap.domain.member.exception.MemberException;
import com.example.plimap.domain.member.repository.MemberFollowRepository;
import com.example.plimap.domain.member.repository.MemberRepository;
import com.example.plimap.domain.member.service.query.MemberQueryService;
import com.example.plimap.domain.report.enums.ReportCategory;
import com.example.plimap.global.external.storage.ProfileImageObjectKeyGenerator;
import com.example.plimap.global.external.storage.ProfileImageStorage;
import com.example.plimap.global.external.storage.ProfileImageStorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemberCommandServiceImplTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long OTHER_MEMBER_ID = 2L;
    private static final byte[] WEBP_CONTENT = {
            'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 0, 0
    };

    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final MemberFollowRepository memberFollowRepository = mock(MemberFollowRepository.class);
    private final MemberQueryService memberQueryService = mock(MemberQueryService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final ProfileImageStorage profileImageStorage = mock(ProfileImageStorage.class);
    private final ProfileImageObjectKeyGenerator profileImageObjectKeyGenerator =
            mock(ProfileImageObjectKeyGenerator.class);
    private final SocialAccountCommandService socialAccountCommandService =
            mock(SocialAccountCommandService.class);

    private MemberCommandServiceImpl memberCommandService;

    @BeforeEach
    void setUp() {
        memberCommandService = new MemberCommandServiceImpl(
                memberRepository,
                memberFollowRepository,
                memberQueryService,
                eventPublisher,
                profileImageStorage,
                profileImageObjectKeyGenerator,
                socialAccountCommandService
        );
    }

    @Test
    void 존재하지_않는_회원이면_예외가_발생한다() {
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberCommandService.completeOnboarding(MEMBER_ID, onboarding("닉네임")))
                .isInstanceOfSatisfying(MemberException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND));
    }

    @Test
    void 이미_온보딩을_완료한_회원이면_예외가_발생한다() {
        Member member = mock(Member.class);
        when(member.isOnboarded()).thenReturn(true);
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> memberCommandService.completeOnboarding(MEMBER_ID, onboarding("닉네임")))
                .isInstanceOfSatisfying(MemberException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(MemberErrorCode.ALREADY_ONBOARDED));

        verify(member, never()).completeOnboarding(any());
    }

    @Test
    void 닉네임이_이미_사용중이면_예외가_발생한다() {
        Member member = mock(Member.class);
        when(member.isOnboarded()).thenReturn(false);
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(memberQueryService.isNicknameAvailable("닉네임")).thenReturn(false);

        assertThatThrownBy(() -> memberCommandService.completeOnboarding(MEMBER_ID, onboarding("닉네임")))
                .isInstanceOfSatisfying(MemberException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(MemberErrorCode.NICKNAME_DUPLICATE));

        verify(member, never()).completeOnboarding(any());
    }

    @Test
    void 닉네임이_금칙어이면_예외가_발생한다() {
        Member member = mock(Member.class);
        when(member.isOnboarded()).thenReturn(false);
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(memberQueryService.isNicknameForbidden("플리맵사용자1")).thenReturn(true);

        assertThatThrownBy(() -> memberCommandService.completeOnboarding(MEMBER_ID, onboarding("플리맵사용자1")))
                .isInstanceOfSatisfying(MemberException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(MemberErrorCode.NICKNAME_FORBIDDEN_WORD));

        verify(memberQueryService, never()).isNicknameAvailable(any());
        verify(member, never()).completeOnboarding(any());
    }

    @Test
    void 정상_요청이면_온보딩을_완료하고_회원을_반환한다() {
        Member member = mock(Member.class);
        when(member.isOnboarded()).thenReturn(false);
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(memberQueryService.isNicknameAvailable("닉네임")).thenReturn(true);

        MemberReqDTO.Onboarding request = onboarding("닉네임");
        Member result = memberCommandService.completeOnboarding(MEMBER_ID, request);

        assertThat(result).isSameAs(member);
        verify(member).completeOnboarding("닉네임");
        verify(memberRepository).flush();
    }

    @Test
    void 동시_요청으로_유니크_제약이_깨지면_닉네임_중복_예외로_변환한다() {
        Member member = mock(Member.class);
        when(member.isOnboarded()).thenReturn(false);
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(memberQueryService.isNicknameAvailable("닉네임")).thenReturn(true);
        doThrow(new DataIntegrityViolationException("duplicate")).when(memberRepository).flush();

        assertThatThrownBy(() -> memberCommandService.completeOnboarding(MEMBER_ID, onboarding("닉네임")))
                .isInstanceOfSatisfying(MemberException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(MemberErrorCode.NICKNAME_DUPLICATE));
    }

    @Test
    void 프로필_수정_시_존재하지_않는_회원이면_예외가_발생한다() {
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberCommandService.updateProfile(MEMBER_ID, updateProfile("새닉네임", null, null)))
                .isInstanceOfSatisfying(MemberException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND));
    }

    @Test
    void 프로필_수정_시_닉네임을_다른_값으로_변경하는데_이미_사용중이면_예외가_발생한다() {
        Member member = mock(Member.class);
        when(member.getNickname()).thenReturn("기존닉네임");
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(memberQueryService.isNicknameAvailable("새닉네임")).thenReturn(false);

        assertThatThrownBy(() -> memberCommandService.updateProfile(MEMBER_ID, updateProfile("새닉네임", null, null)))
                .isInstanceOfSatisfying(MemberException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(MemberErrorCode.NICKNAME_DUPLICATE));

        verify(member, never()).updateProfile(any(), any(), any());
    }

    @Test
    void 프로필_수정_시_닉네임을_금칙어로_변경하면_예외가_발생한다() {
        Member member = mock(Member.class);
        when(member.getNickname()).thenReturn("기존닉네임");
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(memberQueryService.isNicknameForbidden("플리맵사용자1")).thenReturn(true);

        assertThatThrownBy(() -> memberCommandService.updateProfile(MEMBER_ID, updateProfile("플리맵사용자1", null, null)))
                .isInstanceOfSatisfying(MemberException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(MemberErrorCode.NICKNAME_FORBIDDEN_WORD));

        verify(memberQueryService, never()).isNicknameAvailable(any());
        verify(member, never()).updateProfile(any(), any(), any());
    }

    @Test
    void 닉네임을_기존과_대소문자만_다르게_요청하면_중복_검사_없이_수정된다() {
        Member member = mock(Member.class);
        when(member.getNickname()).thenReturn("plimap");
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));

        memberCommandService.updateProfile(MEMBER_ID, updateProfile("PLIMAP", null, null));

        verify(memberQueryService, never()).isNicknameAvailable(any());
        verify(member).updateProfile("PLIMAP", null, null);
    }

    @Test
    void 닉네임을_보내지_않으면_중복_검사_없이_나머지_필드만_수정된다() {
        Member member = mock(Member.class);
        when(member.getNickname()).thenReturn("기존닉네임");
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));

        memberCommandService.updateProfile(MEMBER_ID, updateProfile(null, "새이름", "새소개"));

        verify(memberQueryService, never()).isNicknameAvailable(any());
        verify(member).updateProfile(null, "새이름", "새소개");
    }

    @Test
    void 정상_요청이면_프로필을_수정하고_수정된_프로필_정보를_반환한다() {
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(MEMBER_ID);
        when(member.getNickname()).thenReturn("기존닉네임");
        when(member.getProfileImageObjectKey()).thenReturn("key");
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(memberQueryService.isNicknameAvailable("새닉네임")).thenReturn(true);
        when(profileImageStorage.getPublicUrlOrNull("key")).thenReturn("https://example.com/key");

        MemberReqDTO.UpdateProfile request = updateProfile("새닉네임", "새이름", "새소개");
        MemberResponse.Profile result = memberCommandService.updateProfile(MEMBER_ID, request);

        assertThat(result.id()).isEqualTo(MEMBER_ID);
        assertThat(result.profileImageUrl()).isEqualTo("https://example.com/key");
        verify(member).updateProfile("새닉네임", "새이름", "새소개");
        verify(memberRepository).flush();
    }

    @Test
    void 이름에_빈_문자열을_보내면_서비스를_거쳐_실제로_이름이_삭제된다() {
        Member member = Member.builder()
                .nickname("기존닉네임")
                .name("이예림")
                .build();
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));

        memberCommandService.updateProfile(MEMBER_ID, updateProfile(null, "", null));

        assertThat(member.getName()).isNull();
    }

    @Test
    void 이름에_null을_보내면_서비스를_거쳐도_기존_이름이_유지된다() {
        Member member = Member.builder()
                .nickname("기존닉네임")
                .name("이예림")
                .build();
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));

        memberCommandService.updateProfile(MEMBER_ID, updateProfile(null, null, null));

        assertThat(member.getName()).isEqualTo("이예림");
    }

    @Test
    void 프로필_수정_중_동시_요청으로_유니크_제약이_깨지면_닉네임_중복_예외로_변환한다() {
        Member member = mock(Member.class);
        when(member.getNickname()).thenReturn("기존닉네임");
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(memberQueryService.isNicknameAvailable("새닉네임")).thenReturn(true);
        doThrow(new DataIntegrityViolationException("duplicate")).when(memberRepository).flush();

        assertThatThrownBy(() -> memberCommandService.updateProfile(MEMBER_ID, updateProfile("새닉네임", null, null)))
                .isInstanceOfSatisfying(MemberException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(MemberErrorCode.NICKNAME_DUPLICATE));
    }

    @Test
    void 닉네임을_바꾸지_않았는데_다른_이유로_제약_위반이_나면_원본_예외를_그대로_던진다() {
        Member member = mock(Member.class);
        when(member.getNickname()).thenReturn("기존닉네임");
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        DataIntegrityViolationException original = new DataIntegrityViolationException("unexpected constraint violation");
        doThrow(original).when(memberRepository).flush();

        assertThatThrownBy(() -> memberCommandService.updateProfile(MEMBER_ID, updateProfile(null, "새이름", null)))
                .isSameAs(original);

        verify(memberQueryService, never()).isNicknameAvailable(any());
    }

    @Test
    void 자기_자신을_팔로우하면_예외가_발생한다() {
        assertThatThrownBy(() -> memberCommandService.follow(MEMBER_ID, MEMBER_ID))
                .isInstanceOfSatisfying(MemberException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(MemberErrorCode.CANNOT_FOLLOW_SELF));

        verify(memberRepository, never()).findByIdAndDeletedAtIsNull(any());
    }

    @Test
    void 팔로우를_요청한_회원이_탈퇴한_회원이면_예외가_발생한다() {
        when(memberRepository.findByIdAndDeletedAtIsNull(MEMBER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberCommandService.follow(MEMBER_ID, OTHER_MEMBER_ID))
                .isInstanceOfSatisfying(MemberException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND));

        verify(memberRepository, never()).findByIdAndDeletedAtIsNull(OTHER_MEMBER_ID);
        verify(memberFollowRepository, never()).existsById(any());
    }

    @Test
    void 팔로우_대상_회원이_존재하지_않으면_예외가_발생한다() {
        Member follower = mock(Member.class);
        when(memberRepository.findByIdAndDeletedAtIsNull(MEMBER_ID)).thenReturn(Optional.of(follower));
        when(memberRepository.findByIdAndDeletedAtIsNull(OTHER_MEMBER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberCommandService.follow(MEMBER_ID, OTHER_MEMBER_ID))
                .isInstanceOfSatisfying(MemberException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND));

        verify(memberFollowRepository, never()).existsById(any());
    }

    @Test
    void 이미_팔로우_중이면_예외가_발생한다() {
        Member follower = mock(Member.class);
        Member following = mock(Member.class);
        when(memberRepository.findByIdAndDeletedAtIsNull(MEMBER_ID)).thenReturn(Optional.of(follower));
        when(memberRepository.findByIdAndDeletedAtIsNull(OTHER_MEMBER_ID)).thenReturn(Optional.of(following));
        when(memberFollowRepository.existsById(new MemberFollowId(MEMBER_ID, OTHER_MEMBER_ID))).thenReturn(true);

        assertThatThrownBy(() -> memberCommandService.follow(MEMBER_ID, OTHER_MEMBER_ID))
                .isInstanceOfSatisfying(MemberException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(MemberErrorCode.ALREADY_FOLLOWING));

        verify(memberFollowRepository, never()).saveAndFlush(any());
    }

    @Test
    void 정상_요청이면_팔로우한다() {
        Member follower = mock(Member.class);
        when(follower.getId()).thenReturn(MEMBER_ID);
        Member following = mock(Member.class);
        when(following.getId()).thenReturn(OTHER_MEMBER_ID);
        when(memberRepository.findByIdAndDeletedAtIsNull(MEMBER_ID)).thenReturn(Optional.of(follower));
        when(memberRepository.findByIdAndDeletedAtIsNull(OTHER_MEMBER_ID)).thenReturn(Optional.of(following));
        when(memberFollowRepository.existsById(new MemberFollowId(MEMBER_ID, OTHER_MEMBER_ID))).thenReturn(false);

        memberCommandService.follow(MEMBER_ID, OTHER_MEMBER_ID);

        verify(memberFollowRepository).saveAndFlush(any(MemberFollow.class));
    }

    @Test
    void 동시_요청으로_복합_PK가_깨지면_이미_팔로우_중_예외로_변환한다() {
        Member follower = mock(Member.class);
        when(follower.getId()).thenReturn(MEMBER_ID);
        Member following = mock(Member.class);
        when(following.getId()).thenReturn(OTHER_MEMBER_ID);
        when(memberRepository.findByIdAndDeletedAtIsNull(MEMBER_ID)).thenReturn(Optional.of(follower));
        when(memberRepository.findByIdAndDeletedAtIsNull(OTHER_MEMBER_ID)).thenReturn(Optional.of(following));
        when(memberFollowRepository.existsById(new MemberFollowId(MEMBER_ID, OTHER_MEMBER_ID))).thenReturn(false);
        doThrow(new DataIntegrityViolationException("duplicate")).when(memberFollowRepository).saveAndFlush(any(MemberFollow.class));

        assertThatThrownBy(() -> memberCommandService.follow(MEMBER_ID, OTHER_MEMBER_ID))
                .isInstanceOfSatisfying(MemberException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(MemberErrorCode.ALREADY_FOLLOWING));
    }

    @Test
    void 자기_자신을_언팔로우하면_예외가_발생한다() {
        assertThatThrownBy(() -> memberCommandService.unfollow(MEMBER_ID, MEMBER_ID))
                .isInstanceOfSatisfying(MemberException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(MemberErrorCode.CANNOT_UNFOLLOW_SELF));

        verify(memberFollowRepository, never()).deleteByIdFollowerIdAndIdFollowingId(any(), any());
    }

    @Test
    void 팔로우_중이_아니면_언팔로우_시_예외가_발생한다() {
        when(memberFollowRepository.deleteByIdFollowerIdAndIdFollowingId(MEMBER_ID, OTHER_MEMBER_ID)).thenReturn(0L);

        assertThatThrownBy(() -> memberCommandService.unfollow(MEMBER_ID, OTHER_MEMBER_ID))
                .isInstanceOfSatisfying(MemberException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(MemberErrorCode.NOT_FOLLOWING));
    }

    @Test
    void 정상_요청이면_언팔로우한다() {
        when(memberFollowRepository.deleteByIdFollowerIdAndIdFollowingId(MEMBER_ID, OTHER_MEMBER_ID)).thenReturn(1L);

        memberCommandService.unfollow(MEMBER_ID, OTHER_MEMBER_ID);

        verify(memberFollowRepository).deleteByIdFollowerIdAndIdFollowingId(MEMBER_ID, OTHER_MEMBER_ID);
    }

    @Test
    void 존재하지_않는_회원의_프로필_이미지를_업로드하면_예외가_발생한다() {
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberCommandService.uploadProfileImage(MEMBER_ID, webpFile()))
                .isInstanceOfSatisfying(MemberException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND));
    }

    @Test
    void 기존_이미지가_없던_회원이_최초로_업로드하면_삭제를_호출하지_않는다() {
        Member member = mock(Member.class);
        when(member.getProfileImageObjectKey()).thenReturn(null);
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(profileImageObjectKeyGenerator.generate(MEMBER_ID)).thenReturn("members/1/new.webp");
        when(profileImageStorage.getPublicUrl("members/1/new.webp"))
                .thenReturn(URI.create("https://project.supabase.co/storage/v1/object/public/profile-images/members/1/new.webp"));

        MemberResponse.ProfileImage result = memberCommandService.uploadProfileImage(MEMBER_ID, webpFile());

        assertThat(result.objectKey()).isEqualTo("members/1/new.webp");
        verify(member).updateProfileImage("members/1/new.webp");
        verify(memberRepository).saveAndFlush(member);
        verify(profileImageStorage, never()).delete(any());
    }

    @Test
    void 기존_이미지가_있던_회원이_교체_업로드하면_이전_이미지를_삭제한다() {
        Member member = mock(Member.class);
        when(member.getProfileImageObjectKey()).thenReturn("members/1/old.webp");
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(profileImageObjectKeyGenerator.generate(MEMBER_ID)).thenReturn("members/1/new.webp");
        when(profileImageStorage.getPublicUrl("members/1/new.webp"))
                .thenReturn(URI.create("https://project.supabase.co/storage/v1/object/public/profile-images/members/1/new.webp"));

        memberCommandService.uploadProfileImage(MEMBER_ID, webpFile());

        InOrder replacementOrder = inOrder(profileImageStorage, member, memberRepository);
        replacementOrder.verify(profileImageStorage)
                .upload(eq("members/1/new.webp"), any(), any());
        replacementOrder.verify(member).updateProfileImage("members/1/new.webp");
        replacementOrder.verify(memberRepository).saveAndFlush(member);
        replacementOrder.verify(profileImageStorage).delete("members/1/old.webp");
    }

    @Test
    void 프로필_이미지_DB_저장에_실패하면_업로드한_신규_이미지를_삭제한다() {
        Member member = mock(Member.class);
        RuntimeException persistenceFailure = new RuntimeException("DB 저장 실패");
        when(member.getProfileImageObjectKey()).thenReturn("members/1/old.webp");
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(profileImageObjectKeyGenerator.generate(MEMBER_ID)).thenReturn("members/1/new.webp");
        when(memberRepository.saveAndFlush(member)).thenThrow(persistenceFailure);

        assertThatThrownBy(() -> memberCommandService.uploadProfileImage(MEMBER_ID, webpFile()))
                .isSameAs(persistenceFailure);

        InOrder replacementOrder = inOrder(profileImageStorage, member, memberRepository);
        replacementOrder.verify(profileImageStorage)
                .upload(eq("members/1/new.webp"), any(), any());
        replacementOrder.verify(member).updateProfileImage("members/1/new.webp");
        replacementOrder.verify(memberRepository).saveAndFlush(member);
        replacementOrder.verify(profileImageStorage).delete("members/1/new.webp");
        verify(profileImageStorage, never()).delete("members/1/old.webp");
    }

    @Test
    void DB_저장과_신규_이미지_삭제가_모두_실패하면_원래_DB_예외를_유지한다() {
        Member member = mock(Member.class);
        RuntimeException persistenceFailure = new RuntimeException("DB 저장 실패");
        ProfileImageStorageException cleanupFailure = new ProfileImageStorageException(
                "신규 이미지 삭제 실패",
                new RuntimeException()
        );
        when(member.getProfileImageObjectKey()).thenReturn("members/1/old.webp");
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(profileImageObjectKeyGenerator.generate(MEMBER_ID)).thenReturn("members/1/new.webp");
        when(memberRepository.saveAndFlush(member)).thenThrow(persistenceFailure);
        doThrow(cleanupFailure).when(profileImageStorage).delete("members/1/new.webp");

        assertThatThrownBy(() -> memberCommandService.uploadProfileImage(MEMBER_ID, webpFile()))
                .isSameAs(persistenceFailure)
                .satisfies(exception ->
                        assertThat(exception.getSuppressed()).containsExactly(cleanupFailure));

        verify(profileImageStorage).delete("members/1/new.webp");
        verify(profileImageStorage, never()).delete("members/1/old.webp");
    }

    @Test
    void 이전_이미지_삭제가_실패해도_업로드_자체는_성공한다() {
        Member member = mock(Member.class);
        when(member.getProfileImageObjectKey()).thenReturn("members/1/old.webp");
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(profileImageObjectKeyGenerator.generate(MEMBER_ID)).thenReturn("members/1/new.webp");
        when(profileImageStorage.getPublicUrl("members/1/new.webp"))
                .thenReturn(URI.create("https://project.supabase.co/storage/v1/object/public/profile-images/members/1/new.webp"));
        doThrow(new ProfileImageStorageException("실패", new RuntimeException()))
                .when(profileImageStorage).delete("members/1/old.webp");

        MemberResponse.ProfileImage result = memberCommandService.uploadProfileImage(MEMBER_ID, webpFile());

        assertThat(result.objectKey()).isEqualTo("members/1/new.webp");
    }

    @Test
    void 스토리지_업로드에_실패하면_도메인_예외로_변환한다() {
        Member member = mock(Member.class);
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(profileImageObjectKeyGenerator.generate(MEMBER_ID)).thenReturn("members/1/new.webp");
        doThrow(new ProfileImageStorageException("실패", new RuntimeException()))
                .when(profileImageStorage).upload(eq("members/1/new.webp"), any(), any());

        assertThatThrownBy(() -> memberCommandService.uploadProfileImage(MEMBER_ID, webpFile()))
                .isInstanceOfSatisfying(MemberException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(MemberErrorCode.PROFILE_IMAGE_UPLOAD_FAILED));

        verify(member, never()).updateProfileImage(any());
    }

    @Test
    void 프로필_이미지를_제거한다() {
        Member member = mock(Member.class);
        when(member.getProfileImageObjectKey()).thenReturn("members/1/old.webp");
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));

        memberCommandService.removeProfileImage(MEMBER_ID);

        InOrder order = inOrder(member, memberRepository, profileImageStorage);
        order.verify(member).removeProfileImage();
        order.verify(memberRepository).saveAndFlush(member);
        order.verify(profileImageStorage).delete("members/1/old.webp");
    }

    @Test
    void 이미_프로필_이미지가_없으면_제거_요청은_예외가_발생한다() {
        Member member = mock(Member.class);
        when(member.getProfileImageObjectKey()).thenReturn(null);
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> memberCommandService.removeProfileImage(MEMBER_ID))
                .isInstanceOfSatisfying(MemberException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(MemberErrorCode.PROFILE_IMAGE_NOT_FOUND));

        verify(member, never()).removeProfileImage();
        verify(memberRepository, never()).saveAndFlush(any());
        verify(profileImageStorage, never()).delete(any());
    }

    @Test
    void 존재하지_않는_회원의_프로필_이미지는_제거할_수_없다() {
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberCommandService.removeProfileImage(MEMBER_ID))
                .isInstanceOfSatisfying(MemberException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND));
    }

    @Test
    void 스토리지_삭제가_실패해도_프로필_이미지_제거_자체는_성공한다() {
        Member member = mock(Member.class);
        when(member.getProfileImageObjectKey()).thenReturn("members/1/old.webp");
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        doThrow(new ProfileImageStorageException("실패", new RuntimeException()))
                .when(profileImageStorage).delete("members/1/old.webp");

        memberCommandService.removeProfileImage(MEMBER_ID);

        verify(member).removeProfileImage();
        verify(memberRepository).saveAndFlush(member);
    }

    @Test
    void 빈_파일이면_스토리지를_호출하지_않고_예외가_발생한다() {
        MockMultipartFile emptyFile = new MockMultipartFile("image", "empty.webp", "image/webp", new byte[0]);

        assertThatThrownBy(() -> memberCommandService.uploadProfileImage(MEMBER_ID, emptyFile))
                .isInstanceOfSatisfying(MemberException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(MemberErrorCode.INVALID_PROFILE_IMAGE));

        verify(memberRepository, never()).findById(any());
        verify(profileImageStorage, never()).upload(any(), any(), any());
    }

    @Test
    void 콘텐츠_타입이_webp가_아니면_스토리지를_호출하지_않고_예외가_발생한다() {
        MockMultipartFile pngFile = new MockMultipartFile("image", "profile.png", "image/png", WEBP_CONTENT);

        assertThatThrownBy(() -> memberCommandService.uploadProfileImage(MEMBER_ID, pngFile))
                .isInstanceOfSatisfying(MemberException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(MemberErrorCode.INVALID_PROFILE_IMAGE));

        verify(profileImageStorage, never()).upload(any(), any(), any());
    }

    @Test
    void 매직바이트가_webp_형식이_아니면_스토리지를_호출하지_않고_예외가_발생한다() {
        byte[] fakeContent = "not a real webp file".getBytes();
        MockMultipartFile fakeFile = new MockMultipartFile("image", "fake.webp", "image/webp", fakeContent);

        assertThatThrownBy(() -> memberCommandService.uploadProfileImage(MEMBER_ID, fakeFile))
                .isInstanceOfSatisfying(MemberException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(MemberErrorCode.INVALID_PROFILE_IMAGE));

        verify(profileImageStorage, never()).upload(any(), any(), any());
    }

    @Test
    void 매직바이트_검증에_필요한_최소_길이보다_짧으면_스토리지를_호출하지_않고_예외가_발생한다() {
        byte[] tooShortContent = {1, 2, 3};
        MockMultipartFile tooShortFile = new MockMultipartFile("image", "short.webp", "image/webp", tooShortContent);

        assertThatThrownBy(() -> memberCommandService.uploadProfileImage(MEMBER_ID, tooShortFile))
                .isInstanceOfSatisfying(MemberException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(MemberErrorCode.INVALID_PROFILE_IMAGE));

        verify(profileImageStorage, never()).upload(any(), any(), any());
    }

    @Test
    void 파일_크기가_제한을_초과하면_스토리지를_호출하지_않고_예외가_발생한다() {
        byte[] oversized = new byte[6 * 1024 * 1024];
        System.arraycopy(WEBP_CONTENT, 0, oversized, 0, WEBP_CONTENT.length);
        MockMultipartFile oversizedFile = new MockMultipartFile("image", "big.webp", "image/webp", oversized);

        assertThatThrownBy(() -> memberCommandService.uploadProfileImage(MEMBER_ID, oversizedFile))
                .isInstanceOfSatisfying(MemberException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(MemberErrorCode.INVALID_PROFILE_IMAGE));

        verify(profileImageStorage, never()).upload(any(), any(), any());
    }

    @Test
    void 회원_탈퇴에_성공하면_닉네임을_마스킹하고_연관_데이터를_정리한다() {
        Member member = Member.builder()
                .nickname("예림")
                .introduction("소개")
                .profileImageObjectKey("old-key")
                .build();
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        when(memberQueryService.getActiveMember(MEMBER_ID)).thenReturn(member);

        memberCommandService.withdraw(MEMBER_ID, null);

        assertThat(member.getNickname()).isEqualTo("플리맵사용자" + MEMBER_ID);
        assertThat(member.getWithdrawnNickname()).isEqualTo("예림");
        assertThat(member.getIntroduction()).isNull();
        assertThat(member.getProfileImageObjectKey()).isNull();
        assertThat(member.getStatus()).isEqualTo(MemberStatus.WITHDRAWN);
        assertThat(member.getWithdrawalReason()).isEqualTo(WithdrawalReason.VOLUNTARY);
        assertThat(member.isDeleted()).isTrue();

        verify(memberFollowRepository).deleteByIdFollowerId(MEMBER_ID);
        verify(memberFollowRepository).deleteByIdFollowingId(MEMBER_ID);
        verify(socialAccountCommandService).unlink(MEMBER_ID, null);
        verify(socialAccountCommandService).deleteByMemberId(MEMBER_ID);
        verify(profileImageStorage, never()).delete(any());
        verify(eventPublisher).publishEvent(new MemberWithdrawnEvent(MEMBER_ID, "old-key"));
    }

    @Test
    void 소셜_연결을_끊지_못하면_탈퇴하지_않고_재로그인_예외를_던진다() {
        Member member = Member.builder().nickname("예림").build();
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        when(memberQueryService.getActiveMember(MEMBER_ID)).thenReturn(member);
        doThrow(new AuthException(AuthErrorCode.APPLE_REAUTH_REQUIRED))
                .when(socialAccountCommandService).unlink(MEMBER_ID, null);

        assertThatThrownBy(() -> memberCommandService.withdraw(MEMBER_ID, null))
                .isInstanceOfSatisfying(AuthException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.APPLE_REAUTH_REQUIRED));

        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        verify(socialAccountCommandService, never()).deleteByMemberId(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void 탈퇴_시_프로필_이미지가_없으면_objectKey가_null인_이벤트를_발행한다() {
        Member member = Member.builder().nickname("예림").build();
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        when(memberQueryService.getActiveMember(MEMBER_ID)).thenReturn(member);

        memberCommandService.withdraw(MEMBER_ID, null);

        verify(eventPublisher).publishEvent(new MemberWithdrawnEvent(MEMBER_ID, null));
    }

    @Test
    void 존재하지_않는_회원을_탈퇴시키면_예외가_발생한다() {
        when(memberQueryService.getActiveMember(MEMBER_ID))
                .thenThrow(new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));

        assertThatThrownBy(() -> memberCommandService.withdraw(MEMBER_ID, null))
                .isInstanceOfSatisfying(MemberException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND));
    }

    @Test
    void 마스킹_닉네임이_다른_활성_회원의_닉네임과_겹치면_닉네임_중복_예외로_변환한다() {
        Member member = Member.builder().nickname("예림").build();
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        when(memberQueryService.getActiveMember(MEMBER_ID)).thenReturn(member);
        doThrow(new DataIntegrityViolationException("duplicate"))
                .when(memberRepository).saveAndFlush(any(Member.class));

        assertThatThrownBy(() -> memberCommandService.withdraw(MEMBER_ID, null))
                .isInstanceOfSatisfying(MemberException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(MemberErrorCode.NICKNAME_DUPLICATE));

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void 관리자가_1일_제재를_선택하면_1일_정지되고_사유가_스냅샷된다() {
        Member member = Member.builder().nickname("예림").build();
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        when(memberRepository.findByIdAndDeletedAtIsNullForUpdate(MEMBER_ID)).thenReturn(Optional.of(member));

        Instant before = Instant.now();
        boolean withdrawn = memberCommandService.applySanction(
                MEMBER_ID, SuspensionPeriod.ONE_DAY, ReportCategory.ABUSE_OR_HATE_SPEECH, null);
        Instant after = Instant.now();

        assertThat(withdrawn).isFalse();
        assertThat(member.getPenaltyPoint()).isEqualTo(1);
        assertThat(member.getStatus()).isEqualTo(MemberStatus.SUSPENDED);
        assertThat(member.getSuspendedUntil()).isBetween(before.plus(1, ChronoUnit.DAYS), after.plus(1, ChronoUnit.DAYS));
        assertThat(member.getLastPenaltyCategory()).isEqualTo(ReportCategory.ABUSE_OR_HATE_SPEECH);
        assertThat(member.getLastPenaltyDetail()).isNull();
        assertThat(member.getLastPenaltyPeriod()).isEqualTo(SuspensionPeriod.ONE_DAY);
        verify(memberRepository).save(member);
        verify(memberFollowRepository, never()).deleteByIdFollowerId(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void 이미_1점인_회원이_3일_제재를_받으면_2점이_되어_3일_정지된다() {
        Member member = Member.builder().nickname("예림").build();
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        ReflectionTestUtils.setField(member, "penaltyPoint", 1);
        ReflectionTestUtils.setField(member, "status", MemberStatus.SUSPENDED);
        when(memberRepository.findByIdAndDeletedAtIsNullForUpdate(MEMBER_ID)).thenReturn(Optional.of(member));

        Instant before = Instant.now();
        memberCommandService.applySanction(MEMBER_ID, SuspensionPeriod.THREE_DAYS, ReportCategory.OBSCENE_OR_HARMFUL, null);
        Instant after = Instant.now();

        assertThat(member.getPenaltyPoint()).isEqualTo(2);
        assertThat(member.getStatus()).isEqualTo(MemberStatus.SUSPENDED);
        assertThat(member.getSuspendedUntil()).isBetween(before.plus(3, ChronoUnit.DAYS), after.plus(3, ChronoUnit.DAYS));
        assertThat(member.getLastPenaltyPeriod()).isEqualTo(SuspensionPeriod.THREE_DAYS);
    }

    @Test
    void 이미_2점인_회원이_5일_제재를_받으면_3점이_되어_5일_정지된다() {
        Member member = Member.builder().nickname("예림").build();
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        ReflectionTestUtils.setField(member, "penaltyPoint", 2);
        ReflectionTestUtils.setField(member, "status", MemberStatus.SUSPENDED);
        when(memberRepository.findByIdAndDeletedAtIsNullForUpdate(MEMBER_ID)).thenReturn(Optional.of(member));

        Instant before = Instant.now();
        memberCommandService.applySanction(MEMBER_ID, SuspensionPeriod.FIVE_DAYS, ReportCategory.PERSONAL_INFORMATION_EXPOSURE, null);
        Instant after = Instant.now();

        assertThat(member.getPenaltyPoint()).isEqualTo(3);
        assertThat(member.getStatus()).isEqualTo(MemberStatus.SUSPENDED);
        assertThat(member.getSuspendedUntil()).isBetween(before.plus(5, ChronoUnit.DAYS), after.plus(5, ChronoUnit.DAYS));
        assertThat(member.getLastPenaltyPeriod()).isEqualTo(SuspensionPeriod.FIVE_DAYS);
    }

    @Test
    void 누적_4점째_제재를_받으면_기간을_불문하고_자동_탈퇴로_전환되고_캐스케이드가_실행된다() {
        Member member = Member.builder().nickname("예림").profileImageObjectKey("old-key").build();
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        ReflectionTestUtils.setField(member, "penaltyPoint", 3);
        ReflectionTestUtils.setField(member, "status", MemberStatus.SUSPENDED);
        when(memberRepository.findByIdAndDeletedAtIsNullForUpdate(MEMBER_ID)).thenReturn(Optional.of(member));

        boolean withdrawn = memberCommandService.applySanction(
                MEMBER_ID, SuspensionPeriod.ONE_DAY, ReportCategory.OTHER, "반복 위반");

        assertThat(withdrawn).isTrue();
        assertThat(member.getPenaltyPoint()).isEqualTo(4);
        assertThat(member.getStatus()).isEqualTo(MemberStatus.WITHDRAWN);
        assertThat(member.getWithdrawalReason()).isEqualTo(WithdrawalReason.PENALTY);
        assertThat(member.getSuspendedUntil()).isNull();
        assertThat(member.getNickname()).isEqualTo("플리맵사용자" + MEMBER_ID);
        assertThat(member.getWithdrawnNickname()).isEqualTo("예림");
        assertThat(member.isDeleted()).isTrue();
        assertThat(member.getLastPenaltyCategory()).isEqualTo(ReportCategory.OTHER);
        assertThat(member.getLastPenaltyDetail()).isEqualTo("반복 위반");
        assertThat(member.getLastPenaltyPeriod()).isEqualTo(SuspensionPeriod.ONE_DAY);

        verify(memberFollowRepository).deleteByIdFollowerId(MEMBER_ID);
        verify(memberFollowRepository).deleteByIdFollowingId(MEMBER_ID);
        verify(memberRepository).saveAndFlush(member);
        verify(socialAccountCommandService).unlinkQuietly(MEMBER_ID);
        verify(socialAccountCommandService, never()).deleteByMemberId(any());
        verify(eventPublisher).publishEvent(new MemberWithdrawnEvent(MEMBER_ID, "old-key"));
    }

    @Test
    void 관리자가_영구_제재를_선택하면_누적_점수가_4점_미만이어도_즉시_자동_탈퇴로_전환된다() {
        Member member = Member.builder().nickname("예림").profileImageObjectKey("old-key").build();
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        when(memberRepository.findByIdAndDeletedAtIsNullForUpdate(MEMBER_ID)).thenReturn(Optional.of(member));

        boolean withdrawn = memberCommandService.applySanction(
                MEMBER_ID, SuspensionPeriod.PERMANENT, ReportCategory.PERSONAL_INFORMATION_EXPOSURE, null);

        assertThat(withdrawn).isTrue();
        assertThat(member.getPenaltyPoint()).isEqualTo(1);
        assertThat(member.getStatus()).isEqualTo(MemberStatus.WITHDRAWN);
        assertThat(member.getWithdrawalReason()).isEqualTo(WithdrawalReason.PENALTY);
        assertThat(member.getLastPenaltyPeriod()).isEqualTo(SuspensionPeriod.PERMANENT);
        verify(memberFollowRepository).deleteByIdFollowerId(MEMBER_ID);
        verify(eventPublisher).publishEvent(new MemberWithdrawnEvent(MEMBER_ID, "old-key"));
    }

    @Test
    void 존재하지_않는_회원에게_제재를_부여하면_예외가_발생한다() {
        when(memberRepository.findByIdAndDeletedAtIsNullForUpdate(MEMBER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberCommandService.applySanction(
                MEMBER_ID, SuspensionPeriod.ONE_DAY, ReportCategory.ABUSE_OR_HATE_SPEECH, null))
                .isInstanceOfSatisfying(MemberException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND));
    }

    @Test
    void 정지_해제시_ACTIVE로_복귀하고_정지일이_초기화된다() {
        Member member = Member.builder().nickname("예림").build();
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        ReflectionTestUtils.setField(member, "status", MemberStatus.SUSPENDED);
        ReflectionTestUtils.setField(member, "suspendedUntil", Instant.now().minusSeconds(60));
        when(memberRepository.findByIdAndDeletedAtIsNull(MEMBER_ID)).thenReturn(Optional.of(member));

        memberCommandService.liftSuspension(MEMBER_ID);

        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(member.getSuspendedUntil()).isNull();
        verify(memberRepository).save(member);
    }

    @Test
    void 벌점_프로필_닉네임_치환시_닉네임과_신고누적이_초기화된다() {
        Member member = Member.builder().nickname("기존닉네임").build();
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        ReflectionTestUtils.setField(member, "reportCount", 12);
        when(memberRepository.findByIdAndDeletedAtIsNull(MEMBER_ID)).thenReturn(Optional.of(member));

        memberCommandService.replacePenalizedNickname(MEMBER_ID, "참새");

        assertThat(member.getNickname()).isEqualTo("참새");
        assertThat(member.getReportCount()).isEqualTo(0);
        verify(memberRepository).saveAndFlush(member);
    }

    @Test
    void 벌점_미부여시_신고누적만_초기화된다() {
        Member member = Member.builder().nickname("예림").build();
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        ReflectionTestUtils.setField(member, "reportCount", 10);
        when(memberRepository.findByIdAndDeletedAtIsNull(MEMBER_ID)).thenReturn(Optional.of(member));

        memberCommandService.resetReportCount(MEMBER_ID);

        assertThat(member.getReportCount()).isEqualTo(0);
        assertThat(member.getNickname()).isEqualTo("예림");
        verify(memberRepository).save(member);
    }

    @Test
    void 회원의_신고누적을_감소시킨다() {
        memberCommandService.decreaseReportCount(MEMBER_ID);

        verify(memberRepository).decreaseReportCount(MEMBER_ID);
    }

    @Test
    void 닉네임_강제_재생성시_벌점과_신고누적은_변경되지_않는다() {
        Member member = Member.builder().nickname("기존닉네임").build();
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        ReflectionTestUtils.setField(member, "reportCount", 3);
        ReflectionTestUtils.setField(member, "penaltyPoint", 1);
        when(memberRepository.findByIdAndDeletedAtIsNull(MEMBER_ID)).thenReturn(Optional.of(member));

        memberCommandService.regenerateNickname(MEMBER_ID, "참새");

        assertThat(member.getNickname()).isEqualTo("참새");
        assertThat(member.getReportCount()).isEqualTo(3);
        assertThat(member.getPenaltyPoint()).isEqualTo(1);
        verify(memberRepository).saveAndFlush(member);
    }

    @Test
    void 닉네임_강제_재생성시_새_닉네임이_이미_사용중이면_예외가_발생한다() {
        Member member = Member.builder().nickname("기존닉네임").build();
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        when(memberRepository.findByIdAndDeletedAtIsNull(MEMBER_ID)).thenReturn(Optional.of(member));
        when(memberRepository.saveAndFlush(member)).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> memberCommandService.regenerateNickname(MEMBER_ID, "참새"))
                .isInstanceOfSatisfying(MemberException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(MemberErrorCode.NICKNAME_DUPLICATE));
    }

    @Test
    void 탈퇴한_회원의_닉네임은_재생성할_수_없다() {
        when(memberRepository.findByIdAndDeletedAtIsNull(MEMBER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberCommandService.regenerateNickname(MEMBER_ID, "참새"))
                .isInstanceOfSatisfying(MemberException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND));
    }

    private MockMultipartFile webpFile() {
        return new MockMultipartFile("image", "profile.webp", "image/webp", WEBP_CONTENT);
    }

    private MemberReqDTO.Onboarding onboarding(String nickname) {
        MemberReqDTO.Onboarding request = new MemberReqDTO.Onboarding();
        ReflectionTestUtils.setField(request, "nickname", nickname);
        return request;
    }

    private MemberReqDTO.UpdateProfile updateProfile(String nickname, String name, String introduction) {
        return new MemberReqDTO.UpdateProfile(nickname, name, introduction);
    }
}
