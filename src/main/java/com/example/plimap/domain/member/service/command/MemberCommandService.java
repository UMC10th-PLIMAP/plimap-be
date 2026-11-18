package com.example.plimap.domain.member.service.command;

import com.example.plimap.domain.member.dto.request.MemberReqDTO;
import com.example.plimap.domain.member.dto.response.MemberResponse;
import com.example.plimap.domain.member.entity.Member;
import com.example.plimap.domain.member.enums.SuspensionPeriod;
import com.example.plimap.domain.report.enums.ReportCategory;
import org.springframework.web.multipart.MultipartFile;

public interface MemberCommandService {

    Member completeOnboarding(Long memberId, MemberReqDTO.Onboarding request);

    MemberResponse.Profile updateProfile(Long memberId, MemberReqDTO.UpdateProfile request);

    MemberResponse.ProfileImage uploadProfileImage(Long memberId, MultipartFile image);

    void removeProfileImage(Long memberId);

    void follow(Long followerId, Long followingId);

    void unfollow(Long followerId, Long followingId);

    void increaseReportCount(Long memberId);

    void decreaseReportCount(Long memberId);

    void withdraw(Long memberId, String reauthCode);

    boolean applySanction(Long memberId, SuspensionPeriod period, ReportCategory reasonCategory, String reasonDetail);

    void liftSuspension(Long memberId);

    void replacePenalizedNickname(Long memberId, String newNickname);

    void resetReportCount(Long memberId);

    void regenerateNickname(Long memberId, String newNickname);
}
