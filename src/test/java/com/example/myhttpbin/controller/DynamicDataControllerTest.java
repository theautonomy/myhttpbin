package com.example.myhttpbin.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.http.MediaType;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Flux;

@SpringBootTest(
        classes = MyhttpbinApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class DynamicDataControllerTest {

    @Autowired private MockMvc mockMvc;

    @LocalServerPort private int port;

    @Test
    void testUuidEndpoint() throws Exception {
        mockMvc.perform(get("/uuid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uuid").exists())
                .andExpect(jsonPath("$.uuid").isString());
    }

    @Test
    void testBase64EndpointValid() throws Exception {
        String testString = "Hello World";
        String encoded = Base64.getEncoder().encodeToString(testString.getBytes());

        mockMvc.perform(get("/base64/" + encoded))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decoded").value(testString));
    }

    @Test
    void testBase64EndpointInvalid() throws Exception {
        mockMvc.perform(get("/base64/invalid-base64!@#"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid Base64"));
    }

    @Test
    void testDelayEndpoint() throws Exception {
        mockMvc.perform(get("/delay/1?test=value"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.args.test").value("value"))
                .andExpect(jsonPath("$.url").exists())
                .andExpect(jsonPath("$.origin").exists())
                .andExpect(jsonPath("$.headers").exists());
    }

    @Test
    void testDelayEndpointTooLong() throws Exception {
        mockMvc.perform(get("/delay/65"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Delay too long"));
    }

    @Test
    void testBytesEndpoint() throws Exception {
        int numBytes = 80000000;
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
        mockMvc.perform(get("/bytes/200000000"))
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
        mockMvc.perform(get("/chars/20000000"))
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
                                    .uri("/chars/10000000")
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
                            .uri("/chars/10000000")
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
                                                .maxInMemorySize(12 * 1024 * 1024)) // 12MB
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
                        .uri("/chars/10000000") // 10MB request
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Verify the response
        assertNotNull(result, "Response should not be null");
        assertEquals(10000000, result.length(), "Should receive exactly 10 million characters");
        assertTrue(result.matches("[a-zA-Z0-9]+"), "All characters should be alphanumeric");

        System.out.println(
                "Successfully received " + result.length() + " characters in " + duration + "ms");
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
                        .uri("/chars/10000000") // 10MB request
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Verify the response
        assertNotNull(result, "Response should not be null");
        assertEquals(10000000, result.length(), "Should receive exactly 10 million characters");
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
        assertTrue(
                result.length() >= 10000000, "Should handle large responses without buffer limit");
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
                        .uri("/chars/10000000") // 10MB request
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
        assertEquals(10000000, result.length(), "Should receive exactly 10 million characters");
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
        assertTrue(result.length() >= 10000000, "Should handle large responses via streaming");
    }

    @Test
    void testDelayPostEndpoint() throws Exception {
        String jsonBody = "{\"test\": \"data\", \"number\": 123}";
        
        mockMvc.perform(post("/delay/1")
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
        
        mockMvc.perform(put("/delay/1")
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
        mockMvc.perform(delete("/delay/2")
                .param("force", "true")
                .param("reason", "cleanup"))
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
        mockMvc.perform(post("/delay/1")
                .param("empty", "true"))
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
}
