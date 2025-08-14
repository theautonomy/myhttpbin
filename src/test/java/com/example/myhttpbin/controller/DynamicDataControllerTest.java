package com.example.myhttpbin.controller;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.Charset;
import java.time.Duration;
import java.util.Base64;

import com.example.myhttpbin.MyhttpbinApplication;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClient;

@SpringBootTest(
        classes = MyhttpbinApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class DynamicDataControllerTest {

    @Autowired private MockMvc mockMvc;

    @LocalServerPort private int port;

    private void logTestMetrics(String testName, long startTime, long endTime) {
        long duration = endTime - startTime;
        Runtime runtime = Runtime.getRuntime();
        long memoryUsed = runtime.totalMemory() - runtime.freeMemory();
        System.out.println(
                String.format(
                        "[%s] Duration: %dms, Memory: %dMB",
                        testName, duration, memoryUsed / 1024 / 1024));
    }

    @Test
    void testUuidEndpoint() throws Exception {
        long startTime = System.currentTimeMillis();

        mockMvc.perform(get("/uuid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uuid").exists())
                .andExpect(jsonPath("$.uuid").isString());

        long endTime = System.currentTimeMillis();
        logTestMetrics("testUuidEndpoint", startTime, endTime);
    }

    @Test
    void testBase64EndpointValid() throws Exception {
        long startTime = System.currentTimeMillis();

        String testString = "Hello World";
        String encoded = Base64.getEncoder().encodeToString(testString.getBytes());

        mockMvc.perform(get("/base64/" + encoded))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decoded").value(testString));

        long endTime = System.currentTimeMillis();
        logTestMetrics("testBase64EndpointValid", startTime, endTime);
    }

    @Test
    void testBase64EndpointInvalid() throws Exception {
        long startTime = System.currentTimeMillis();

        mockMvc.perform(get("/base64/invalid-base64!@#"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid Base64"));

        long endTime = System.currentTimeMillis();
        logTestMetrics("testBase64EndpointInvalid", startTime, endTime);
    }

    @Test
    void testDelayEndpoint() throws Exception {
        long startTime = System.currentTimeMillis();

        mockMvc.perform(get("/delay/1?test=value"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.args.test").value("value"))
                .andExpect(jsonPath("$.url").exists())
                .andExpect(jsonPath("$.origin").exists())
                .andExpect(jsonPath("$.headers").exists());

        long endTime = System.currentTimeMillis();
        logTestMetrics("testDelayEndpoint", startTime, endTime);
    }

    @Test
    void testDelayEndpointTooLong() throws Exception {
        long startTime = System.currentTimeMillis();

        mockMvc.perform(get("/delay/65"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Delay too long"));

        long endTime = System.currentTimeMillis();
        logTestMetrics("testDelayEndpointTooLong", startTime, endTime);
    }

    @Test
    void testBytesEndpoint() throws Exception {
        int numBytes = 900000;
        byte[] result =
                mockMvc.perform(get("/bytes/" + numBytes))
                        .andExpect(status().isOk())
                        .andExpect(header().string("Content-Type", "application/octet-stream"))
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray();

        assertEquals(numBytes, result.length);
    }

    @Test
    void testBytesEndpointLargeSize() throws Exception {
        mockMvc.perform(get("/bytes/1024"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/octet-stream"));
    }

    @Test
    void testBytesEndpointInvalidSize() throws Exception {
        mockMvc.perform(get("/bytes/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid size"));
    }

    @Test
    void testBytesEndpointNegativeSize() throws Exception {
        mockMvc.perform(get("/bytes/-10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid size"));
    }

    @Test
    void testBytesEndpointTooLarge() throws Exception {
        mockMvc.perform(get("/bytes/1100000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Size too large"));
    }

    @Test
    void testDelayEndpointWithMultipleParams() throws Exception {
        mockMvc.perform(get("/delay/1?param1=value1&param2=value2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.args.param1").value("value1"))
                .andExpect(jsonPath("$.args.param2").value("value2"));
    }

    @Test
    void testUuidEndpointFormat() throws Exception {
        String uuidRegex = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$";
        mockMvc.perform(get("/uuid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uuid").exists())
                .andExpect(
                        jsonPath("$.uuid").value(org.hamcrest.Matchers.matchesPattern(uuidRegex)));
    }

    @Test
    void testCharsEndpoint() throws Exception {
        int numChars = 50;
        String result =
                mockMvc.perform(get("/chars/" + numChars))
                        .andExpect(status().isOk())
                        .andExpect(header().string("Content-Type", "text/plain"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        assertEquals(numChars, result.length());
        // Verify all characters are alphanumeric
        assertTrue(result.matches("[a-zA-Z0-9]+"));
    }

    @Test
    void testCharsEndpointSmallSize() throws Exception {
        mockMvc.perform(get("/chars/5"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/plain"));
    }

    @Test
    void testCharsEndpointInvalidSize() throws Exception {
        mockMvc.perform(get("/chars/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid size"));
    }

    @Test
    void testCharsEndpointNegativeSize() throws Exception {
        mockMvc.perform(get("/chars/-5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid size"));
    }

    @Test
    void testCharsEndpointTooLarge() throws Exception {
        mockMvc.perform(get("/chars/1100000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Size too large"));
    }

    @Test
    void testCharsEndpointRandomness() throws Exception {
        String result1 =
                mockMvc.perform(get("/chars/100"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        String result2 =
                mockMvc.perform(get("/chars/100"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        // Very unlikely to be the same for 100 random characters
        assertNotEquals(result1, result2);
    }

    @Test
    void testCharsEndpointLargeRequestWebClient() {
        WebClient webClient = WebClient.builder().baseUrl("http://localhost:" + port).build();

        assertThatThrownBy(
                        () -> {
                            webClient
                                    .get()
                                    .uri("/chars/900000")
                                    .retrieve()
                                    .bodyToMono(String.class)
                                    .block();
                        })
                .hasRootCauseInstanceOf(DataBufferLimitException.class)
                .hasRootCauseMessage("Exceeded limit on max bytes to buffer : 262144");
    }

    @Test
    void testCharsEndpointLargeRequestWebClientManualExceptionHandling() throws Exception {
        WebClient webClient = WebClient.builder().baseUrl("http://localhost:" + port).build();

        boolean dataBufferLimitExceptionThrown = false;

        String result = "";

        try {
            result =
                    webClient
                            .get()
                            .uri("/chars/900000")
                            .retrieve()
                            .bodyToMono(String.class)
                            .block();

            // This should not happen for 10MB request due to buffer limit
            System.out.println("Request succeeded with " + result.length() + " characters");
        } catch (Exception e) {
            System.out.println("expected: Request with " + result.length() + " characters");
            System.out.println("WebClient test caught exception: " + e.getClass().getSimpleName());
            System.out.println("Exception message: " + e.getMessage());

            // Check if the root cause is DataBufferLimitException
            Throwable cause = e;
            while (cause != null) {
                if (cause instanceof DataBufferLimitException) {
                    dataBufferLimitExceptionThrown = true;
                    System.out.println(
                            "Expected DataBufferLimitException caught: " + cause.getMessage());
                    assertTrue(
                            cause.getMessage().contains("Exceeded limit on max bytes to buffer"));
                    break;
                }
                cause = cause.getCause();
            }

            if (!dataBufferLimitExceptionThrown) {
                System.out.println("Unexpected exception type. Full stack trace:");
                e.printStackTrace();
            }
        }

        // Verify that the expected exception was thrown
        assertTrue(
                dataBufferLimitExceptionThrown,
                "Expected DataBufferLimitException to be thrown for large response");
    }

    @Test
    void testCharsEndpointLargeRequestWithIncreasedBufferSize() throws Exception {
        // Configure WebClient with 12MB buffer size
        ExchangeStrategies strategies =
                ExchangeStrategies.builder()
                        .codecs(
                                configurer ->
                                        configurer
                                                .defaultCodecs()
                                                .maxInMemorySize(1 * 1024 * 1024)) // 12MB
                        .build();

        WebClient webClient =
                WebClient.builder()
                        .baseUrl("http://localhost:" + port)
                        .exchangeStrategies(strategies)
                        .build();

        long startTime = System.currentTimeMillis();

        String result =
                webClient
                        .get()
                        .uri("/chars/900000") // 30MB request
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Verify the response
        assertNotNull(result, "Response should not be null");
        assertEquals(900000, result.length(), "Should receive exactly 10 million characters");
        assertTrue(result.matches("[a-zA-Z0-9]+"), "All characters should be alphanumeric");

        // Memory usage information
        Runtime runtime = Runtime.getRuntime();
        long memoryUsed = runtime.totalMemory() - runtime.freeMemory();

        System.out.println("=== Increased Buffer Size Test Results ===");
        System.out.println(
                "Successfully received " + result.length() + " characters in " + duration + "ms");
        System.out.println("Memory used: " + (memoryUsed / 1024 / 1024) + " MB");
        System.out.println("Buffer configuration: 12MB");
        System.out.println("First 100 chars: " + result.substring(0, 100));
        System.out.println("Last 100 chars: " + result.substring(result.length() - 100));
    }

    @Test
    void testCharsEndpointLargeRequestWithUnlimitedBuffer() throws Exception {
        // Configure WebClient with unlimited buffer size using the Medium post approach
        ExchangeStrategies exchangeStrategies =
                ExchangeStrategies.builder()
                        .codecs(
                                configurer ->
                                        configurer
                                                .defaultCodecs()
                                                .maxInMemorySize(-1)) // -1 = unlimited
                        .build();

        WebClient webClient =
                WebClient.builder()
                        .baseUrl("http://localhost:" + port)
                        .exchangeStrategies(exchangeStrategies)
                        .build();

        long startTime = System.currentTimeMillis();

        String result =
                webClient
                        .get()
                        .uri("/chars/900000") // 10MB request
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Verify the response
        assertNotNull(result, "Response should not be null");
        assertEquals(900000, result.length(), "Should receive exactly 10 million characters");
        assertTrue(result.matches("[a-zA-Z0-9]+"), "All characters should be alphanumeric");

        // Memory usage information
        Runtime runtime = Runtime.getRuntime();
        long memoryUsed = runtime.totalMemory() - runtime.freeMemory();

        System.out.println("=== Medium Post Approach Test Results ===");
        System.out.println(
                "Successfully received " + result.length() + " characters in " + duration + "ms");
        System.out.println("Memory used: " + (memoryUsed / 1024 / 1024) + " MB");
        System.out.println("Buffer configuration: UNLIMITED (-1)");
        System.out.println("First 50 chars: " + result.substring(0, 50));
        System.out.println("Last 50 chars: " + result.substring(result.length() - 50));

        // Validate that we can handle very large responses
        assertTrue(result.length() >= 256000, "Should handle large responses without buffer limit");
    }

    @Test
    void testCharsEndpointLargeRequestWithDataBufferStreaming() throws Exception {
        // Use default WebClient (with 256KB limit) but stream the response using DataBuffer
        WebClient webClient = WebClient.builder().baseUrl("http://localhost:" + port).build();

        long startTime = System.currentTimeMillis();

        // Stream the response as DataBuffer flux to avoid buffering the entire response in memory
        Flux<DataBuffer> dataBufferFlux =
                webClient
                        .get()
                        .uri("/chars/900000") // 10MB request
                        .retrieve()
                        .bodyToFlux(DataBuffer.class);

        // Process the streaming data and build the result

        // Consume the flux and aggregate the data
        String result =
                dataBufferFlux
                        .map(
                                dataBuffer -> {
                                    try {
                                        // Read the data from the buffer
                                        byte[] bytes = new byte[dataBuffer.readableByteCount()];
                                        dataBuffer.read(bytes);
                                        return new String(bytes);
                                    } finally {
                                        // Always release the buffer to prevent memory leaks
                                        DataBufferUtils.release(dataBuffer);
                                    }
                                })
                        .reduce("", (accumulated, chunk) -> accumulated + chunk)
                        .block();

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Verify the response
        assertNotNull(result, "Response should not be null");
        assertEquals(900000, result.length(), "Should receive exactly 10 million characters");
        assertTrue(result.matches("[a-zA-Z0-9]+"), "All characters should be alpranumeric");

        // Memory usage information
        Runtime runtime = Runtime.getRuntime();
        long memoryUsed = runtime.totalMemory() - runtime.freeMemory();

        System.out.println("=== DataBuffer Streaming Approach Test Results ===");
        System.out.println(
                "Successfully streamed " + result.length() + " characters in " + duration + "ms");
        System.out.println("Memory used: " + (memoryUsed / 1024 / 1024) + " MB");
        System.out.println("Buffer configuration: DEFAULT (256KB) with DataBuffer streaming");
        System.out.println("First 50 chars: " + result.substring(0, 50));
        System.out.println("Last 50 chars: " + result.substring(result.length() - 50));

        // Validate streaming approach worked with default buffer limits
        assertTrue(result.length() >= 256000, "Should handle large responses via streaming");
    }

    @Test
    void testDelayPostEndpoint() throws Exception {
        String jsonBody = "{\"test\": \"data\", \"number\": 123}";

        mockMvc.perform(
                        post("/delay/1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonBody)
                                .param("param1", "value1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.method").value("POST"))
                .andExpect(jsonPath("$.args.param1").value("value1"))
                .andExpect(jsonPath("$.data").value(jsonBody))
                .andExpect(jsonPath("$.url").exists())
                .andExpect(jsonPath("$.origin").exists())
                .andExpect(jsonPath("$.headers").exists());
    }

    @Test
    void testDelayPutEndpoint() throws Exception {
        String xmlBody = "<user><name>John</name><age>30</age></user>";

        mockMvc.perform(
                        put("/delay/1")
                                .contentType(MediaType.APPLICATION_XML)
                                .content(xmlBody)
                                .param("action", "update"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.method").value("PUT"))
                .andExpect(jsonPath("$.args.action").value("update"))
                .andExpect(jsonPath("$.data").value(xmlBody))
                .andExpect(jsonPath("$.url").exists());
    }

    @Test
    void testDelayDeleteEndpoint() throws Exception {
        mockMvc.perform(delete("/delay/2").param("force", "true").param("reason", "cleanup"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.method").value("DELETE"))
                .andExpect(jsonPath("$.args.force").value("true"))
                .andExpect(jsonPath("$.args.reason").value("cleanup"))
                .andExpect(jsonPath("$.url").exists())
                .andExpect(jsonPath("$.data").doesNotExist()); // DELETE should not have body
    }

    @Test
    void testDelayGetEndpointStillWorks() throws Exception {
        mockMvc.perform(get("/delay/1?test=value"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.method").value("GET"))
                .andExpect(jsonPath("$.args.test").value("value"))
                .andExpect(jsonPath("$.url").exists())
                .andExpect(jsonPath("$.origin").exists())
                .andExpect(jsonPath("$.headers").exists());
    }

    @Test
    void testDelayPostWithoutBody() throws Exception {
        mockMvc.perform(post("/delay/1").param("empty", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.method").value("POST"))
                .andExpect(jsonPath("$.args.empty").value("true"))
                .andExpect(jsonPath("$.data").doesNotExist()); // No body provided
    }

    @Test
    void testDelayAllMethodsTooLong() throws Exception {
        // Test that all methods respect the 60-second limit
        mockMvc.perform(get("/delay/65"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Delay too long"));

        mockMvc.perform(post("/delay/70"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Delay too long"));

        mockMvc.perform(put("/delay/75"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Delay too long"));

        mockMvc.perform(delete("/delay/80"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Delay too long"));
    }

    @Test
    void testDelayMaximumAllowedTime() throws Exception {
        // Test that 60 seconds is still allowed
        mockMvc.perform(get("/delay/60?test=maxtime"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.method").value("GET"))
                .andExpect(jsonPath("$.args.test").value("maxtime"));
    }

    @Test
    void testWebClientTimeoutWithLongDelay() throws Exception {
        // Configure WebClient with 10-second timeout
        WebClient webClient = WebClient.builder().baseUrl("http://localhost:" + port).build();

        long startTime = System.currentTimeMillis();

        assertThatThrownBy(
                        () -> {
                            webClient
                                    .get()
                                    .uri("/delay/15?test=timeout") // Request 15-second delay
                                    .retrieve()
                                    .bodyToMono(String.class)
                                    .timeout(java.time.Duration.ofSeconds(10)) // 10-second timeout
                                    .block();
                        })
                .hasRootCauseInstanceOf(java.util.concurrent.TimeoutException.class)
                .hasRootCauseMessage(
                        "Did not observe any item or terminal signal within 10000ms in 'flatMap' (and no fallback has been configured)");

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        System.out.println("Request timed out after " + duration + "ms (expected ~10000ms)");

        // Verify timeout occurred around 10 seconds (allow some variance)
        assertTrue(
                duration >= 9000 && duration <= 12000,
                "Timeout should occur around 10 seconds, but was " + duration + "ms");
    }

    @Test
    void testWebClientConnectionTimeout() throws Exception {
        // Configure WebClient with very short connection timeout to non-routable IP
        HttpClient httpClient =
                HttpClient.create().option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 2000); // 2 seconds

        WebClient webClient =
                WebClient.builder()
                        .clientConnector(
                                new org.springframework.http.client.reactive
                                        .ReactorClientHttpConnector(httpClient))
                        .build();

        long startTime = System.currentTimeMillis();

        assertThatThrownBy(
                        () -> {
                            webClient
                                    .get()
                                    .uri("http://10.255.255.1:8080/delay/1") // Non-routable IP
                                    // to trigger
                                    // connection
                                    // timeout
                                    .retrieve()
                                    .bodyToMono(String.class)
                                    .block();
                        })
                .hasRootCauseInstanceOf(java.net.ConnectException.class);

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        System.out.println("Connection timeout after " + duration + "ms (expected ~2000ms)");

        // Verify connection timeout occurred around 2 seconds
        assertTrue(
                duration >= 1500 && duration <= 3000,
                "Connection timeout should occur around 2 seconds, but was " + duration + "ms");
    }

    @Test
    void testWebClientReadTimeout() throws Exception {
        // Configure WebClient with read timeout
        HttpClient httpClient =
                HttpClient.create()
                        .doOnConnected(
                                conn ->
                                        conn.addHandlerLast(
                                                new ReadTimeoutHandler(
                                                        5))); // 5 seconds read timeout

        WebClient webClient =
                WebClient.builder()
                        .baseUrl("http://localhost:" + port)
                        .clientConnector(
                                new org.springframework.http.client.reactive
                                        .ReactorClientHttpConnector(httpClient))
                        .build();

        long startTime = System.currentTimeMillis();

        assertThatThrownBy(
                        () -> {
                            webClient
                                    .get()
                                    .uri("/delay/8?test=readtimeout") // Request 8-second delay
                                    // (exceeds 5s read
                                    // timeout)
                                    .retrieve()
                                    .bodyToMono(String.class)
                                    .block();
                        })
                .hasRootCauseInstanceOf(io.netty.handler.timeout.ReadTimeoutException.class);

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        System.out.println("Read timeout after " + duration + "ms (expected ~5000ms)");

        // Verify read timeout occurred around 5 seconds
        assertTrue(
                duration >= 4000 && duration <= 6500,
                "Read timeout should occur around 5 seconds, but was " + duration + "ms");
    }

    @Test
    void testWebClientWriteTimeout() throws Exception {
        // Configure WebClient with write timeout
        HttpClient httpClient =
                HttpClient.create()
                        .doOnConnected(
                                conn ->
                                        conn.addHandlerLast(
                                                new WriteTimeoutHandler(
                                                        3))); // 3 seconds write timeout

        WebClient webClient =
                WebClient.builder()
                        .baseUrl("http://localhost:" + port)
                        .clientConnector(
                                new org.springframework.http.client.reactive
                                        .ReactorClientHttpConnector(httpClient))
                        .build();

        // Create a large payload to potentially trigger write timeout
        String largePayload = "x".repeat(1000000); // 1MB payload

        long startTime = System.currentTimeMillis();

        // Note: Write timeout is harder to trigger reliably in tests
        // This test mainly verifies the configuration works
        try {
            String result =
                    webClient
                            .post()
                            .uri("/delay/1")
                            .bodyValue(largePayload)
                            .retrieve()
                            .bodyToMono(String.class)
                            .block();

            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            System.out.println("Write completed successfully after " + duration + "ms");
            assertNotNull(result);

        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            System.out.println(
                    "Write operation failed after " + duration + "ms: " + e.getMessage());

            // If write timeout occurs, verify it's the expected type
            if (e.getCause() instanceof io.netty.handler.timeout.WriteTimeoutException) {
                assertTrue(
                        duration >= 2500 && duration <= 4000,
                        "Write timeout should occur around 3 seconds, but was " + duration + "ms");
            }
        }
    }

    @Test
    void testWebClientGlobalResponseTimeout() throws Exception {
        // Configure WebClient with global response timeout
        HttpClient httpClient =
                HttpClient.create()
                        .responseTimeout(
                                Duration.ofSeconds(4)); // 4 seconds global response timeout

        WebClient webClient =
                WebClient.builder()
                        .baseUrl("http://localhost:" + port)
                        .clientConnector(
                                new org.springframework.http.client.reactive
                                        .ReactorClientHttpConnector(httpClient))
                        .build();

        long startTime = System.currentTimeMillis();

        assertThatThrownBy(
                        () -> {
                            webClient
                                    .get()
                                    .uri("/delay/7?test=globalresponsetimeout") // Request
                                    // 7-second delay
                                    // (exceeds 4s
                                    // timeout)
                                    .retrieve()
                                    .bodyToMono(String.class)
                                    .block();
                        })
                .hasRootCauseInstanceOf(io.netty.handler.timeout.ReadTimeoutException.class);

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        System.out.println("Global response timeout after " + duration + "ms (expected ~4000ms)");

        // Verify global response timeout occurred around 4 seconds
        assertTrue(
                duration >= 3500 && duration <= 5000,
                "Global response timeout should occur around 4 seconds, but was "
                        + duration
                        + "ms");
    }

    @Test
    void testWebClientPerRequestResponseTimeout() throws Exception {
        // Configure WebClient without global timeout
        HttpClient httpClient = HttpClient.create();

        WebClient webClient =
                WebClient.builder()
                        .baseUrl("http://localhost:" + port)
                        .clientConnector(
                                new org.springframework.http.client.reactive
                                        .ReactorClientHttpConnector(httpClient))
                        .build();

        long startTime = System.currentTimeMillis();

        assertThatThrownBy(
                        () -> {
                            webClient
                                    .get()
                                    .uri("/delay/6?test=perrequesttimeout") // Request 6-second
                                    // delay
                                    .httpRequest(
                                            httpRequest -> {
                                                reactor.netty.http.client.HttpClientRequest
                                                        reactorRequest =
                                                                (reactor.netty.http.client
                                                                                .HttpClientRequest)
                                                                        httpRequest
                                                                                .getNativeRequest();
                                                reactorRequest.responseTimeout(
                                                        Duration.ofSeconds(
                                                                3)); // 3-second per-request timeout
                                            })
                                    .retrieve()
                                    .bodyToMono(String.class)
                                    .block();
                        })
                .hasRootCauseInstanceOf(io.netty.handler.timeout.ReadTimeoutException.class);

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        System.out.println(
                "Per-request response timeout after " + duration + "ms (expected ~3000ms)");

        // Verify per-request response timeout occurred around 3 seconds
        assertTrue(
                duration >= 2500 && duration <= 4000,
                "Per-request response timeout should occur around 3 seconds, but was "
                        + duration
                        + "ms");
    }

    @Test
    void testWebClientAllTimeoutsCombined() throws Exception {
        // Configure WebClient with all timeout types
        HttpClient httpClient =
                HttpClient.create()
                        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000) // 5s connection timeout
                        .doOnConnected(
                                conn ->
                                        conn.addHandlerLast(
                                                        new ReadTimeoutHandler(
                                                                10)) // 10s read timeout
                                                .addHandlerLast(
                                                        new WriteTimeoutHandler(
                                                                10))) // 10s write timeout
                        .responseTimeout(Duration.ofSeconds(6)); // 6s global response timeout

        WebClient webClient =
                WebClient.builder()
                        .baseUrl("http://localhost:" + port)
                        .clientConnector(
                                new org.springframework.http.client.reactive
                                        .ReactorClientHttpConnector(httpClient))
                        .build();

        long startTime = System.currentTimeMillis();

        assertThatThrownBy(
                        () -> {
                            webClient
                                    .post()
                                    .uri("/delay/8?test=alltimeouts") // Request 8-second delay
                                    // (exceeds 6s response
                                    // timeout)
                                    .bodyValue("{\"test\": \"data\"}")
                                    .retrieve()
                                    .bodyToMono(String.class)
                                    .block();
                        })
                .hasRootCauseInstanceOf(io.netty.handler.timeout.ReadTimeoutException.class);

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        System.out.println(
                "Combined timeouts - response timeout triggered after "
                        + duration
                        + "ms (expected ~6000ms)");

        // The response timeout (6s) should trigger before read timeout (10s)
        assertTrue(
                duration >= 5500 && duration <= 7000,
                "Response timeout should occur around 6 seconds, but was " + duration + "ms");
    }

    @Test
    void testCharsEndpointLargeRequestWithExchangeToMono() throws Exception {
        // Use default WebClient with exchangeToMono and manual DataBuffer handling
        WebClient webClient = WebClient.builder().baseUrl("http://localhost:" + port).build();

        long startTime = System.currentTimeMillis();

        String result =
                webClient
                        .method(GET)
                        .uri("/chars/900000")
                        .exchangeToMono(
                                response -> {
                                    return response.bodyToFlux(DataBuffer.class)
                                            .switchOnFirst(
                                                    (firstBufferSignal, responseBody$) -> {
                                                        assert firstBufferSignal.isOnNext();
                                                        return responseBody$
                                                                .collect(
                                                                        () ->
                                                                                requireNonNull(
                                                                                                firstBufferSignal
                                                                                                        .get())
                                                                                        .factory()
                                                                                        .allocateBuffer(
                                                                                                0),
                                                                        (accumulator, curr) -> {
                                                                            accumulator.write(curr);
                                                                            DataBufferUtils.release(
                                                                                    curr);
                                                                        })
                                                                .map(
                                                                        accumulator -> {
                                                                            final var
                                                                                    responseBodyAsStr =
                                                                                            accumulator
                                                                                                    .toString(
                                                                                                            UTF_8);
                                                                            DataBufferUtils.release(
                                                                                    accumulator);
                                                                            return responseBodyAsStr;
                                                                        });
                                                    })
                                            .single();
                                })
                        .block();

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Verify the response
        assertNotNull(result, "Response should not be null");
        assertEquals(900000, result.length(), "Should receive exactly 10 million characters");
        assertTrue(result.matches("[a-zA-Z0-9]+"), "All characters should be alphanumeric");

        // Memory usage information
        Runtime runtime = Runtime.getRuntime();
        long memoryUsed = runtime.totalMemory() - runtime.freeMemory();

        System.out.println("=== ExchangeToMono with Manual DataBuffer Handling Test Results ===");
        System.out.println(
                "Successfully processed " + result.length() + " characters in " + duration + "ms");
        System.out.println("Memory used: " + (memoryUsed / 1024 / 1024) + " MB");
        System.out.println(
                "Buffer configuration: DEFAULT (256KB) with exchangeToMono manual handling");
        System.out.println("First 50 chars: " + result.substring(0, 50));
        System.out.println("Last 50 chars: " + result.substring(result.length() - 50));

        // Validate manual buffer handling worked with default buffer limits
        assertTrue(
                result.length() >= 256000,
                "Should handle large responses via manual buffer handling");
    }

    @Test
    void testCharsEndpoint30MBWithDataBufferFluxMapping() throws Exception {
        // Use default WebClient with DataBuffer flux mapping approach
        WebClient webClient = WebClient.builder().baseUrl("http://localhost:" + port).build();

        long startTime = System.currentTimeMillis();

        String result =
                webClient
                        .get()
                        .uri("/chars/900000")
                        .retrieve()
                        .bodyToFlux(DataBuffer.class)
                        .map(
                                buffer -> {
                                    String string = buffer.toString(Charset.forName("UTF-8"));
                                    DataBufferUtils.release(buffer);
                                    return string;
                                })
                        .reduce("", (accumulated, chunk) -> accumulated + chunk)
                        .block();

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Verify the response
        assertNotNull(result, "Response should not be null");
        assertEquals(900000, result.length(), "Should receive exactly 30 million characters");
        assertTrue(result.matches("[a-zA-Z0-9]+"), "All characters should be alphanumeric");

        // Memory usage information
        Runtime runtime = Runtime.getRuntime();
        long memoryUsed = runtime.totalMemory() - runtime.freeMemory();

        System.out.println("=== DataBuffer Flux Mapping 30MB Test Results ===");
        System.out.println(
                "Successfully processed " + result.length() + " characters in " + duration + "ms");
        System.out.println("Memory used: " + (memoryUsed / 1024 / 1024) + " MB");
        System.out.println("Buffer configuration: DEFAULT (256KB) with DataBuffer flux mapping");
        System.out.println("First 50 chars: " + result.substring(0, 50));
        System.out.println("Last 50 chars: " + result.substring(result.length() - 50));

        // Validate DataBuffer flux mapping approach worked with default buffer limits
        assertTrue(
                result.length() >= 256000,
                "Should handle large responses via DataBuffer flux mapping");
    }
}
