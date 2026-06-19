package com.example.sender;

import java.net.URI;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.PostExchange;

/**
 * Declarative HTTP Interface client for the receiver's upload endpoint.
 *
 * <p>The {@link URI} parameter dynamically sets the full request URL (overriding any
 * base URL), so the receiver endpoint can be chosen per request. The {@link Resource}
 * body is streamed by {@code HttpExchangeFileForwarder}.
 */
public interface ReceiverHttpClient {

    @PostExchange(contentType = "application/octet-stream")
    ResponseEntity<String> upload(URI uri,
                                  @RequestHeader(FileForwarder.FILE_NAME_HEADER) String fileName,
                                  @RequestBody Resource body);
}

