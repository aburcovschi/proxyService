package com.example.sender;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;


@Component
public class HttpExchangeFileForwarder implements FileForwarder {

    private final ReceiverHttpClient client;

    public HttpExchangeFileForwarder() {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofMinutes(60));
        RestClient restClient = RestClient.builder().requestFactory(factory).build();
        HttpServiceProxyFactory proxyFactory = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build();
        this.client = proxyFactory.createClient(ReceiverHttpClient.class);
    }

    @Override
    public String strategy() {
        return "httpexchange";
    }

    @Override
    public SendResult forward(InputStream source, String fileName, URI receiverUri) {
        ResponseEntity<String> response = client.upload(receiverUri, fileName, new InputStreamResource(source));
        return new SendResult(response.getStatusCode().is2xxSuccessful() ? "OK" : "FAILED", response.getStatusCode().value(),
                fileName, response.getBody());
    }
}

