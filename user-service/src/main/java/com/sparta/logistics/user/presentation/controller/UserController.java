package com.sparta.logistics.user.presentation.controller;

import com.sparta.logistics.common.response.ApiResponse;
import com.sparta.logistics.user.application.dto.response.GetResult;
import com.sparta.logistics.user.application.dto.response.UserResult;
import com.sparta.logistics.user.application.service.UserService;
import com.sparta.logistics.user.presentation.dto.response.GetResponse;
import com.sparta.logistics.user.presentation.dto.response.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {

//    private final UserService userService;
//
//    @GetMapping// 전체 정보 조회
//    public ResponseEntity<ApiResponse<Page<GetResponse>>> getUser(
//            @PageableDefault(
//                    page = 0,
//                    size = 10,
//                    sort ="createdAt",
//                    direction = Sort.Direction.DESC) Pageable pageable ) {
//
//        Page<GetResult> getResult = userService.getUser(pageable);
//
//        return null;
//    }




}
