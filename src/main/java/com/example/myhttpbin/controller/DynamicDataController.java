package com.example.myhttpbin.controller;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.example.myhttpbin.dto.Base64Response;
import com.example.myhttpbin.dto.DelayResponse;
import com.example.myhttpbin.dto.ErrorResponse;
import com.example.myhttpbin.dto.UuidResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/")
public class DynamicDataController {

    private final SecureRandom random = new SecureRandom();

    @GetMapping("/uuid")
    public ResponseEntity<UuidResponse> generateUuid() {
        String uuid = UUID.randomUUID().toString();
        return ResponseEntity.ok(new UuidResponse(uuid));
    }

    @GetMapping("/base64/{value}")
    public ResponseEntity<?> decodeBase64(@PathVariable String value) {
        try {
            byte[] decodedBytes = Base64.getDecoder().decode(value);
            String decoded = new String(decodedBytes);
            return ResponseEntity.ok(new Base64Response(decoded));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(
                            new ErrorResponse(
                                    "Invalid Base64", "The provided value is not valid Base64"));
        }
    }

    @GetMapping("/delay/{seconds}")
    public ResponseEntity<?> delayResponse(@PathVariable int seconds, HttpServletRequest request) {
        if (seconds > 10) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("Delay too long", "Maximum delay is 10 seconds"));
        }

        try {
            TimeUnit.SECONDS.sleep(seconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Interrupted", "Request was interrupted"));
        }

        Map<String, Object> args = new HashMap<>();
        request.getParameterMap()
                .forEach(
                        (key, values) -> {
                            if (values.length == 1) {
                                args.put(key, values[0]);
                            } else {
                                args.put(key, Arrays.asList(values));
                            }
                        });

        Map<String, String> headers = new HashMap<>();
        Collections.list(request.getHeaderNames())
                .forEach(headerName -> headers.put(headerName, request.getHeader(headerName)));

        String origin = request.getRemoteAddr();
        String url = request.getRequestURL().toString();
        if (request.getQueryString() != null) {
            url += "?" + request.getQueryString();
        }

        DelayResponse response = new DelayResponse(args, headers, origin, url);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/bytes/{n}")
    public ResponseEntity<?> generateBytes(@PathVariable int n) {
        if (n <= 0) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("Invalid size", "Number of bytes must be positive"));
        }

        if (n > 100 * 1024 * 1024) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("Size too large", "Maximum size is 100KB"));
        }

        byte[] randomBytes = new byte[n];
        random.nextBytes(randomBytes);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(randomBytes);
    }

    @GetMapping("/chars/{n}")
    public ResponseEntity<?> generateChars(@PathVariable int n) {
        if (n <= 0) {
            return ResponseEntity.badRequest()
                    .body(
                            new ErrorResponse(
                                    "Invalid size", "Number of characters must be positive"));
        }

        if (n > 10 * 1024 * 1024) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("Size too large", "Maximum size is 100KB"));
        }

        String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < n; i++) {
            int randomIndex = random.nextInt(chars.length());
            result.append(chars.charAt(randomIndex));
        }

        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(result.toString());
    }
}
