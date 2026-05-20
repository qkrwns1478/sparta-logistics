package com.sparta.logistics.user.presentation.controller;

import com.sparta.logistics.common.response.ApiResponse;
import com.sparta.logistics.user.application.dto.response.UserResult;
import com.sparta.logistics.user.application.service.UserService;
import com.sparta.logistics.user.presentation.dto.response.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<ApiResponse<UserResponse>> getUser() {

        UserResult userResult = userService.getUser();



    }
}
