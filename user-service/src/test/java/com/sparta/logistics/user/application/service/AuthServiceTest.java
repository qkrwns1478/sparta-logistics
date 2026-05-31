package com.sparta.logistics.user.application.service;

import com.sparta.logistics.common.domain.Role;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.user.auth.internal.command.LoginCommand;
import com.sparta.logistics.user.auth.internal.command.SignupCommand;
import com.sparta.logistics.user.auth.internal.result.Token;
import com.sparta.logistics.user.auth.internal.result.UserResult;
import com.sparta.logistics.user.auth.service.AuthService;
import com.sparta.logistics.user.user.entity.UserEntity;
import com.sparta.logistics.user.user.enums.UserStatus;
import com.sparta.logistics.user.auth.repository.RefreshTokenRepository;
import com.sparta.logistics.user.user.repository.UserRepository;
import com.sparta.logistics.user.exception.UserErrorCode;
import com.sparta.logistics.user.user.dto.response.ApproveResponse;
import com.sparta.logistics.user.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService 단위 테스트")
class AuthServiceTest {

    @InjectMocks
    private AuthService authService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private UserEntity approvedUser;
    private UserEntity pendingUser;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();

        approvedUser = UserEntity.builder()
                .id(userId)
                .username("testuser")
                .password("encodedPw")
                .name("홍길동")
                .email("hong@test.com")
                .slackId("U123456")
                .role(Role.MASTER)
                .status(UserStatus.APPROVED)
                .build();

        pendingUser = UserEntity.builder()
                .id(UUID.randomUUID())
                .username("pendinguser")
                .password("encodedPw")
                .name("김대기")
                .email("pending@test.com")
                .slackId("U999")
                .role(Role.MASTER)
                .status(UserStatus.PENDING)
                .build();
    }

    // ────────────────────────────────────────────────
    // signUp
    // ────────────────────────────────────────────────
    @Nested
    @DisplayName("signUp - 회원가입")
    class SignUp {

        @Test
        @DisplayName("성공 - 일반 회원가입")
        void success() {
            SignupCommand command = SignupCommand.builder()
                    .username("newuser")
                    .password("pass1234")
                    .name("신규유저")
                    .email("new@test.com")
                    .role(Role.MASTER)
                    .build();

            given(userRepository.existsByUsername("newuser")).willReturn(false);
            given(userRepository.existsByEmail("new@test.com")).willReturn(false);
            given(passwordEncoder.encode("pass1234")).willReturn("encoded");
            given(userRepository.existsAny()).willReturn(true);
            given(userRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            UserResult result = authService.signUp(command);

            assertThat(result).isNotNull();
            assertThat(result.username()).isEqualTo("newuser");
        }

        @Test
        @DisplayName("성공 - 첫 번째 가입자는 MASTER + APPROVED 자동 부여")
        void firstUserBecomeMaster() {
            SignupCommand command = SignupCommand.builder()
                    .username("firstuser")
                    .password("pass1234")
                    .name("최초유저")
                    .role(Role.MASTER)
                    .build();

            given(userRepository.existsByUsername("firstuser")).willReturn(false);
            given(passwordEncoder.encode("pass1234")).willReturn("encoded");
            given(userRepository.existsAny()).willReturn(false);
            given(userRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            UserResult result = authService.signUp(command);

            assertThat(result.status()).isEqualTo(UserStatus.APPROVED);
            assertThat(result.role()).isEqualTo(Role.MASTER);
        }

        @Test
        @DisplayName("실패 - 중복 username 시 USER_ALREADY_EXISTS 예외")
        void duplicateUsername() {
            SignupCommand command = SignupCommand.builder()
                    .username("testuser")
                    .password("pass1234")
                    .name("중복유저")
                    .role(Role.MASTER)
                    .build();

            given(userRepository.existsByUsername("testuser")).willReturn(true);

            assertThatThrownBy(() -> authService.signUp(command))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(UserErrorCode.USER_ALREADY_EXISTS));
        }

        @Test
        @DisplayName("실패 - 중복 이메일 시 EMAIL_ALREADY_EXISTS 예외")
        void duplicateEmail() {
            SignupCommand command = SignupCommand.builder()
                    .username("newuser2")
                    .password("pass1234")
                    .name("이메일중복")
                    .email("hong@test.com")
                    .role(Role.MASTER)
                    .build();

            given(userRepository.existsByUsername("newuser2")).willReturn(false);
            given(userRepository.existsByEmail("hong@test.com")).willReturn(true);

            assertThatThrownBy(() -> authService.signUp(command))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(UserErrorCode.EMAIL_ALREADY_EXISTS));
        }
    }

    // ────────────────────────────────────────────────
    // login
    // ────────────────────────────────────────────────
    @Nested
    @DisplayName("login - 로그인")
    class Login {

        @Test
        @DisplayName("성공 - 올바른 자격증명 + APPROVED 상태")
        void success() {
            LoginCommand command = LoginCommand.builder()
                    .username("testuser")
                    .password("pass1234")
                    .build();

            given(userRepository.findByUsernameAndDeletedAtIsNull("testuser")).willReturn(Optional.of(approvedUser));
            given(passwordEncoder.matches("pass1234", "encodedPw")).willReturn(true);
            given(jwtUtil.createAccessToken(any(), any(), any(), any())).willReturn("access-token");
            given(jwtUtil.createRefreshToken(any(), any(), any(), any())).willReturn("refresh-token");

            Token token = authService.login(command);

            assertThat(token.accessToken()).isEqualTo("access-token");
            assertThat(token.refreshToken()).isEqualTo("refresh-token");
            verify(refreshTokenRepository).save(any(), eq("refresh-token"));
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 username")
        void userNotFound() {
            LoginCommand command = LoginCommand.builder()
                    .username("ghost")
                    .password("pass1234")
                    .build();

            given(userRepository.findByUsernameAndDeletedAtIsNull("ghost")).willReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(command))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(UserErrorCode.USER_NOT_FOUND));
        }

        @Test
        @DisplayName("실패 - 비밀번호 불일치")
        void wrongPassword() {
            LoginCommand command = LoginCommand.builder()
                    .username("testuser")
                    .password("wrongpw")
                    .build();

            given(userRepository.findByUsernameAndDeletedAtIsNull("testuser")).willReturn(Optional.of(approvedUser));
            given(passwordEncoder.matches("wrongpw", "encodedPw")).willReturn(false);

            assertThatThrownBy(() -> authService.login(command))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(UserErrorCode.PASSWORD_NOT_MATCH));
        }

        @Test
        @DisplayName("실패 - PENDING 상태 사용자 로그인 불가")
        void notApproved() {
            LoginCommand command = LoginCommand.builder()
                    .username("pendinguser")
                    .password("pass1234")
                    .build();

            given(userRepository.findByUsernameAndDeletedAtIsNull("pendinguser")).willReturn(Optional.of(pendingUser));
            given(passwordEncoder.matches("pass1234", "encodedPw")).willReturn(true);

            assertThatThrownBy(() -> authService.login(command))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(UserErrorCode.USER_NOT_APPROVED));
        }
    }

    // ────────────────────────────────────────────────
    // approveUserByMaster / rejectUserByMaster
    // ────────────────────────────────────────────────
    @Nested
    @DisplayName("approve/reject - 마스터 승인/거절")
    class ApproveRejectByMaster {

        @Test
        @DisplayName("성공 - 마스터가 PENDING 유저 승인")
        void approveSuccess() {
            given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(pendingUser));

            ApproveResponse response = authService.approveUserByMaster(userId);

            assertThat(response).isNotNull();
            assertThat(pendingUser.getStatus()).isEqualTo(UserStatus.APPROVED);
        }

        @Test
        @DisplayName("성공 - 마스터가 PENDING 유저 거절")
        void rejectSuccess() {
            given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(pendingUser));

            ApproveResponse response = authService.rejectUserByMaster(userId);

            assertThat(response).isNotNull();
            assertThat(pendingUser.getStatus()).isEqualTo(UserStatus.REJECTED);
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 유저 승인 시 USER_NOT_FOUND 예외")
        void approveNotFound() {
            given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> authService.approveUserByMaster(userId))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(UserErrorCode.USER_NOT_FOUND));
        }

        @Test
        @DisplayName("실패 - 이미 처리된 유저 승인 시 ALREADY_PROCESSED 예외")
        void approveAlreadyProcessed() {
            given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(approvedUser));

            assertThatThrownBy(() -> authService.approveUserByMaster(userId))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(UserErrorCode.ALREADY_PROCESSED));
        }
    }

    // ────────────────────────────────────────────────
    // approveUserByHub / rejectUserByHub
    // ────────────────────────────────────────────────
    @Nested
    @DisplayName("approve/reject - 허브 매니저 승인/거절")
    class ApproveRejectByHub {

        private UUID hubId;
        private UserEntity hubUser;

        @BeforeEach
        void setUp() {
            hubId = UUID.randomUUID();
            hubUser = UserEntity.builder()
                    .id(UUID.randomUUID())
                    .username("hubuser")
                    .password("encodedPw")
                    .name("허브유저")
                    .role(Role.HUB_MANAGER)
                    .hubId(hubId)
                    .status(UserStatus.PENDING)
                    .build();
        }

        @Test
        @DisplayName("성공 - 같은 허브 소속 유저 승인")
        void approveSuccess() {
            given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(hubUser));

            ApproveResponse response = authService.approveUserByHub(userId, hubId);

            assertThat(response).isNotNull();
            assertThat(hubUser.getStatus()).isEqualTo(UserStatus.APPROVED);
        }

        @Test
        @DisplayName("실패 - 다른 허브 소속 유저 승인 시 HUB_MISMATCH 예외")
        void hubMismatch() {
            UUID otherHubId = UUID.randomUUID();
            given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(hubUser));

            assertThatThrownBy(() -> authService.approveUserByHub(userId, otherHubId))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(UserErrorCode.HUB_MISMATCH));
        }

        @Test
        @DisplayName("성공 - 같은 허브 소속 유저 거절")
        void rejectSuccess() {
            given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(hubUser));

            ApproveResponse response = authService.rejectUserByHub(userId, hubId);

            assertThat(response).isNotNull();
            assertThat(hubUser.getStatus()).isEqualTo(UserStatus.REJECTED);
        }

        @Test
        @DisplayName("실패 - 다른 허브 소속 유저 거절 시 HUB_MISMATCH 예외")
        void rejectHubMismatch() {
            UUID otherHubId = UUID.randomUUID();
            given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(hubUser));

            assertThatThrownBy(() -> authService.rejectUserByHub(userId, otherHubId))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(UserErrorCode.HUB_MISMATCH));
        }
    }
}
