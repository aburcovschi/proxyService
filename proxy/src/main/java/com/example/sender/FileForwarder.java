package com.example.sender;

import java.io.InputStream;
import java.net.URI;

public interface FileForwarder {

    String FILE_NAME_HEADER = "X-File-Name";

    String strategy();

    /**
     * Streams {@code source} to {@code receiverUri}, forwarding the file name in the
     * {@code X-File-Name} header and reporting the byte count.
     */
    SendResult forward(InputStream source, String fileName, URI receiverUri) throws Exception;

}

