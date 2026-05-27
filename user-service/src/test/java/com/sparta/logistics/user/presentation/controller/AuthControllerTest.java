package com.sparta.logistics.user.presentation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.logistics.common.domain.Role;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.exception.GlobalExceptionHandler;
import com.sparta.logistics.user.application.result.Token;
import com.sparta.logistics.user.application.result.UserResult;
import com.sparta.logistics.user.application.service.AuthService;
import com.sparta.logistics.user.application.validator.HubCompanyValidator;
import com.sparta.logistics.user.domain.model.entity.UserEntity;
import com.sparta.logistics.user.domain.model.enums.UserStatus;
import com.sparta.logistics.user.exception.UserErrorCode;
import com.sparta.logistics.user.presentation.dto.response.ApproveResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController 단위 테스트")
class AuthControllerTest {

    @InjectMocks
    private AuthController authController;

    @Mock
    private AuthService authService;

    @Mock
    private HubCompanyValidator hubCompanyValidator;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private UserResult mockUserResult;
    private Token mockToken;
    private UUID userId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        objectMapper = new ObjectMapper();

        userId = UUID.randomUUID();

        mockUserResult = UserResult.builder()
                .userId(userId)
                .username("testuser")
                .name("홍길동")
                .role(Role.MASTER)
                .status(UserStatus.APPROVED)
                .createdAt(LocalDateTime.now())
                .build();

        mockToken = new Token(mockUserResult, "mock-access-token", "mock-refresh-token");
    }

    // ────────────────────────────────────────────────
    // POST /api/v1/auth/signup
    // ────────────────────────────────────────────────
    @Nested
    @DisplayName("POST /signup - 회원가입")
    class SignUp {

        private final String VALID_BODY = """
                {
                  "username": "testuser",
                  "password": "Test1234!",
                  "name": "홍길동",
                  "email": "test@example.com",
                  "slackId": "U123456",
                  "role": "MASTER"
                }
                """;

        @Test
        @DisplayName("성공 - 201 Created 반환")
        void success() throws Exception {
            willDoNothing().given(hubCompanyValidator).validate(any(), any());
            given(authService.signUp(any())).willReturn(mockUserResult);

            mockMvc.perform(post("/api/v1/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.username").value("testuser"))
                    .andExpect(jsonPath("$.data.role").value("MASTER"));
        }

        @Test
        @DisplayName("실패 - username 공백 시 400 Bad Request")
        void blankUsername() throws Exception {
            String body = """
                    {
                      "username": "",
                      "password": "Test1234!",
                      "name": "홍길동",
                      "slackId": "U123456",
                      "role": "MASTER"
                    }
                    """;

            mockMvc.perform(post("/api/v1/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("실패 - password 형식 불일치 시 400 Bad Request")
        void invalidPassword() throws Exception {
            String body = """
                    {
                      "username": "testuser",
                      "password": "simple",
                      "name": "홍길동",
                      "slackId": "U123456",
                      "role": "MASTER"
                    }
                    """;

            mockMvc.perform(post("/api/v1/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("실패 - role 누락 시 400 Bad Request")
        void missingRole() throws Exception {
            String body = """
                    {
                      "username": "testuser",
                      "password": "Test1234!",
                      "name": "홍길동",
                      "slackId": "U123456"
                    }
                    """;

            mockMvc.perform(post("/api/v1/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("실패 - 중복 username 시 409 Conflict")
        void duplicateUsername() throws Exception {
            willDoNothing().given(hubCompanyValidator).validate(any(), any());
            given(authService.signUp(any()))
                    .willThrow(new BusinessException(UserErrorCode.USER_ALREADY_EXISTS));

            mockMvc.perform(post("/api/v1/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isConflict());
        }
    }

    // ────────────────────────────────────────────────
    // POST /api/v1/auth/login
    // ────────────────────────────────────────────────
    @Nested
    @DisplayName("POST /login - 로그인")
    class Login {

        private final String VALID_BODY = """
                {
                  "username": "testuser",
                  "password": "Test1234!"
                }
                """;

        @Test
        @DisplayName("성공 - 200 OK, 응답 헤더에 토큰 포함")
        void success() throws Exception {
            given(authService.login(any())).willReturn(mockToken);

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Authorization", "Bearer mock-access-token"))
                    .andExpect(header().string("X-Refresh-Token", "mock-refresh-token"))
                    .andExpect(jsonPath("$.data.username").value("testuser"));
        }

        @Test
        @DisplayName("실패 - 비밀번호 불일치 시 401 Unauthorized")
        void wrongPassword() throws Exception {
            given(authService.login(any()))
                    .willThrow(new BusinessException(UserErrorCode.PASSWORD_NOT_MATCH));

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("실패 - 미승인 사용자 로그인 시 403 Forbidden")
        void notApproved() throws Exception {
            given(authService.login(any()))
                    .willThrow(new BusinessException(UserErrorCode.USER_NOT_APPROVED));

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("실패 - username 공백 시 400 Bad Request")
        void blankUsername() throws Exception {
            String body = """
                    { "username": "", "password": "Test1234!" }
                    """;

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    // ────────────────────────────────────────────────
    // POST /api/v1/auth/refresh
    // ────────────────────────────────────────────────
    @Nested
    @DisplayName("POST /refresh - 토큰 갱신")
    class Refresh {

        @Test
        @DisplayName("성공 - 200 OK, 새 토큰 헤더에 포함")
        void success() throws Exception {
            given(authService.refresh("valid-refresh-token")).willReturn(mockToken);

            mockMvc.perform(post("/api/v1/auth/refresh")
                            .header("X-Refresh-Token", "valid-refresh-token"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Authorization", "Bearer mock-access-token"))
                    .andExpect(header().string("X-Refresh-Token", "mock-refresh-token"));
        }

        @Test
        @DisplayName("실패 - 유효하지 않은 토큰 시 401 Unauthorized")
        void invalidToken() throws Exception {
            given(authService.refresh("invalid-token"))
                    .willThrow(new BusinessException(UserErrorCode.INVALID_TOKEN));

            mockMvc.perform(post("/api/v1/auth/refresh")
                            .header("X-Refresh-Token", "invalid-token"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("실패 - 헤더 누락 시 400 Bad Request")
        void missingHeader() throws Exception {
            mockMvc.perform(post("/api/v1/auth/refresh"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ────────────────────────────────────────────────
    // PATCH /api/v1/auth/signup/{userId}/approve
    // ────────────────────────────────────────────────
    @Nested
    @DisplayName("PATCH /signup/{userId}/approve - 회원가입 승인")
    class Approve {

        private ApproveResponse approveResponse;

        @BeforeEach
        void setUp() {
            approveResponse = ApproveResponse.builder()
                    .userId(userId)
                    .username("testuser")
                    .status(UserStatus.APPROVED)
                    .role(Role.MASTER)
                    .build();
        }

        @Test
        @DisplayName("성공 - MASTER 역할로 승인")
        void approveByMaster() throws Exception {
            given(authService.approveUserByMaster(userId)).willReturn(approveResponse);

            mockMvc.perform(patch("/api/v1/auth/signup/{userId}/approve", userId)
                            .header("X-User-Role", "MASTER"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("APPROVED"));
        }

        @Test
        @DisplayName("성공 - HUB_MANAGER가 자신의 허브 소속 유저 승인")
        void approveByHubManager() throws Exception {
            UUID hubId = UUID.randomUUID();
            given(authService.approveUserByHub(userId, hubId)).willReturn(approveResponse);

            mockMvc.perform(patch("/api/v1/auth/signup/{userId}/approve", userId)
                            .header("X-User-Role", "HUB_MANAGER")
                            .header("X-User-HubId", hubId.toString()))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("실패 - HUB_MANAGER인데 hubId 헤더 없으면 403 Forbidden")
        void hubManagerWithoutHubId() throws Exception {
            mockMvc.perform(patch("/api/v1/auth/signup/{userId}/approve", userId)
                            .header("X-User-Role", "HUB_MANAGER"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("실패 - COMPANY_MANAGER 역할은 403 Forbidden")
        void companyManagerForbidden() throws Exception {
            mockMvc.perform(patch("/api/v1/auth/signup/{userId}/approve", userId)
                            .header("X-User-Role", "COMPANY_MANAGER"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("실패 - 이미 처리된 유저 승인 시 400 Bad Request")
        void alreadyProcessed() throws Exception {
            given(authService.approveUserByMaster(userId))
                    .willThrow(new BusinessException(UserErrorCode.ALREADY_PROCESSED));

            mockMvc.perform(patch("/api/v1/auth/signup/{userId}/approve", userId)
                            .header("X-User-Role", "MASTER"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ────────────────────────────────────────────────
    // PATCH /api/v1/auth/signup/{userId}/reject
    // ────────────────────────────────────────────────
    @Nested
    @DisplayName("PATCH /signup/{userId}/reject - 회원가입 거절")
    class Reject {

        private ApproveResponse rejectResponse;

        @BeforeEach
        void setUp() {
            rejectResponse = ApproveResponse.builder()
                    .userId(userId)
                    .username("testuser")
                    .status(UserStatus.REJECTED)
                    .role(Role.MASTER)
                    .build();
        }

        @Test
        @DisplayName("성공 - MASTER 역할로 거절")
        void rejectByMaster() throws Exception {
            given(authService.rejectUserByMaster(userId)).willReturn(rejectResponse);

            mockMvc.perform(patch("/api/v1/auth/signup/{userId}/reject", userId)
                            .header("X-User-Role", "MASTER"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("REJECTED"));
        }

        @Test
        @DisplayName("성공 - HUB_MANAGER가 자신의 허브 소속 유저 거절")
        void rejectByHubManager() throws Exception {
            UUID hubId = UUID.randomUUID();
            given(authService.rejectUserByHub(userId, hubId)).willReturn(rejectResponse);

            mockMvc.perform(patch("/api/v1/auth/signup/{userId}/reject", userId)
                            .header("X-User-Role", "HUB_MANAGER")
                            .header("X-User-HubId", hubId.toString()))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("실패 - HUB_MANAGER인데 hubId 헤더 없으면 403 Forbidden")
        void hubManagerWithoutHubId() throws Exception {
            mockMvc.perform(patch("/api/v1/auth/signup/{userId}/reject", userId)
                            .header("X-User-Role", "HUB_MANAGER"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("실패 - DELIVERY_MANAGER 역할은 403 Forbidden")
        void deliveryManagerForbidden() throws Exception {
            mockMvc.perform(patch("/api/v1/auth/signup/{userId}/reject", userId)
                            .header("X-User-Role", "DELIVERY_MANAGER"))
                    .andExpect(status().isForbidden());
        }
    }
}
