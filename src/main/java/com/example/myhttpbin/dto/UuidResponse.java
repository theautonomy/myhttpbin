package com.example.myhttpbin.dto;

public class UuidResponse {
    private String uuid;

    public UuidResponse() {}

    public UuidResponse(String uuid) {
        this.uuid = uuid;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }
}
