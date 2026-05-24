package com.sparta.logistics.user.application.service;

import com.sparta.logistics.common.domain.Role;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.user.client.CompanyServiceClient;
import com.sparta.logistics.user.client.HubServiceClient;
import com.sparta.logistics.user.domain.model.entity.UserEntity;
import com.sparta.logistics.user.domain.model.enums.UserStatus;
import com.sparta.logistics.user.domain.repository.UserRepository;
import com.sparta.logistics.user.exception.UserErrorCode;
import com.sparta.logistics.user.presentation.dto.request.UpdateRequest;
import com.sparta.logistics.user.presentation.dto.response.DeleteResponse;
import com.sparta.logistics.user.presentation.dto.response.GetResponse;
import com.sparta.logistics.user.presentation.dto.response.UpdateResponse;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final HubServiceClient hubServiceClient;
    private final CompanyServiceClient companyServiceClient;

    // 전체 조회
    public Page<GetResponse> getUsers(String username, String name, Role role, UserStatus status, Pageable pageable) {
        return userRepository.searchUsers(username, name, role, status, pageable)
                .map(GetResponse::from);
    }

    // 유저 조회
    public GetResponse getUser(UUID userId) {
        UserEntity user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
        return GetResponse.from(user);
    }

    // 유저 존재 여부 확인 (내부 서비스용)
    public void checkUserExists(UUID userId) {
        userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
    }

    // 사용자 수정
    @Transactional
    public UpdateResponse updateUser(UUID userId, UpdateRequest request) {
        UserEntity user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
        validateHubAndCompany(request.hubId(), request.companyId());
        user.update(request.name(), request.email(), request.slackId(), request.role(), request.hubId(), request.companyId());
        return UpdateResponse.from(user);
    }

    // 사용자 삭제
    @Transactional
    public DeleteResponse deleteUser(UUID userId, UUID requesterId) {
        UserEntity user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
        user.softDelete(requesterId);
        return DeleteResponse.from(user);
    }

    // hubId, companyId 존재 여부 검증
    private void validateHubAndCompany(UUID hubId, UUID companyId) {
        if (hubId != null) {
            try {
                hubServiceClient.checkHubExists(hubId);
            } catch (FeignException.NotFound e) {
                throw new BusinessException(UserErrorCode.HUB_NOT_FOUND);
            } catch (FeignException e) {
                throw new BusinessException(UserErrorCode.HUB_SERVICE_UNAVAILABLE);
            }
        }
        if (companyId != null) {
            try {
                companyServiceClient.checkCompanyExists(companyId);
            } catch (FeignException.NotFound e) {
                throw new BusinessException(UserErrorCode.COMPANY_NOT_FOUND);
            } catch (FeignException e) {
                throw new BusinessException(UserErrorCode.COMPANY_SERVICE_UNAVAILABLE);
            }
        }
    }
}
