package com.example.receiver;

/**
 * Summary of a completed upload. {@code record} is fine here since the receiver
 * targets Java 17+.
 */
public record UploadResult(String status, long bytesReceived, String storedAs, long elapsedMs) {
}

