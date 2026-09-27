package com.example.plimap.domain.auth.service.command;

import com.example.plimap.domain.auth.dto.request.AuthReqDTO;
import com.example.plimap.domain.auth.dto.response.AuthResponse;

public interface AppOAuthCommandService {

    AuthResponse.AppLogin login(AuthReqDTO.AppLogin request);
}
