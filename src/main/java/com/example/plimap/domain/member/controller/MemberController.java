package com.example.plimap.domain.member.controller;

import com.example.plimap.domain.auth.entity.AuthMember;
import com.example.plimap.domain.member.controller.docs.MemberControllerDocs;
import com.example.plimap.domain.member.converter.MemberConverter;
import com.example.plimap.domain.member.dto.Pagination;
import com.example.plimap.domain.member.dto.request.MemberReqDTO;
import com.example.plimap.domain.member.dto.response.MemberResponse;
import com.example.plimap.domain.member.enums.NicknameCheckFailReason;
import com.example.plimap.domain.member.exception.MemberSuccessCode;
import com.example.plimap.domain.member.service.command.MemberCommandService;
import com.example.plimap.domain.member.service.query.MemberQueryService;
import com.example.plimap.global.apiPayload.ApiResponse;
import com.example.plimap.global.security.SessionInvalidationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberController implements MemberControllerDocs {

    private final MemberQueryService memberQueryService;
    private final MemberCommandService memberCommandService;
    private final SessionInvalidationService sessionInvalidationService;

    @Override
    @GetMapping("/nickname/check")
    public ApiResponse<MemberResponse.NicknameCheck> checkNickname(@RequestParam String nickname) {
        NicknameCheckFailReason reason = memberQueryService.checkNicknameFailReason(nickname);
        return ApiResponse.success(MemberSuccessCode.NICKNAME_CHECKED, MemberConverter.toNicknameCheck(nickname, reason));
    }

    @Override
    @GetMapping("/me")
    public ApiResponse<MemberResponse.MyProfile> getMyProfile(@AuthenticationPrincipal AuthMember authMember) {
        MemberResponse.MyProfile profile = memberQueryService.getMyProfile(authMember.getMember().getId());
        return ApiResponse.success(MemberSuccessCode.MY_PROFILE_FETCHED, profile);
    }

    @Override
    @GetMapping("/{memberId}")
    public ApiResponse<MemberResponse.OtherProfile> getOtherProfile(
            @AuthenticationPrincipal AuthMember authMember,
            @PathVariable Long memberId
    ) {
        MemberResponse.OtherProfile profile = memberQueryService.getOtherProfile(authMember.getMember().getId(), memberId);
        return ApiResponse.success(MemberSuccessCode.OTHER_PROFILE_FETCHED, profile);
    }

    @Override
    @PatchMapping("/me")
    public ApiResponse<MemberResponse.Profile> updateProfile(
            @AuthenticationPrincipal AuthMember authMember,
            @Valid @RequestBody MemberReqDTO.UpdateProfile request
    ) {
        MemberResponse.Profile profile = memberCommandService.updateProfile(authMember.getMember().getId(), request);
        return ApiResponse.success(MemberSuccessCode.PROFILE_UPDATED, profile);
    }

    @Override
    @PostMapping(value = "/me/profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<MemberResponse.ProfileImage> uploadProfileImage(
            @AuthenticationPrincipal AuthMember authMember,
            @RequestPart("image") MultipartFile image
    ) {
        MemberResponse.ProfileImage result =
                memberCommandService.uploadProfileImage(authMember.getMember().getId(), image);
        return ApiResponse.success(MemberSuccessCode.PROFILE_IMAGE_UPLOADED, result);
    }

    @Override
    @DeleteMapping("/me/profile-image")
    public ApiResponse<Void> removeProfileImage(@AuthenticationPrincipal AuthMember authMember) {
        memberCommandService.removeProfileImage(authMember.getMember().getId());
        return ApiResponse.success(MemberSuccessCode.PROFILE_IMAGE_REMOVED, null);
    }

    @Override
    @PostMapping("/{memberId}/follow")
    public ApiResponse<Void> follow(
            @AuthenticationPrincipal AuthMember authMember,
            @PathVariable Long memberId
    ) {
        memberCommandService.follow(authMember.getMember().getId(), memberId);
        return ApiResponse.success(MemberSuccessCode.FOLLOWED, null);
    }

    @Override
    @DeleteMapping("/{memberId}/follow")
    public ApiResponse<Void> unfollow(
            @AuthenticationPrincipal AuthMember authMember,
            @PathVariable Long memberId
    ) {
        memberCommandService.unfollow(authMember.getMember().getId(), memberId);
        return ApiResponse.success(MemberSuccessCode.UNFOLLOWED, null);
    }

    @Override
    @GetMapping("/{memberId}/followers")
    public ApiResponse<Pagination<MemberResponse.FollowerItem>> getFollowers(
            @AuthenticationPrincipal AuthMember authMember,
            @PathVariable Long memberId,
            @RequestParam(required = false, defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String cursor
    ) {
        Pagination<MemberResponse.FollowerItem> response =
                memberQueryService.findFollowers(authMember.getMember().getId(), memberId, cursor, pageSize);
        return ApiResponse.success(MemberSuccessCode.FOLLOWERS_FETCHED, response);
    }

    @Override
    @GetMapping("/{memberId}/following")
    public ApiResponse<Pagination<MemberResponse.FollowingItem>> getFollowing(
            @AuthenticationPrincipal AuthMember authMember,
            @PathVariable Long memberId,
            @RequestParam(required = false, defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String cursor
    ) {
        Pagination<MemberResponse.FollowingItem> response =
                memberQueryService.findFollowing(authMember.getMember().getId(), memberId, cursor, pageSize);
        return ApiResponse.success(MemberSuccessCode.FOLLOWING_FETCHED, response);
    }

    @Override
    @GetMapping("/search")
    public ApiResponse<Pagination<MemberResponse.SearchItem>> searchMembers(
            @AuthenticationPrincipal AuthMember authMember,
            @RequestParam(required = false, defaultValue = "") String keyword,
            @RequestParam(required = false, defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String cursor
    ) {
        Pagination<MemberResponse.SearchItem> response =
                memberQueryService.searchActiveMembers(authMember.getMember().getId(), keyword, cursor, pageSize);
        return ApiResponse.success(MemberSuccessCode.MEMBERS_SEARCHED, response);
    }

    @Override
    @DeleteMapping("/me")
    public ApiResponse<Void> withdraw(
            @AuthenticationPrincipal AuthMember authMember,
            @RequestBody(required = false) MemberReqDTO.Withdraw withdrawRequest,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String reauthCode = withdrawRequest == null ? null : withdrawRequest.authorizationCode();
        memberCommandService.withdraw(authMember.getMember().getId(), reauthCode);

        sessionInvalidationService.invalidate(request, response);

        return ApiResponse.success(MemberSuccessCode.WITHDRAWN, null);
    }
}
