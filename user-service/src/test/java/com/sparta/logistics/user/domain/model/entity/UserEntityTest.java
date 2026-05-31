package com.sparta.logistics.user.domain.model.entity;

import com.sparta.logistics.common.domain.Role;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.user.user.enums.UserStatus;
import com.sparta.logistics.user.exception.UserErrorCode;
import com.sparta.logistics.user.user.entity.UserEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("UserEntity 단위 테스트")
class UserEntityTest {

    // ────────────────────────────────────────────────
    // validateRoleConstraints
    // ────────────────────────────────────────────────
    @Nested
    @DisplayName("validateRoleConstraints - 역할 제약 조건 검증")
    class ValidateRoleConstraints {

        @Test
        @DisplayName("성공 - HUB_MANAGER는 hubId 필수, companyId 없어야 함")
        void hubManagerWithHubId() {
            UserEntity user = UserEntity.builder()
                    .username("hubmgr")
                    .password("pw")
                    .name("허브매니저")
                    .role(Role.HUB_MANAGER)
                    .hubId(UUID.randomUUID())
                    .build();

            // 예외 없이 통과해야 함
            user.validateRoleConstraints();
        }

        @Test
        @DisplayName("실패 - HUB_MANAGER에 hubId 없으면 HUB_ID_REQUIRED 예외")
        void hubManagerWithoutHubId() {
            UserEntity user = UserEntity.builder()
                    .username("hubmgr")
                    .password("pw")
                    .name("허브매니저")
                    .role(Role.HUB_MANAGER)
                    .build();

            assertThatThrownBy(user::validateRoleConstraints)
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(UserErrorCode.HUB_ID_REQUIRED));
        }

        @Test
        @DisplayName("실패 - HUB_MANAGER에 companyId 있으면 COMPANY_ID_NOT_ALLOWED 예외")
        void hubManagerWithCompanyId() {
            UserEntity user = UserEntity.builder()
                    .username("hubmgr")
                    .password("pw")
                    .name("허브매니저")
                    .role(Role.HUB_MANAGER)
                    .hubId(UUID.randomUUID())
                    .companyId(UUID.randomUUID())
                    .build();

            assertThatThrownBy(user::validateRoleConstraints)
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(UserErrorCode.COMPANY_ID_NOT_ALLOWED));
        }

        @Test
        @DisplayName("성공 - DELIVERY_MANAGER는 hubId 필수, companyId 없어야 함")
        void deliveryManagerWithHubId() {
            UserEntity user = UserEntity.builder()
                    .username("dlvmgr")
                    .password("pw")
                    .name("배송매니저")
                    .role(Role.DELIVERY_MANAGER)
                    .hubId(UUID.randomUUID())
                    .build();

            user.validateRoleConstraints();
        }

        @Test
        @DisplayName("성공 - COMPANY_MANAGER는 companyId 필수")
        void companyManagerWithCompanyId() {
            UserEntity user = UserEntity.builder()
                    .username("compmgr")
                    .password("pw")
                    .name("업체매니저")
                    .role(Role.COMPANY_MANAGER)
                    .companyId(UUID.randomUUID())
                    .build();

            user.validateRoleConstraints();
        }

        @Test
        @DisplayName("실패 - COMPANY_MANAGER에 companyId 없으면 COMPANY_ID_REQUIRED 예외")
        void companyManagerWithoutCompanyId() {
            UserEntity user = UserEntity.builder()
                    .username("compmgr")
                    .password("pw")
                    .name("업체매니저")
                    .role(Role.COMPANY_MANAGER)
                    .build();

            assertThatThrownBy(user::validateRoleConstraints)
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(UserErrorCode.COMPANY_ID_REQUIRED));
        }

        @Test
        @DisplayName("성공 - MASTER는 hubId, companyId 없어야 함")
        void masterWithoutHubAndCompany() {
            UserEntity user = UserEntity.builder()
                    .username("master")
                    .password("pw")
                    .name("마스터")
                    .role(Role.MASTER)
                    .build();

            user.validateRoleConstraints();
        }

        @Test
        @DisplayName("실패 - MASTER에 hubId 있으면 MASTER_CANNOT_HAVE_HUB_OR_COMPANY 예외")
        void masterWithHubId() {
            UserEntity user = UserEntity.builder()
                    .username("master")
                    .password("pw")
                    .name("마스터")
                    .role(Role.MASTER)
                    .hubId(UUID.randomUUID())
                    .build();

            assertThatThrownBy(user::validateRoleConstraints)
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(UserErrorCode.MASTER_CANNOT_HAVE_HUB_OR_COMPANY));
        }

        @Test
        @DisplayName("실패 - MASTER에 companyId 있으면 MASTER_CANNOT_HAVE_HUB_OR_COMPANY 예외")
        void masterWithCompanyId() {
            UserEntity user = UserEntity.builder()
                    .username("master")
                    .password("pw")
                    .name("마스터")
                    .role(Role.MASTER)
                    .companyId(UUID.randomUUID())
                    .build();

            assertThatThrownBy(user::validateRoleConstraints)
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(UserErrorCode.MASTER_CANNOT_HAVE_HUB_OR_COMPANY));
        }
    }

    // ────────────────────────────────────────────────
    // approve / reject
    // ────────────────────────────────────────────────
    @Nested
    @DisplayName("approve / reject - 상태 변경")
    class ApproveReject {

        @Test
        @DisplayName("성공 - PENDING 상태에서 approve 호출 시 APPROVED로 변경")
        void approveSuccess() {
            UserEntity user = buildPendingUser();

            user.approve();

            assertThat(user.getStatus()).isEqualTo(UserStatus.APPROVED);
        }

        @Test
        @DisplayName("실패 - APPROVED 상태에서 approve 호출 시 ALREADY_PROCESSED 예외")
        void approveAlreadyApproved() {
            UserEntity user = buildApprovedUser();

            assertThatThrownBy(user::approve)
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(UserErrorCode.ALREADY_PROCESSED));
        }

        @Test
        @DisplayName("성공 - PENDING 상태에서 reject 호출 시 REJECTED로 변경")
        void rejectSuccess() {
            UserEntity user = buildPendingUser();

            user.reject();

            assertThat(user.getStatus()).isEqualTo(UserStatus.REJECTED);
        }

        @Test
        @DisplayName("실패 - REJECTED 상태에서 reject 호출 시 ALREADY_PROCESSED 예외")
        void rejectAlreadyRejected() {
            UserEntity user = UserEntity.builder()
                    .username("user")
                    .password("pw")
                    .name("유저")
                    .role(Role.MASTER)
                    .status(UserStatus.REJECTED)
                    .build();

            assertThatThrownBy(user::reject)
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(UserErrorCode.ALREADY_PROCESSED));
        }
    }

    // ────────────────────────────────────────────────
    // setRoleAndApprove
    // ────────────────────────────────────────────────
    @Nested
    @DisplayName("setRoleAndApprove - 첫 가입자 자동 설정")
    class SetRoleAndApprove {

        @Test
        @DisplayName("성공 - role이 MASTER로, status가 APPROVED로 설정됨")
        void success() {
            UserEntity user = buildPendingUser();

            user.setRoleAndApprove();

            assertThat(user.getRole()).isEqualTo(Role.MASTER);
            assertThat(user.getStatus()).isEqualTo(UserStatus.APPROVED);
        }
    }

    // ────────────────────────────────────────────────
    // update
    // ────────────────────────────────────────────────
    @Nested
    @DisplayName("update - 유저 정보 업데이트")
    class Update {

        @Test
        @DisplayName("성공 - 이름/이메일/slackId/role 변경")
        void success() {
            UserEntity user = buildApprovedUser();

            user.update("새이름", "new@test.com", "NEWSLACK", Role.MASTER, null, null);

            assertThat(user.getName()).isEqualTo("새이름");
            assertThat(user.getEmail()).isEqualTo("new@test.com");
            assertThat(user.getSlackId()).isEqualTo("NEWSLACK");
        }

        @Test
        @DisplayName("실패 - HUB_MANAGER로 역할 변경 시 hubId 없으면 예외")
        void updateToHubManagerWithoutHubId() {
            UserEntity user = buildApprovedUser();

            assertThatThrownBy(() -> user.update("이름", null, "slack", Role.HUB_MANAGER, null, null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(UserErrorCode.HUB_ID_REQUIRED));
        }
    }

    // ────────────────────────────────────────────────
    // helpers
    // ────────────────────────────────────────────────
    private UserEntity buildPendingUser() {
        return UserEntity.builder()
                .username("pendinguser")
                .password("pw")
                .name("대기유저")
                .role(Role.MASTER)
                .status(UserStatus.PENDING)
                .build();
    }

    private UserEntity buildApprovedUser() {
        return UserEntity.builder()
                .username("approveduser")
                .password("pw")
                .name("승인유저")
                .role(Role.MASTER)
                .status(UserStatus.APPROVED)
                .build();
    }
}
