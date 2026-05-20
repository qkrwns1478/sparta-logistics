package com.sparta.logistics.user.application.service;

import com.sparta.logistics.user.application.dto.response.UserResult;
import com.sparta.logistics.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
public class UserService {


//    private final UserRepository userRepository;
//
//    public UserResult getUser() {
//
//
//        return null;
//    }
}
