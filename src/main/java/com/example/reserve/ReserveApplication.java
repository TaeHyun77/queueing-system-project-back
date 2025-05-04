package com.example.reserve;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.data.redis.core.ReactiveRedisTemplate;

@RequiredArgsConstructor
@SpringBootApplication
public class ReserveApplication { // implements ApplicationListener<ApplicationReadyEvent>

    // redis 객체
    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

    public static void main(String[] args) {
        SpringApplication.run(ReserveApplication.class, args);
    }

//    Spring Boot가 정상 기동 완료 되었을 때 아래 메서드를 호출, 테스트용 데이터를 redis에 미리 넣고 싶을 때 사용
//    @Override
//    public void onApplicationEvent(ApplicationReadyEvent event) {
//        reactiveRedisTemplate.opsForValue().set("testKey", "testValue").subscribe();
//    }
}