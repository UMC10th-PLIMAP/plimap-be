package com.example.plimap.domain.member.service.command.impl;

import com.example.plimap.domain.auth.service.command.SocialAccountCommandService;
import com.example.plimap.domain.member.converter.MemberConverter;
import com.example.plimap.domain.member.dto.request.MemberReqDTO;
import com.example.plimap.domain.member.dto.response.MemberResponse;
import com.example.plimap.domain.member.entity.Member;
import com.example.plimap.domain.member.entity.MemberFollow;
import com.example.plimap.domain.member.entity.MemberFollowId;
import com.example.plimap.domain.member.enums.MemberStatus;
import com.example.plimap.domain.member.enums.SuspensionPeriod;
import com.example.plimap.domain.member.event.MemberFollowedEvent;
import com.example.plimap.domain.member.event.MemberWithdrawnEvent;
import com.example.plimap.domain.member.exception.MemberErrorCode;
import com.example.plimap.domain.member.exception.MemberException;
import com.example.plimap.domain.member.repository.MemberFollowRepository;
import com.example.plimap.domain.member.repository.MemberRepository;
import com.example.plimap.domain.member.service.command.MemberCommandService;
import com.example.plimap.domain.member.service.query.MemberQueryService;
import com.example.plimap.domain.report.enums.ReportCategory;
import com.example.plimap.global.external.storage.ProfileImageObjectKeyGenerator;
import com.example.plimap.global.external.storage.ProfileImageStorage;
import com.example.plimap.global.external.storage.ProfileImageStorageException;
import java.io.IOException;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberCommandServiceImpl implements MemberCommandService {

    private static final long MAX_PROFILE_IMAGE_SIZE = 5 * 1024 * 1024;
    private static final MediaType IMAGE_WEBP = MediaType.parseMediaType("image/webp");

    private final MemberRepository memberRepository;
    private final MemberFollowRepository memberFollowRepository;
    private final MemberQueryService memberQueryService;
    private final ApplicationEventPublisher eventPublisher;
    private final ProfileImageStorage profileImageStorage;
    private final ProfileImageObjectKeyGenerator profileImageObjectKeyGenerator;
    private final SocialAccountCommandService socialAccountCommandService;

    @Override
    @Transactional
    public Member completeOnboarding(Long memberId, MemberReqDTO.Onboarding request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));

        if (member.isOnboarded()) {
            throw new MemberException(MemberErrorCode.ALREADY_ONBOARDED);
        }

        if (memberQueryService.isNicknameForbidden(request.getNickname())) {
            throw new MemberException(MemberErrorCode.NICKNAME_FORBIDDEN_WORD);
        }
        if (!memberQueryService.isNicknameAvailable(request.getNickname())) {
            throw new MemberException(MemberErrorCode.NICKNAME_DUPLICATE);
        }

        try {
            member.completeOnboarding(request.getNickname());
            memberRepository.flush();
        } catch (DataIntegrityViolationException e) {
            // 동시에 같은 닉네임으로 온보딩을 완료하는 경우 사전 체크를 통과했더라도
            // DB의 대소문자 무시 유니크 인덱스(uk_member_nickname_ci)에서 최종적으로 걸러진다.
            throw new MemberException(MemberErrorCode.NICKNAME_DUPLICATE, e);
        }

        return member;
    }

    @Override
    @Transactional
    public MemberResponse.Profile updateProfile(Long memberId, MemberReqDTO.UpdateProfile request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));

        boolean nicknameChanged = request.nickname() != null
                && !request.nickname().equalsIgnoreCase(member.getNickname());
        if (nicknameChanged) {
            if (memberQueryService.isNicknameForbidden(request.nickname())) {
                throw new MemberException(MemberErrorCode.NICKNAME_FORBIDDEN_WORD);
            }
            if (!memberQueryService.isNicknameAvailable(request.nickname())) {
                throw new MemberException(MemberErrorCode.NICKNAME_DUPLICATE);
            }
        }

        try {
            member.updateProfile(request.nickname(), request.name(), request.introduction());
            memberRepository.flush();
        } catch (DataIntegrityViolationException e) {
            if (!nicknameChanged) {
                throw e;
            }
            // 동시에 같은 닉네임으로 변경하는 경우 사전 체크를 통과했더라도
            // DB의 대소문자 무시 유니크 인덱스(uk_member_nickname_ci)에서 최종적으로 걸러진다.
            throw new MemberException(MemberErrorCode.NICKNAME_DUPLICATE, e);
        }

        String profileImageUrl = profileImageStorage.getPublicUrlOrNull(member.getProfileImageObjectKey());
        return MemberConverter.toProfile(member, profileImageUrl);
    }

    @Override
    public MemberResponse.ProfileImage uploadProfileImage(Long memberId, MultipartFile image) {
        // 의도적으로 @Transactional을 붙이지 않는다: Supabase 업로드/삭제는 네트워크 I/O라
        // 트랜잭션으로 묶으면 DB 커넥션을 오래 점유하고, 커밋 전에 이전 이미지를 지우면
        // 커밋 실패 시 DB는 이전 objectKey를 가리키는데 실제 객체는 이미 삭제된 상태가 된다.
        // findById/saveAndFlush는 Spring Data JPA가 각각 자체 트랜잭션으로 짧게 처리하므로,
        // 이전 이미지 삭제는 이 DB 갱신이 실제로 커밋된 뒤에만 실행된다.
        validateImageMetadata(image);
        byte[] content = readContent(image);
        validateWebpSignature(content);

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));
        String oldObjectKey = member.getProfileImageObjectKey();

        String newObjectKey = profileImageObjectKeyGenerator.generate(memberId);
        try {
            profileImageStorage.upload(newObjectKey, content, IMAGE_WEBP);
        } catch (ProfileImageStorageException e) {
            throw new MemberException(MemberErrorCode.PROFILE_IMAGE_UPLOAD_FAILED, e);
        }

        member.updateProfileImage(newObjectKey);
        try {
            memberRepository.saveAndFlush(member);
        } catch (RuntimeException persistenceException) {
            deleteNewProfileImageAfterPersistenceFailure(newObjectKey, persistenceException);
            throw persistenceException;
        }

        if (oldObjectKey != null) {
            try {
                profileImageStorage.delete(oldObjectKey);
            } catch (ProfileImageStorageException e) {
                log.warn("이전 프로필 이미지 삭제 실패: objectKey={}", oldObjectKey, e);
            }
        }

        URI publicUrl = profileImageStorage.getPublicUrl(newObjectKey);
        return MemberConverter.toProfileImage(newObjectKey, publicUrl);
    }

    @Override
    public void removeProfileImage(Long memberId) {
        // uploadProfileImage와 동일한 이유로 @Transactional을 붙이지 않는다: 스토리지 삭제는
        // DB 갱신이 실제로 커밋된 뒤에만 실행되어야 커밋 실패 시 파일이 먼저 사라지는 불일치를 막는다.
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));
        String oldObjectKey = member.getProfileImageObjectKey();
        if (oldObjectKey == null) {
            throw new MemberException(MemberErrorCode.PROFILE_IMAGE_NOT_FOUND);
        }

        member.removeProfileImage();
        memberRepository.saveAndFlush(member);

        try {
            profileImageStorage.delete(oldObjectKey);
        } catch (ProfileImageStorageException e) {
            log.warn("프로필 이미지 삭제 실패: objectKey={}", oldObjectKey, e);
        }
    }

    private void deleteNewProfileImageAfterPersistenceFailure(
            String newObjectKey,
            RuntimeException persistenceException
    ) {
        try {
            profileImageStorage.delete(newObjectKey);
        } catch (ProfileImageStorageException cleanupException) {
            persistenceException.addSuppressed(cleanupException);
            log.warn(
                    "프로필 이미지 DB 반영 실패 후 신규 이미지 삭제 실패: objectKey={}",
                    newObjectKey,
                    cleanupException
            );
        }
    }

    private void validateImageMetadata(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new MemberException(MemberErrorCode.INVALID_PROFILE_IMAGE);
        }
        if (image.getSize() > MAX_PROFILE_IMAGE_SIZE) {
            throw new MemberException(MemberErrorCode.INVALID_PROFILE_IMAGE);
        }
        if (!IMAGE_WEBP.equals(parseContentType(image.getContentType()))) {
            throw new MemberException(MemberErrorCode.INVALID_PROFILE_IMAGE);
        }
    }

    private byte[] readContent(MultipartFile image) {
        try {
            return image.getBytes();
        } catch (IOException e) {
            throw new MemberException(MemberErrorCode.INVALID_PROFILE_IMAGE, e);
        }
    }

    private void validateWebpSignature(byte[] content) {
        if (!hasWebpSignature(content)) {
            throw new MemberException(MemberErrorCode.INVALID_PROFILE_IMAGE);
        }
    }

    private MediaType parseContentType(String contentType) {
        try {
            return contentType == null ? null : MediaType.parseMediaType(contentType);
        } catch (InvalidMediaTypeException e) {
            return null;
        }
    }

    private boolean hasWebpSignature(byte[] content) {
        if (content.length < 12) {
            return false;
        }
        boolean hasRiffHeader = content[0] == 'R' && content[1] == 'I' && content[2] == 'F' && content[3] == 'F';
        boolean hasWebpMarker = content[8] == 'W' && content[9] == 'E' && content[10] == 'B' && content[11] == 'P';
        return hasRiffHeader && hasWebpMarker;
    }

    @Override
    @Transactional
    public void follow(Long followerId, Long followingId) {
        if (followerId.equals(followingId)) {
            throw new MemberException(MemberErrorCode.CANNOT_FOLLOW_SELF);
        }

        Member follower = memberRepository.findByIdAndDeletedAtIsNull(followerId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));
        Member following = memberRepository.findByIdAndDeletedAtIsNull(followingId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));

        MemberFollowId id = new MemberFollowId(followerId, followingId);
        if (memberFollowRepository.existsById(id)) {
            throw new MemberException(MemberErrorCode.ALREADY_FOLLOWING);
        }

        try {
            memberFollowRepository.saveAndFlush(MemberFollow.create(follower, following));
        } catch (DataIntegrityViolationException e) {
            // 동시에 같은 대상을 팔로우하는 경우 사전 체크를 통과했더라도
            // 복합 PK(pk_member_follow)에서 최종적으로 걸러진다.
            throw new MemberException(MemberErrorCode.ALREADY_FOLLOWING, e);
        }

        eventPublisher.publishEvent(new MemberFollowedEvent(followerId, followingId));
    }

    @Override
    @Transactional
    public void unfollow(Long followerId, Long followingId) {
        if (followerId.equals(followingId)) {
            throw new MemberException(MemberErrorCode.CANNOT_UNFOLLOW_SELF);
        }

        long deletedCount = memberFollowRepository.deleteByIdFollowerIdAndIdFollowingId(followerId, followingId);
        if (deletedCount == 0) {
            throw new MemberException(MemberErrorCode.NOT_FOLLOWING);
        }
    }

    @Override
    @Transactional
    public void increaseReportCount(Long memberId) {
        memberRepository.increaseReportCount(memberId);
    }

    @Override
    @Transactional
    public void decreaseReportCount(Long memberId) {
        memberRepository.decreaseReportCount(memberId);
    }

    @Override
    @Transactional
    public void withdraw(Long memberId, String reauthCode) {
        Member member = memberQueryService.getActiveMember(memberId);
        String oldProfileImageKey = member.getProfileImageObjectKey();

        member.withdrawVoluntarily();
        memberFollowRepository.deleteByIdFollowerId(memberId);
        memberFollowRepository.deleteByIdFollowingId(memberId);

        try {
            memberRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException e) {
            // 마스킹 닉네임("플리맵사용자{id}")이 다른 활성 회원이 실제로 사용 중인 닉네임과
            // 우연히 겹치는 경우 DB의 대소문자 무시 유니크 인덱스(uk_member_nickname_ci)에서 걸러진다.
            throw new MemberException(MemberErrorCode.NICKNAME_DUPLICATE, e);
        }

        // DB 검증(닉네임 충돌)을 통과한 뒤 같은 트랜잭션 안에서 소셜 연결을 끊는다. 애플/구글을 끊지 못하면
        // 재로그인 요청 예외로 탈퇴 전체가 롤백되고, revoke 이후 닉네임 충돌로 롤백되는 경우는 생기지 않는다.
        socialAccountCommandService.unlink(memberId, reauthCode);
        socialAccountCommandService.deleteByMemberId(memberId);

        // 탈퇴 트랜잭션 커밋 전에 스토리지 객체를 지우면, 커밋 실패(롤백) 시 DB는 여전히
        // oldProfileImageKey를 가리키는데 실제 객체는 이미 삭제된 상태가 된다. 실제 삭제는
        // MemberEventListener가 이 트랜잭션이 커밋된 뒤에만 수행한다.
        eventPublisher.publishEvent(new MemberWithdrawnEvent(memberId, oldProfileImageKey));
    }

    @Override
    @Transactional
    public boolean applySanction(Long memberId, SuspensionPeriod period, ReportCategory reasonCategory, String reasonDetail) {
        // PESSIMISTIC_WRITE로 조회해 동시에 들어온 벌점 부여 요청이 서로의 증가분을
        // 덮어쓰지 않도록 직렬화한다(같은 회원에 대한 두 번째 요청은 첫 번째가 커밋될 때까지 대기).
        Member member = memberRepository.findByIdAndDeletedAtIsNullForUpdate(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));
        String oldProfileImageKey = member.getProfileImageObjectKey();

        member.applySanction(period, reasonCategory, reasonDetail);

        if (member.getStatus() != MemberStatus.WITHDRAWN) {
            memberRepository.save(member);
            return false;
        }

        memberFollowRepository.deleteByIdFollowerId(memberId);
        memberFollowRepository.deleteByIdFollowingId(memberId);
        // 재가입 차단용 SocialAccount는 남기되 provider 연결은 끊는다. 실패해도 자동 탈퇴는 진행한다.
        socialAccountCommandService.unlinkQuietly(memberId);

        try {
            memberRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException e) {
            // 마스킹 닉네임("플리맵사용자{id}")이 다른 활성 회원이 실제로 사용 중인 닉네임과
            // 우연히 겹치는 경우 DB의 대소문자 무시 유니크 인덱스(uk_member_nickname_ci)에서 걸러진다.
            throw new MemberException(MemberErrorCode.NICKNAME_DUPLICATE, e);
        }

        // 탈퇴 트랜잭션 커밋 전에 스토리지 객체를 지우면, 커밋 실패(롤백) 시 DB는 여전히
        // oldProfileImageKey를 가리키는데 실제 객체는 이미 삭제된 상태가 된다. 실제 삭제는
        // MemberEventListener가 이 트랜잭션이 커밋된 뒤에만 수행한다.
        eventPublisher.publishEvent(new MemberWithdrawnEvent(memberId, oldProfileImageKey));
        return true;
    }

    @Override
    @Transactional
    public void liftSuspension(Long memberId) {
        Member member = memberRepository.findByIdAndDeletedAtIsNull(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));
        member.liftSuspension();
        memberRepository.save(member);
    }

    @Override
    @Transactional
    public void replacePenalizedNickname(Long memberId, String newNickname) {
        Member member = memberRepository.findByIdAndDeletedAtIsNull(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));

        try {
            member.replaceNicknameForPenalty(newNickname);
            memberRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException e) {
            throw new MemberException(MemberErrorCode.NICKNAME_DUPLICATE, e);
        }
    }

    @Override
    @Transactional
    public void resetReportCount(Long memberId) {
        Member member = memberRepository.findByIdAndDeletedAtIsNull(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));
        member.resetReportCount();
        memberRepository.save(member);
    }

    @Override
    @Transactional
    public void regenerateNickname(Long memberId, String newNickname) {
        Member member = memberRepository.findByIdAndDeletedAtIsNull(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));

        try {
            member.regenerateNickname(newNickname);
            memberRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException e) {
            throw new MemberException(MemberErrorCode.NICKNAME_DUPLICATE, e);
        }
    }
}
