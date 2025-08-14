package com.example.myhttpbin.dto;

public class Base64Response {
    private String decoded;

    public Base64Response() {}

    public Base64Response(String decoded) {
        this.decoded = decoded;
    }

    public String getDecoded() {
        return decoded;
    }

    public void setDecoded(String decoded) {
        this.decoded = decoded;
    }
}
