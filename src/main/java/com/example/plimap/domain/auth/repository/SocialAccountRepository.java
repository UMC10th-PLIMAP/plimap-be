package com.example.plimap.domain.auth.repository;

import com.example.plimap.domain.auth.entity.SocialAccount;
import com.example.plimap.domain.auth.enums.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {

    Optional<SocialAccount> findByProviderAndProviderSubject(AuthProvider provider, String providerSubject);

    // 회원은 가입한 provider 계정 하나만 가진다(CustomOAuthService가 가입 시에만 SocialAccount를 만든다).
    Optional<SocialAccount> findFirstByMember_Id(Long memberId);

    List<SocialAccount> findByMember_IdInOrderByCreatedAtAsc(List<Long> memberIds);

    @Modifying
    @Query("DELETE FROM SocialAccount sa WHERE sa.member.id = :memberId")
    long deleteByMemberId(@Param("memberId") Long memberId);
}
