package com.sparta.logistics.user.application.service;

import com.sparta.logistics.common.domain.Role;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.user.domain.model.entity.UserEntity;
import com.sparta.logistics.user.domain.model.enums.UserStatus;
import com.sparta.logistics.user.domain.repository.UserRepository;
import com.sparta.logistics.user.exception.UserErrorCode;
import com.sparta.logistics.user.presentation.dto.request.UpdateRequest;
import com.sparta.logistics.user.presentation.dto.response.DeleteResponse;
import com.sparta.logistics.user.presentation.dto.response.GetResponse;
import com.sparta.logistics.user.presentation.dto.response.UpdateResponse;
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
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService 단위 테스트")
class UserServiceTest {

    @InjectMocks
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    private UserEntity mockUser;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        mockUser = UserEntity.builder()
                .id(userId)
                .username("testuser")
                .password("encodedPw")
                .name("홍길동")
                .email("hong@test.com")
                .slackId("U123456")
                .role(Role.MASTER)
                .status(UserStatus.APPROVED)
                .build();
    }

    // ────────────────────────────────────────────────
    // getUser
    // ────────────────────────────────────────────────
    @Nested
    @DisplayName("getUser - 단일 유저 조회")
    class GetUser {

        @Test
        @DisplayName("성공 - 존재하는 유저를 조회하면 GetResponse 반환")
        void success() {
            given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(mockUser));

            GetResponse response = userService.getUser(userId);

            assertThat(response).isNotNull();
            assertThat(response.username()).isEqualTo("testuser");
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 userId 조회 시 USER_NOT_FOUND 예외")
        void notFound() {
            given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getUser(userId))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(UserErrorCode.USER_NOT_FOUND));
        }
    }

    // ────────────────────────────────────────────────
    // checkUserExists
    // ────────────────────────────────────────────────
    @Nested
    @DisplayName("checkUserExists - 유저 존재 여부 확인")
    class CheckUserExists {

        @Test
        @DisplayName("성공 - 유저가 존재하면 예외 없음")
        void success() {
            given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(mockUser));

            // 예외가 발생하지 않으면 성공
            userService.checkUserExists(userId);
        }

        @Test
        @DisplayName("실패 - 유저가 없으면 USER_NOT_FOUND 예외")
        void notFound() {
            given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> userService.checkUserExists(userId))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(UserErrorCode.USER_NOT_FOUND));
        }
    }

    // ────────────────────────────────────────────────
    // getUsers
    // ────────────────────────────────────────────────
    @Nested
    @DisplayName("getUsers - 전체 유저 조회")
    class GetUsers {

        @Test
        @DisplayName("성공 - 조건 없이 전체 조회 시 Page<GetResponse> 반환")
        void success() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<UserEntity> page = new PageImpl<>(List.of(mockUser));
            given(userRepository.searchUsers(null, null, null, null, pageable)).willReturn(page);

            Page<GetResponse> result = userService.getUsers(null, null, null, null, pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).username()).isEqualTo("testuser");
        }
    }

    // ────────────────────────────────────────────────
    // updateUser
    // ────────────────────────────────────────────────
    @Nested
    @DisplayName("updateUser - 유저 정보 수정")
    class UpdateUser {

        @Test
        @DisplayName("성공 - 이메일 변경 없이 정보 수정")
        void successWithoutEmailChange() {
            UpdateRequest request = new UpdateRequest("김철수", "hong@test.com", "U9999", Role.MASTER, null, null);
            given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(mockUser));

            UpdateResponse response = userService.updateUser(userId, request);

            assertThat(response).isNotNull();
            verify(userRepository).findByIdAndDeletedAtIsNull(userId);
        }

        @Test
        @DisplayName("성공 - 새로운 이메일로 변경 (중복 없음)")
        void successWithNewEmail() {
            UpdateRequest request = new UpdateRequest("김철수", "new@test.com", "U9999", Role.MASTER, null, null);
            given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(mockUser));
            given(userRepository.existsByEmail("new@test.com")).willReturn(false);

            UpdateResponse response = userService.updateUser(userId, request);

            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("실패 - 이미 사용 중인 이메일로 변경 시 EMAIL_ALREADY_EXISTS 예외")
        void duplicateEmail() {
            UpdateRequest request = new UpdateRequest("김철수", "other@test.com", "U9999", Role.MASTER, null, null);
            given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(mockUser));
            given(userRepository.existsByEmail("other@test.com")).willReturn(true);

            assertThatThrownBy(() -> userService.updateUser(userId, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(UserErrorCode.EMAIL_ALREADY_EXISTS));
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 유저 수정 시 USER_NOT_FOUND 예외")
        void userNotFound() {
            UpdateRequest request = new UpdateRequest("김철수", null, "U9999", Role.MASTER, null, null);
            given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> userService.updateUser(userId, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(UserErrorCode.USER_NOT_FOUND));
        }
    }

    // ────────────────────────────────────────────────
    // deleteUser
    // ────────────────────────────────────────────────
    @Nested
    @DisplayName("deleteUser - 유저 소프트 삭제")
    class DeleteUser {

        @Test
        @DisplayName("성공 - 유저 소프트 삭제")
        void success() {
            UUID requesterId = UUID.randomUUID();
            given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(mockUser));

            DeleteResponse response = userService.deleteUser(userId, requesterId);

            assertThat(response).isNotNull();
            verify(userRepository).findByIdAndDeletedAtIsNull(userId);
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 유저 삭제 시 USER_NOT_FOUND 예외")
        void notFound() {
            UUID requesterId = UUID.randomUUID();
            given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> userService.deleteUser(userId, requesterId))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(UserErrorCode.USER_NOT_FOUND));
        }
    }
}
