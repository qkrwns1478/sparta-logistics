package com.sparta.logistics.user.presentation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.logistics.common.domain.Role;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.GlobalExceptionHandler;
import com.sparta.logistics.user.user.service.UserService;
import com.sparta.logistics.user.user.service.validator.HubCompanyValidator;
import com.sparta.logistics.user.user.enums.UserStatus;
import com.sparta.logistics.user.exception.UserErrorCode;
import com.sparta.logistics.user.user.dto.response.DeleteResponse;
import com.sparta.logistics.user.user.dto.response.GetResponse;
import com.sparta.logistics.user.user.dto.response.UpdateResponse;
import com.sparta.logistics.user.user.controller.UserController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserController 단위 테스트")
class UserControllerTest {

    @InjectMocks
    private UserController userController;

    @Mock
    private UserService userService;

    @Mock
    private HubCompanyValidator hubCompanyValidator;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private UUID userId;
    private GetResponse mockGetResponse;
    private UpdateResponse mockUpdateResponse;
    private DeleteResponse mockDeleteResponse;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(userController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();

        objectMapper = new ObjectMapper();
        userId = UUID.randomUUID();

        mockGetResponse = GetResponse.builder()
                .userId(userId)
                .username("testuser")
                .name("홍길동")
                .email("test@example.com")
                .slackId("U123456")
                .role(Role.MASTER)
                .status(UserStatus.APPROVED)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        mockUpdateResponse = UpdateResponse.builder()
                .userId(userId)
                .username("testuser")
                .name("수정된이름")
                .email("updated@example.com")
                .slackId("U999")
                .role(Role.MASTER)
                .status(UserStatus.APPROVED)
                .updatedAt(LocalDateTime.now())
                .build();

        mockDeleteResponse = DeleteResponse.builder()
                .userId(userId)
                .deletedAt(LocalDateTime.now())
                .build();
    }

    // ────────────────────────────────────────────────
    // GET /api/v1/users/{userId}/exists
    // ────────────────────────────────────────────────
    @Nested
    @DisplayName("GET /{userId}/exists - 유저 존재 여부 확인 (내부 서비스용)")
    class CheckUserExists {

        @Test
        @DisplayName("성공 - 유저 존재 시 200 OK")
        void success() throws Exception {
            willDoNothing().given(userService).checkUserExists(userId);

            mockMvc.perform(get("/api/v1/users/{userId}/exists", userId))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("실패 - 유저 미존재 시 404 Not Found")
        void notFound() throws Exception {
            willThrow(new BusinessException(UserErrorCode.USER_NOT_FOUND))
                    .given(userService).checkUserExists(userId);

            mockMvc.perform(get("/api/v1/users/{userId}/exists", userId))
                    .andExpect(status().isNotFound());
        }
    }

    // ────────────────────────────────────────────────
    // GET /api/v1/users
    // ────────────────────────────────────────────────
    @Nested
    @DisplayName("GET / - 전체 유저 조회")
    class GetUsers {

        @Test
        @DisplayName("성공 - MASTER 역할로 조회 시 200 OK")
        void successByMaster() throws Exception {
            Page<GetResponse> page = new PageImpl<>(List.of(mockGetResponse), PageRequest.of(0, 10), 1);
            given(userService.getUsers(any(), any(), any(), any(), any())).willReturn(page);

            mockMvc.perform(get("/api/v1/users")
                            .header("X-User-Role", "MASTER")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].username").value("testuser"));
        }

        @Test
        @DisplayName("실패 - MASTER가 아닌 역할로 조회 시 403 Forbidden")
        void forbiddenForNonMaster() throws Exception {
            mockMvc.perform(get("/api/v1/users")
                            .header("X-User-Role", "HUB_MANAGER")
                            .param("size", "10"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("실패 - 허용되지 않은 페이지 크기(size=20) 시 400 Bad Request")
        void invalidPageSize() throws Exception {
            mockMvc.perform(get("/api/v1/users")
                            .header("X-User-Role", "MASTER")
                            .param("size", "20"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("성공 - 조회 조건(username, role) 필터 적용")
        void withFilters() throws Exception {
            Page<GetResponse> page = new PageImpl<>(List.of(mockGetResponse), PageRequest.of(0, 10), 1);
            given(userService.getUsers(any(), any(), any(), any(), any())).willReturn(page);

            mockMvc.perform(get("/api/v1/users")
                            .header("X-User-Role", "MASTER")
                            .param("username", "testuser")
                            .param("role", "MASTER")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalElements").value(1));
        }
    }

    // ────────────────────────────────────────────────
    // GET /api/v1/users/{userId}
    // ────────────────────────────────────────────────
    @Nested
    @DisplayName("GET /{userId} - 유저 단건 조회")
    class GetUser {

        @Test
        @DisplayName("성공 - MASTER 역할로 타인 조회")
        void successByMaster() throws Exception {
            UUID requesterId = UUID.randomUUID();
            given(userService.getUser(userId)).willReturn(mockGetResponse);

            mockMvc.perform(get("/api/v1/users/{userId}", userId)
                            .header("X-User-Role", "MASTER")
                            .header("X-User-Id", requesterId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.username").value("testuser"));
        }

        @Test
        @DisplayName("성공 - 본인 조회")
        void successBySelf() throws Exception {
            given(userService.getUser(userId)).willReturn(mockGetResponse);

            mockMvc.perform(get("/api/v1/users/{userId}", userId)
                            .header("X-User-Role", "HUB_MANAGER")
                            .header("X-User-Id", userId.toString()))  // X-User-Id == userId
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("실패 - 타인이 HUB_MANAGER 역할로 조회 시 403 Forbidden")
        void forbiddenForOther() throws Exception {
            UUID otherId = UUID.randomUUID();

            mockMvc.perform(get("/api/v1/users/{userId}", userId)
                            .header("X-User-Role", "HUB_MANAGER")
                            .header("X-User-Id", otherId.toString()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("실패 - 유저 미존재 시 404 Not Found")
        void notFound() throws Exception {
            given(userService.getUser(userId))
                    .willThrow(new BusinessException(UserErrorCode.USER_NOT_FOUND));

            mockMvc.perform(get("/api/v1/users/{userId}", userId)
                            .header("X-User-Role", "MASTER")
                            .header("X-User-Id", UUID.randomUUID().toString()))
                    .andExpect(status().isNotFound());
        }
    }

    // ────────────────────────────────────────────────
    // PUT /api/v1/users/{userId}
    // ────────────────────────────────────────────────
    @Nested
    @DisplayName("PUT /{userId} - 유저 정보 수정")
    class UpdateUser {

        private final String VALID_BODY = """
                {
                  "name": "수정된이름",
                  "email": "updated@example.com",
                  "slackId": "U999",
                  "role": "MASTER"
                }
                """;

        @Test
        @DisplayName("성공 - MASTER 역할로 수정")
        void successByMaster() throws Exception {
            willDoNothing().given(hubCompanyValidator).validate(any(), any());
            given(userService.updateUser(any(), any())).willReturn(mockUpdateResponse);

            mockMvc.perform(put("/api/v1/users/{userId}", userId)
                            .header("X-User-Role", "MASTER")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.name").value("수정된이름"));
        }

        @Test
        @DisplayName("실패 - MASTER가 아닌 역할로 수정 시 403 Forbidden")
        void forbiddenForNonMaster() throws Exception {
            mockMvc.perform(put("/api/v1/users/{userId}", userId)
                            .header("X-User-Role", "HUB_MANAGER")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("실패 - name 공백 시 400 Bad Request")
        void blankName() throws Exception {
            String body = """
                    {
                      "name": "",
                      "slackId": "U999",
                      "role": "MASTER"
                    }
                    """;

            mockMvc.perform(put("/api/v1/users/{userId}", userId)
                            .header("X-User-Role", "MASTER")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("실패 - 중복 이메일 수정 시 409 Conflict")
        void duplicateEmail() throws Exception {
            willDoNothing().given(hubCompanyValidator).validate(any(), any());
            given(userService.updateUser(any(), any()))
                    .willThrow(new BusinessException(UserErrorCode.EMAIL_ALREADY_EXISTS));

            mockMvc.perform(put("/api/v1/users/{userId}", userId)
                            .header("X-User-Role", "MASTER")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isConflict());
        }
    }

    // ────────────────────────────────────────────────
    // DELETE /api/v1/users/{userId}
    // ────────────────────────────────────────────────
    @Nested
    @DisplayName("DELETE /{userId} - 유저 삭제")
    class DeleteUser {

        @Test
        @DisplayName("성공 - MASTER 역할로 삭제")
        void successByMaster() throws Exception {
            UUID requesterId = UUID.randomUUID();
            given(userService.deleteUser(userId, requesterId)).willReturn(mockDeleteResponse);

            mockMvc.perform(delete("/api/v1/users/{userId}", userId)
                            .header("X-User-Role", "MASTER")
                            .header("X-User-Id", requesterId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.userId").value(userId.toString()));
        }

        @Test
        @DisplayName("실패 - MASTER가 아닌 역할로 삭제 시 403 Forbidden")
        void forbiddenForNonMaster() throws Exception {
            mockMvc.perform(delete("/api/v1/users/{userId}", userId)
                            .header("X-User-Role", "HUB_MANAGER")
                            .header("X-User-Id", UUID.randomUUID().toString()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 유저 삭제 시 404 Not Found")
        void notFound() throws Exception {
            UUID requesterId = UUID.randomUUID();
            given(userService.deleteUser(userId, requesterId))
                    .willThrow(new BusinessException(UserErrorCode.USER_NOT_FOUND));

            mockMvc.perform(delete("/api/v1/users/{userId}", userId)
                            .header("X-User-Role", "MASTER")
                            .header("X-User-Id", requesterId.toString()))
                    .andExpect(status().isNotFound());
        }
    }
}
