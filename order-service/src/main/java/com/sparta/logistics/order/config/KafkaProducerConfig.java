package com.sparta.logistics.order.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka 프로듀서 설정
 * 모든 이벤트를 JSON으로 직렬화하고 __TypeId__ 헤더를 포함해 컨슈머가 타입을 자동으로 복원할 수 있게 함
 * 파티션 키는 각 발행 지점(Producer)에서 orderId 또는 deliveryId를 사용함
 * 동일 키는 같은 파티션으로 라우팅되어 이벤트 순서가 보장됨
 * */
@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers); // 연결할 Kafka 브로커 주소
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class); // 파티션 키를 문자열로 직렬화
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class); // 페이로드(이벤트 객체)를 JSON으로 직렬화
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, true); // 메시지 헤더에 __TypeId__ 추가
        props.put(ProducerConfig.ACKS_CONFIG, "all"); // ISR 전체가 확인해야 ack 반환
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true); // 멱등성 프로듀서 활성화
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
