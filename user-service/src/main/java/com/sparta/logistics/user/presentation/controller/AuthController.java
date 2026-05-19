package com.sparta.logistics.user.presentation.controller;


import com.sparta.logistics.common.response.ApiResponse;
import com.sparta.logistics.user.application.dto.request.LoginCommand;
import com.sparta.logistics.user.application.dto.response.TokenDto;
import com.sparta.logistics.user.application.dto.response.UserResult;
import com.sparta.logistics.user.presentation.dto.request.LoginRequest;
import com.sparta.logistics.user.presentation.dto.request.SignupRequest;
import com.sparta.logistics.user.presentation.dto.response.UserResponse;
import com.sparta.logistics.user.application.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    // 회원가입
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<UserResponse>> signUp(@Valid @RequestBody SignupRequest request) { // User 객체는 프로젝트 내 정의된 위치 확인 필요

        UserResult userResult = authService.signUp(request.toCommand());

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(UserResponse.from(userResult)));
    }

    // 로그인
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<UserResponse>> login(@Valid @RequestBody LoginRequest request){

        TokenDto token = authService.login(request.toCommand());

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token.accessToken());
        headers.set("X-Refresh-Token", token.refreshToken());


        return ResponseEntity.ok()
                .headers(headers)
                .body(ApiResponse.ok(UserResponse.from(token.userResult())));
    }


    // 토큰 갱신
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<UserResponse>> refreshToken(
            @RequestHeader("X-Refresh-Token") String refreshToken){

        TokenDto token = authService.refresh(refreshToken);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token.accessToken());
        headers.set("X-Refresh-Token", token.refreshToken());

        return ResponseEntity.ok()
                .headers(headers)
                .body(ApiResponse.ok(UserResponse.from(token.userResult())));
    }

    // 가입 승인 (MASTER, HUB_MANAGER)
    @PostMapping("/signup/{userId}/approve")
    public ResponseEntity<Void> approveUser(@PathVariable("userId") UUID userId){
        authService.appoveUser(userId);
        return ResponseEntity.noContent().build();
    }

    // 가입 거절 (MASTER, HUB_MANAGER)
    @PatchMapping("/{id}/reject")
    public ResponseEntity<Void> rejectUser(@PathVariable UUID id) {
        authService.rejectUser(id);
        return ResponseEntity.noContent().build();
    }




}
