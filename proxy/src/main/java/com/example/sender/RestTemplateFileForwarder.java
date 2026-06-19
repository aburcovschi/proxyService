package com.example.sender;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.StreamingHttpOutputMessage;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestTemplate;


@Component
public class RestTemplateFileForwarder implements FileForwarder {

    private static final int CHUNK_SIZE = 64 * 1024; // 64 KB

    private final RestTemplate restTemplate;

    public RestTemplateFileForwarder() {
        //TODO: replace factory with HttpComponentsClientHttpRequestFactory
        //TODO : set factory.setBufferRequestBody(false) ITS DEPRECATED SINCE BOOT 3.1
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setChunkSize(CHUNK_SIZE);
        factory.setReadTimeout(0);
        factory.setConnectTimeout(30_000); // 30 seconds to establish the connection
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public String strategy() {
        return "resttemplate";
    }

    @Override
    public SendResult forward(InputStream source, String fileName, URI receiverUri) {
        ResponseEntity<String> response = restTemplate.execute(
                receiverUri,
                HttpMethod.POST,
                request -> {
                    request.getHeaders().setContentType(MediaType.APPLICATION_OCTET_STREAM);
                    request.getHeaders().add(FileForwarder.FILE_NAME_HEADER, fileName);
                    setStreamingBody(request, source);
                },
                resp -> ResponseEntity.status(resp.getStatusCode())
                        .body(StreamUtils.copyToString(resp.getBody(), StandardCharsets.UTF_8)));

        boolean ok = response != null && response.getStatusCode().is2xxSuccessful();
        int httpStatus = response != null ? response.getStatusCode().value() : 0;
        String respBody = response != null ? response.getBody() : null;
        return new SendResult(ok ? "OK" : "FAILED", httpStatus, fileName,respBody);
    }

    private static void setStreamingBody(ClientHttpRequest request, InputStream source) throws java.io.IOException {
//        var abc = request.getBody();
//        source.transferTo(abc);

//        if (request instanceof StreamingHttpOutputMessage streaming) {
//            // writeTo is invoked by Spring with the live connection output stream.
//            streaming.setBody(out -> StreamUtils.copy(source, out));
//        } else {
//            StreamUtils.copy(source, request.getBody());
//        }

        // Do NOT call request.getBody(): on Spring 7's AbstractStreamingClientHttpRequest that
        // buffers the WHOLE body into a FastByteArrayOutputStream (heap) and OOMs on big files.
        // Use the streaming setBody callback so bytes go straight to the chunked connection stream.
        if (request instanceof StreamingHttpOutputMessage streaming) {
            streaming.setBody(out -> StreamUtils.copy(source, out));
        } else {
            // Fallback (shouldn't happen with SimpleClientHttpRequestFactory).
            StreamUtils.copy(source, request.getBody());
        }
    }
}

/*
    @PostMapping("/proxy")
    public ResponseEntity<String> proxy(HttpServletRequest request) {
        return restTemplate.execute(
                "http://target-service/upload",
                HttpMethod.POST,
                clientRequest -> {
                    clientRequest.getHeaders()
                            .setContentType(MediaType.APPLICATION_OCTET_STREAM);

                    try (InputStream in = request.getInputStream();
                         OutputStream out = clientRequest.getBody()) {

                        in.transferTo(out);
                    }
                },
                clientHttpResponse -> ResponseEntity
                        .status(clientHttpResponse.getStatusCode())
                        .body(new String(
                                clientHttpResponse.getBody().readAllBytes(),
                                StandardCharsets.UTF_8))
        );
    }
}
 */
