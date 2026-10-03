package com.example.plimap.global.external.oidc;

import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

// 구글/애플 JWKS 조회 전용 RestClient. 두 provider가 URL만 다를 뿐 같은 방식(GET, JSON 문자열
// 응답)으로 호출되므로 baseUrl 없이 하나의 클라이언트를 공유한다.
@Configuration
@EnableConfigurationProperties(JwksRestClientProperties.class)
public class JwksRestClientConfig {

    @Bean
    public RestClient jwksRestClient(JwksRestClientProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());

        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }
}
