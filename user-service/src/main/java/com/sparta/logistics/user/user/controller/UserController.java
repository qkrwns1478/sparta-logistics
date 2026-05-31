package com.sparta.logistics.user.user.controller;

import com.sparta.logistics.common.domain.Role;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.response.ApiResponse;
import com.sparta.logistics.user.user.service.UserService;
import com.sparta.logistics.user.user.enums.UserStatus;
import com.sparta.logistics.user.exception.UserErrorCode;
import com.sparta.logistics.user.user.dto.request.UpdateRequest;
import com.sparta.logistics.user.user.dto.response.DeleteResponse;
import com.sparta.logistics.user.user.dto.response.GetResponse;
import com.sparta.logistics.user.user.dto.response.UpdateResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static com.sparta.logistics.common.domain.Role.MASTER;

import com.sparta.logistics.user.validator.HubCompanyValidator;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserService userService;
    private final HubCompanyValidator hubCompanyValidator;
    private static final List<Integer> ALLOWED_PAGE_SIZES = List.of(10, 30, 50);

    // 사용자 존재 여부 확인 (내부 서비스용)
    @GetMapping("/{userId}/exists")
    public ResponseEntity<Void> checkUserExists(@PathVariable UUID userId) {
        userService.checkUserExists(userId);
        return ResponseEntity.ok().build();
    }

    // 전체 정보 조회 (MASTER)
    @GetMapping
    public ResponseEntity<ApiResponse<Page<GetResponse>>> getUsers(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) UserStatus status,
            @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestHeader("X-User-Role") Role requestRole
    ) {
        if (!ALLOWED_PAGE_SIZES.contains(pageable.getPageSize())) {
            throw new BusinessException(UserErrorCode.INVALID_PAGE_SIZE);
        }
        if(MASTER.equals(requestRole)){
            Page<GetResponse> response = userService.getUsers(username,name,role,status, pageable);
            return ResponseEntity.ok(ApiResponse.ok("요청이 성공적으로 처리되었습니다.",response));
        }
        throw new BusinessException(UserErrorCode.ACCESS_DENIED);
    }

    // 사용자 단건 조회 (MASTER, 본인)
    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<GetResponse>>getUser(@PathVariable UUID userId,
                                                           @RequestHeader("X-User-Id") UUID id,
                                                           @RequestHeader("X-User-Role") Role role){
        if (MASTER.equals(role) || id.equals(userId)){
            GetResponse response = userService.getUser(userId);
            return ResponseEntity.ok(ApiResponse.ok(response));
        }
        throw new BusinessException(UserErrorCode.ACCESS_DENIED);
    }

    // 사용자 수정 (MASTER)
    @PutMapping("/{userId}")
    public ResponseEntity<ApiResponse<UpdateResponse>> updateUser(@PathVariable("userId") UUID userId,
                                                                  @RequestHeader("X-User-Role") Role role,
                                                                  @Valid @RequestBody UpdateRequest request
    ){
        if(MASTER.equals(role)){
            hubCompanyValidator.validate(request.hubId(), request.companyId());
            UpdateResponse response = userService.updateUser(userId, request);
            return ResponseEntity.ok(ApiResponse.ok("사용자 정보가 수정되었습니다.", response));
        }
        throw new BusinessException(UserErrorCode.ACCESS_DENIED);
    }

    // 사용자 삭제 (MASTER)
    @DeleteMapping("/{userId}")
    public ResponseEntity<ApiResponse<DeleteResponse>> deleteUser(@PathVariable("userId") UUID userId,
                                                                  @RequestHeader("X-User-Role") Role role,
                                                                  @RequestHeader("X-User-Id") UUID requesterId
    ){
        if(MASTER.equals(role)){
            DeleteResponse response = userService.deleteUser(userId, requesterId);
            return ResponseEntity.ok(ApiResponse.ok("사용자가 삭제되었습니다.", response));
        }
        throw new BusinessException(UserErrorCode.ACCESS_DENIED);
    }
}

