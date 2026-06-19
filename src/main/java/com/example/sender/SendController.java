package com.example.sender;

import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

@RestController
public class SendController {

    private final Map<String, FileForwarder> forwarders;
    private final String defaultForwarder;
    private final String defaultReceiverUrl;

    public SendController(List<FileForwarder> forwarderList,
                          @Value("${sender.forwarder:httpclient}") String defaultForwarder,
                          @Value("${sender.receiver-url:http://localhost:8081/upload}") String defaultReceiverUrl) {
        this.forwarders = forwarderList.stream()
                .collect(Collectors.toMap(FileForwarder::strategy, f -> f));
        this.defaultForwarder = defaultForwarder;
        this.defaultReceiverUrl = defaultReceiverUrl;
    }

    @PostMapping(value = "/send", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Object> send(@RequestParam(value = "receiverUrl", required = false) String receiverUrl,
                                       @RequestParam(value = "forwarder", required = false) String forwarder,
                                       MultipartHttpServletRequest request) {
        //MultipartHttpServletRequest in place of multiPartFile because its part of MultipartHttpServletRequest
        MultipartFile file = request.getFile("file");
        if (file == null) {
            file = request.getFileMap().values().stream().findFirst().orElse(null);
        }
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(error(
                    "No file part found. Send multipart/form-data with a file part, e.g. curl -F \"file=@/path\". "
                            + "Parts received: " + request.getFileMap().keySet()));
        }
        String strategy = (forwarder != null && !forwarder.isBlank()) ? forwarder : defaultForwarder;
        FileForwarder ff = forwarders.get(strategy);
        if (ff == null) {
            return ResponseEntity.badRequest()
                    .body(error("Unknown forwarder '" + strategy + "'. Available: " + forwarders.keySet()));
        }

        URI receiver = URI.create(receiverUrl != null && !receiverUrl.isBlank() ? receiverUrl : defaultReceiverUrl);
        String name = (file.getOriginalFilename() != null && !file.getOriginalFilename().isBlank())
                ? file.getOriginalFilename() : "upload.bin";

        // try-with-resources guarantees the part stream is closed in every path.
        try (InputStream in = file.getInputStream()) {
            SendResult result = ff.forward(in, name, receiver);
            HttpStatus status = "OK".equals(result.getStatus()) ? HttpStatus.OK : HttpStatus.BAD_GATEWAY;
            return ResponseEntity.status(status).body(result);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error("Transfer interrupted"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(error("Failed to proxy file: " + e.getMessage()));
        }
    }

    private static java.util.Map<String, String> error(String message) {
        return java.util.Map.of("status", "ERROR", "message", message);
    }
}

