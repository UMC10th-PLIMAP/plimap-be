package com.example.plimap.domain.auth.entity;

import com.example.plimap.domain.auth.enums.AuthProvider;
import com.example.plimap.domain.member.entity.Member;
import com.example.plimap.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "social_account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SocialAccount extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    private AuthProvider provider;

    @Column(name = "provider_subject", nullable = false, length = 255)
    private String providerSubject;

    @Column(name = "email", length = 320)
    private String email;

    // 탈퇴 시 애플/구글 연결 끊기(revoke)에 쓰는 provider refresh token. SocialTokenCipher로 암호화된 값이다.
    @Column(name = "encrypted_refresh_token")
    private String encryptedRefreshToken;

    @Builder
    private SocialAccount(Member member, AuthProvider provider, String providerSubject, String email) {
        this.member = member;
        this.provider = provider;
        this.providerSubject = providerSubject;
        this.email = email;
    }

    public static SocialAccount create(Member member, AuthProvider provider, String providerSubject, String email) {
        return SocialAccount.builder()
                .member(member)
                .provider(provider)
                .providerSubject(providerSubject)
                .email(email)
                .build();
    }

    public void updateEncryptedRefreshToken(String encryptedRefreshToken) {
        this.encryptedRefreshToken = encryptedRefreshToken;
    }
}
