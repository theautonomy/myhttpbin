package com.example.myhttpbin.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Base64;

import com.example.myhttpbin.MyhttpbinApplication;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = MyhttpbinApplication.class)
@AutoConfigureMockMvc
class DynamicDataControllerTest {

    @Autowired private MockMvc mockMvc;

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
        mockMvc.perform(get("/delay/15"))
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
        mockMvc.perform(get("/bytes/200000"))
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
}
