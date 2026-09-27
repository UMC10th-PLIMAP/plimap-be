package com.example.plimap.domain.auth.service.command.impl;

import com.example.plimap.domain.auth.dto.GoogleDTO;
import com.example.plimap.domain.auth.dto.KakaoDTO;
import com.example.plimap.domain.auth.dto.OAuthDTO;
import com.example.plimap.domain.auth.entity.OAuthMember;
import com.example.plimap.domain.auth.entity.SocialAccount;
import com.example.plimap.domain.auth.enums.AuthProvider;
import com.example.plimap.domain.auth.exception.SanctionedMemberAuthenticationException;
import com.example.plimap.domain.auth.repository.SocialAccountRepository;
import com.example.plimap.domain.member.AdminEmailPolicy;
import com.example.plimap.domain.member.converter.MemberConverter;
import com.example.plimap.domain.member.entity.Member;
import com.example.plimap.domain.member.enums.MemberRole;
import com.example.plimap.domain.member.enums.MemberStatus;
import com.example.plimap.domain.member.enums.WithdrawalReason;
import com.example.plimap.domain.member.exception.MemberErrorCode;
import com.example.plimap.domain.member.exception.MemberException;
import com.example.plimap.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class CustomOAuthService extends DefaultOAuth2UserService {

    private final MemberRepository memberRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final PlatformTransactionManager transactionManager;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuthUser = super.loadUser(userRequest);

        AuthProvider provider;
        try {
            provider = AuthProvider.valueOf(
                    userRequest.getClientRegistration().getRegistrationId().toUpperCase()
            );
        } catch (IllegalArgumentException e) {
            throw new MemberException(MemberErrorCode.NOT_SUPPORT_SOCIAL_PROVIDER, e);
        }

        OAuthDTO dto = switch (provider) {
            case KAKAO -> {
                if (!(oAuthUser.getAttribute("id") instanceof Number idNumber)) {
                    throw new MemberException(MemberErrorCode.INVALID_SOCIAL_PROFILE);
                }
                long id = idNumber.longValue();
                Map<String, Object> kakaoAccount = oAuthUser.getAttribute("kakao_account");
                if (kakaoAccount == null) {
                    throw new MemberException(MemberErrorCode.INVALID_SOCIAL_PROFILE);
                }
                Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");
                if (!(profile != null && profile.get("nickname") instanceof String nickname)) {
                    throw new MemberException(MemberErrorCode.INVALID_SOCIAL_PROFILE);
                }
                String providerSubject = String.valueOf(id);
                String email = (String) kakaoAccount.get("email");
                yield new KakaoDTO(providerSubject, email, nickname);
            }
            case GOOGLE -> {
                if (!(oAuthUser.getAttribute("sub") instanceof String providerSubject)) {
                    throw new MemberException(MemberErrorCode.INVALID_SOCIAL_PROFILE);
                }
                if (!(oAuthUser.getAttribute("email") instanceof String email)) {
                    throw new MemberException(MemberErrorCode.INVALID_SOCIAL_PROFILE);
                }
                String nickname = oAuthUser.getAttribute("name");
                yield new GoogleDTO(providerSubject, email, nickname);
            }
            default -> throw new MemberException(MemberErrorCode.NOT_SUPPORT_SOCIAL_PROVIDER);
        };

        Member member = resolveMember(provider, dto);

        return new OAuthMember(member, oAuthUser.getAttributes());
    }

    // AppOAuthCommandServiceImpl(앱 로그인)에서도 재사용하므로 public.
    @Transactional
    public Member resolveMember(AuthProvider provider, OAuthDTO dto) {
        Member member = socialAccountRepository
                .findByProviderAndProviderSubject(provider, dto.getProviderSubject())
                .map(SocialAccount::getMember)
                .orElseGet(() -> createMemberWithSocialAccount(provider, dto));
        // 벌점 4점 누적으로 자동 탈퇴된 회원은 SocialAccount가 유지되어 여기서 발견되므로 재가입을 차단한다.
        // 자발적 탈퇴는 SocialAccount를 하드 삭제하므로 이 분기에 도달하지 않고 새 회원으로 처리된다.
        if (member.getWithdrawalReason() == WithdrawalReason.PENALTY) {
            throw new SanctionedMemberAuthenticationException(member);
        }
        // 정지 중인 회원은 로그인 자체를 차단해 리다이렉트로 제재 정보를 바로 전달한다.
        // 정지가 이미 만료된 경우엔 lazy로 해제하고 로그인을 계속 진행한다(MemberStatusInterceptor와 동일한 패턴).
        if (member.getStatus() == MemberStatus.SUSPENDED) {
            if (member.isSuspensionExpired()) {
                member.liftSuspension();
            } else {
                throw new SanctionedMemberAuthenticationException(member);
            }
        }
        // 관리자 이메일 정책이 나중에 추가되거나 가입 이후 반영된 경우를 대비해,
        // 신규 가입 시점뿐 아니라 기존 회원 로그인 시에도 매번 승격 여부를 확인한다.
        if (AdminEmailPolicy.isAdminEmail(dto.getEmail()) && member.getRole() != MemberRole.ADMIN) {
            member.grantAdmin();
        }
        // SocialAccount.member는 LAZY라 기존 회원 로그인 시 프록시 상태로 반환되는데,
        // OAuthSuccessHandler는 세션이 닫힌 뒤(트랜잭션 밖)에 member.isOnboarded()를 읽으므로
        // 세션이 살아있는 지금 초기화해둔다.
        Hibernate.initialize(member);
        return member;
    }

    private Member createMemberWithSocialAccount(AuthProvider provider, OAuthDTO dto) {
        TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        try {
            return requiresNew.execute(status -> {
                Member newMember = MemberConverter.toMember(dto);
                memberRepository.save(newMember);
                SocialAccount socialAccount = SocialAccount.create(
                        newMember, dto.getProvider(), dto.getProviderSubject(), dto.getEmail());
                socialAccountRepository.save(socialAccount);
                return newMember;
            });
        } catch (DataIntegrityViolationException e) {
            // 동시에 처음 로그인하는 경우 UNIQUE 제약(provider, provider_subject)에 걸릴 수 있으므로,
            // 먼저 커밋된 계정을 다시 조회해 그 회원으로 로그인 처리한다.
            return socialAccountRepository.findByProviderAndProviderSubject(provider, dto.getProviderSubject())
                    .map(SocialAccount::getMember)
                    .orElseThrow(() -> e);
        }
    }
}
