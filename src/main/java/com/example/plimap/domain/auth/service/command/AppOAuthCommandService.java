package com.example.plimap.domain.auth.service.command;

import com.example.plimap.domain.auth.dto.request.AuthReqDTO;
import com.example.plimap.domain.auth.dto.response.AuthResponse;

public interface AppOAuthCommandService {

    AuthResponse.AppLogin login(AuthReqDTO.AppLogin request);

    AuthResponse.AppTokenReissue reissue(AuthReqDTO.AppReissue request);

    AuthResponse.AppLoginNonce issueNonce();
}
