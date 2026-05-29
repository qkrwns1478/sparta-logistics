package com.sparta.logistics.hub.config;

import com.sparta.logistics.common.domain.Role;
import com.sparta.logistics.hub.hub.dto.request.CreateHubRequest;
import com.sparta.logistics.hub.hub.service.HubService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Profile("!test")
@Component
@RequiredArgsConstructor
@Order(1)
public class DataInitializer implements ApplicationRunner {

    private final HubService hubService;

    @Override
    public void run(ApplicationArguments args) {
        if (hubService.count() > 0) return;

        List<CreateHubRequest> hubs = List.of(
                CreateHubRequest.of("서울특별시 센터",     "서울특별시 송파구 송파대로 55",                37.5146, 127.1054),
                CreateHubRequest.of("경기 북부 센터",      "경기도 고양시 덕양구 권율대로 570",            37.6588, 126.8320),
                CreateHubRequest.of("경기 남부 센터",      "경기도 이천시 덕평로 257-21",                  37.1526, 127.3867),
                CreateHubRequest.of("인천광역시 센터",     "인천 남동구 정각로 29",                        37.4563, 126.7052),
                CreateHubRequest.of("강원특별자치도 센터", "강원특별자치도 춘천시 중앙로 1",               37.8813, 127.7298),
                CreateHubRequest.of("충청북도 센터",       "충북 청주시 상당구 상당로 82",                 36.6357, 127.4917),
                CreateHubRequest.of("충청남도 센터",       "충남 홍성군 홍북읍 충남대로 21",               36.6024, 126.6600),
                CreateHubRequest.of("대전광역시 센터",     "대전 서구 둔산로 100",                         36.3504, 127.3845),
                CreateHubRequest.of("세종특별자치시 센터", "세종특별자치시 한누리대로 2130",               36.4801, 127.2890),
                CreateHubRequest.of("경상북도 센터",       "경북 안동시 풍천면 도청대로 455",              36.5760, 128.5055),
                CreateHubRequest.of("대구광역시 센터",     "대구 북구 태평로 161",                         35.8714, 128.6014),
                CreateHubRequest.of("전북특별자치도 센터", "전북특별자치도 전주시 완산구 효자로 225",      35.8242, 127.1480),
                CreateHubRequest.of("광주광역시 센터",     "광주 서구 내방로 111",                         35.1595, 126.8526),
                CreateHubRequest.of("전라남도 센터",       "전남 무안군 삼향읍 오룡길 1",                  34.8679, 126.4430),
                CreateHubRequest.of("울산광역시 센터",     "울산 남구 중앙로 201",                         35.5384, 129.3114),
                CreateHubRequest.of("경상남도 센터",       "경남 창원시 의창구 중앙대로 300",              35.2278, 128.6817),
                CreateHubRequest.of("부산광역시 센터",     "부산 동구 중앙대로 206",                       35.1796, 129.0756)
        );

        for (CreateHubRequest request : hubs) {
            hubService.createHub(request, Role.MASTER);
        }
    }
}
