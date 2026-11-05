package com.example.plimap.global.external.kakao;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class KakaoUserApiClientImplTest {

    @Test
    void 어드민_키와_회원번호로_연결_끊기를_요청한다() {
        KakaoUserApiProperties properties = new KakaoUserApiProperties(
                URI.create("https://kapi.kakao.test"), Duration.ofSeconds(2), Duration.ofSeconds(5), "admin-key");
        // 운영과 같은 메시지 컨버터 구성에서 form 본문이 인코딩되는지 확인한다.
        RestClient.Builder builder = new KakaoUserApiClientConfig()
                .kakaoUserApiRestClient(properties, JsonMapper.builder().build())
                .mutate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://kapi.kakao.test/v1/user/unlink"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "KakaoAK admin-key"))
                .andExpect(content().string("target_id_type=user_id&target_id=12345"))
                .andRespond(withSuccess());

        new KakaoUserApiClientImpl(builder.build(), properties).unlink("12345");

        server.verify();
    }
}
