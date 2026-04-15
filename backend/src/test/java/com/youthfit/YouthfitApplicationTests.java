package com.youthfit;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("외부 인프라(DB, Redis) 연결이 필요하여 CI에서 제외")
class YouthfitApplicationTests {

    @Test
    void contextLoads() {
    }

}
