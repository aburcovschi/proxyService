package com.example.sender;

public class SendResult {

    private final String status;
    private final int httpStatus;
    private final String fileName;
    private final String receiverResponse;

    public SendResult(String status, int httpStatus, String fileName, String receiverResponse) {
        this.status = status;
        this.httpStatus = httpStatus;
        this.fileName = fileName;
        this.receiverResponse = receiverResponse;
    }

    public String getStatus() {
        return status;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getFileName() {
        return fileName;
    }

    public String getReceiverResponse() {
        return receiverResponse;
    }
}

