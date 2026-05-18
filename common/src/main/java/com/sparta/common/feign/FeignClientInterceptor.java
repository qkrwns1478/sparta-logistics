package com.sparta.common.feign;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class FeignClientInterceptor implements RequestInterceptor{

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLE_HEADER = "X-User-Role";

    @Override
    public void apply(RequestTemplate template){
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (attributes != null){
            HttpServletRequest request = attributes.getRequest();
            String userId = request.getHeader(USER_ID_HEADER);
            String userRole = request.getHeader(USER_ROLE_HEADER);

            if (userId != null) template.header(USER_ID_HEADER, userId);
            if (userRole != null) template.header(USER_ROLE_HEADER, userRole);
        }
    }
}
