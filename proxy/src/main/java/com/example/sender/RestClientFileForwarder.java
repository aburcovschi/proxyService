package com.example.sender;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class RestClientFileForwarder implements FileForwarder {

    private final RestClient restClient;

    public RestClientFileForwarder() {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofMinutes(60));
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    @Override
    public String strategy() {
        return "restclient";
    }

    @Override
    public SendResult forward(InputStream source, String fileName, URI receiverUri) {
        ResponseEntity<String> response = restClient.post()
                .uri(receiverUri)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(FILE_NAME_HEADER, fileName)
                .body(new InputStreamResource(source))
                .retrieve()
                .toEntity(String.class);
        return new SendResult(response.getStatusCode().is2xxSuccessful() ? "OK" : "FAILED", response.getStatusCode().value(),
                fileName, response.getBody());
    }
}

