package com.example.plimap.domain.auth.service.command.impl;

import com.example.plimap.domain.auth.config.DemoAuthProperties;
import com.example.plimap.domain.auth.entity.AuthMember;
import com.example.plimap.domain.auth.exception.AuthErrorCode;
import com.example.plimap.domain.auth.exception.AuthException;
import com.example.plimap.domain.auth.service.command.DemoAuthCommandService;
import com.example.plimap.domain.member.entity.Member;
import com.example.plimap.domain.member.enums.MemberRole;
import com.example.plimap.domain.member.service.query.MemberQueryService;
import com.example.plimap.global.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DemoAuthCommandServiceImpl implements DemoAuthCommandService {

    private final DemoAuthProperties properties;
    private final MemberQueryService memberQueryService;
    private final JwtUtil jwtUtil;

    @Override
    public String issueAccessToken() {
        if (!properties.enabled() || properties.memberId() == null || properties.memberId() <= 0) {
            throw new AuthException(AuthErrorCode.DEMO_LOGIN_UNAVAILABLE);
        }

        Member member = memberQueryService.getActiveMember(properties.memberId());
        if (member.getRole() != MemberRole.USER) {
            throw new AuthException(AuthErrorCode.DEMO_LOGIN_UNAVAILABLE);
        }

        return jwtUtil.createAccessToken(new AuthMember(member));
    }
}
