//package com.example.reserve;
//
//import jakarta.annotation.PostConstruct;
//import jakarta.annotation.PreDestroy;
//import redis.embedded.RedisServer;
//
//import java.io.File;
//import java.io.IOException;
//
//public class TestRedisConfiguration {
//
//    private final RedisServer redisServer;
//
//    // 포트는 0~65535 범위
//    // Mac에서는 embedded redis Redis 바이너리 호환성 문제 때문에 redis-server 바이너리 경로를 수동으로 지정해줘야 함
//    public TestRedisConfiguration() throws IOException {
//        this.redisServer = new RedisServer(new File("src/main/resources/binary/redis/redis-mac-arm"), 6386);
//    }
//
//    @PostConstruct
//    public void start() throws IOException {
//        this.redisServer.start();
//    }
//    @PreDestroy
//    public void stop() throws IOException {
//        this.redisServer.stop();
//    }
//}
