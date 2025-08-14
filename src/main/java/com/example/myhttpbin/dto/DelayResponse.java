package com.example.myhttpbin.dto;

import java.util.Map;

public class DelayResponse {
    private Map<String, Object> args;
    private Map<String, String> headers;
    private String origin;
    private String url;

    public DelayResponse() {}

    public DelayResponse(
            Map<String, Object> args, Map<String, String> headers, String origin, String url) {
        this.args = args;
        this.headers = headers;
        this.origin = origin;
        this.url = url;
    }

    public Map<String, Object> getArgs() {
        return args;
    }

    public void setArgs(Map<String, Object> args) {
        this.args = args;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
