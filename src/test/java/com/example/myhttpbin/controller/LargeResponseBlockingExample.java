package com.example.myhttpbin.controller;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

/**
 * Demonstrates various ways to handle large HTTP responses in a blocking manner while keeping
 * memory usage low by avoiding the need to increase max-in-memory-size
 */
public class LargeResponseBlockingExample {

    private final WebClient webClient;
    private final RestTemplate restTemplate;

    public LargeResponseBlockingExample() {
        // WebClient with small memory buffer (256KB default)
        this.webClient =
                WebClient.builder()
                        .codecs(
                                configurer ->
                                        configurer
                                                .defaultCodecs()
                                                .maxInMemorySize(256 * 1024)) // Keep small - 256KB
                        .build();

        this.restTemplate = new RestTemplate();
    }

    /**
     * Method 1: Block on Flux processing - processes chunks sequentially Memory usage: Low (only
     * one chunk at a time) Blocking: Yes (blocks until all chunks processed)
     */
    public ProcessingResult processLargeResponseWithBlockLast(String url) {
        System.out.println("=== Method 1: Block on Flux Processing ===");

        AtomicLong totalBytes = new AtomicLong(0);
        AtomicInteger chunkCount = new AtomicInteger(0);
        List<String> processedData = new ArrayList<>();

        long startTime = System.currentTimeMillis();

        webClient
                .get()
                .uri(url)
                .retrieve()
                .bodyToFlux(DataBuffer.class)
                .doOnNext(
                        dataBuffer -> {
                            // Process each chunk
                            int size = dataBuffer.readableByteCount();
                            byte[] bytes = new byte[size];
                            dataBuffer.read(bytes);

                            totalBytes.addAndGet(size);
                            chunkCount.incrementAndGet();

                            // Simulate processing - extract lines, parse JSON, etc.
                            String content = new String(bytes, StandardCharsets.UTF_8);
                            String processed = processChunkContent(content, chunkCount.get());
                            processedData.add(processed);

                            // Important: Release the buffer
                            DataBufferUtils.release(dataBuffer);

                            // Log progress every 10 chunks
                            if (chunkCount.get() % 10 == 0) {
                                System.out.printf(
                                        "Processed %d chunks, %d bytes%n",
                                        chunkCount.get(), totalBytes.get());
                            }
                        })
                .blockLast(); // THIS IS THE BLOCKING CALL

        long duration = System.currentTimeMillis() - startTime;

        return new ProcessingResult(
                "BlockLast Method",
                totalBytes.get(),
                chunkCount.get(),
                processedData.size(),
                duration);
    }

    /**
     * Method 2: Collect results and block - accumulates processed results Memory usage: Medium
     * (stores processed results, but not raw data) Blocking: Yes (blocks until all processing
     * complete)
     */
    public ProcessingResult collectAndProcessBlocking(String url) {
        System.out.println("=== Method 2: Collect Results and Block ===");

        long startTime = System.currentTimeMillis();

        List<ProcessedChunk> results =
                webClient
                        .get()
                        .uri(url)
                        .retrieve()
                        .bodyToFlux(DataBuffer.class)
                        .map(
                                dataBuffer -> {
                                    // Process each chunk and return result object
                                    int size = dataBuffer.readableByteCount();
                                    byte[] bytes = new byte[size];
                                    dataBuffer.read(bytes);

                                    String content = new String(bytes, StandardCharsets.UTF_8);
                                    ProcessedChunk chunk =
                                            new ProcessedChunk(size, content.lines().count());

                                    DataBufferUtils.release(dataBuffer);
                                    return chunk;
                                })
                        .collectList() // Collect all processed chunks
                        .block(); // THIS IS THE BLOCKING CALL

        long duration = System.currentTimeMillis() - startTime;

        // Aggregate results
        long totalBytes = results.stream().mapToLong(ProcessedChunk::getSize).sum();
        long totalLines = results.stream().mapToLong(ProcessedChunk::getLineCount).sum();

        return new ProcessingResult(
                "Collect and Block Method", totalBytes, results.size(), (int) totalLines, duration);
    }

    /**
     * Method 3: Stream to temporary file, then process blocking Memory usage: Very low (constant
     * regardless of response size) Blocking: Yes (blocks on file write, then processes file
     * synchronously)
     */
    public ProcessingResult streamToFileAndProcessBlocking(String url) throws IOException {
        System.out.println("=== Method 3: Stream to File then Process ===");

        Path tempFile = Files.createTempFile("large-response", ".tmp");
        long startTime = System.currentTimeMillis();

        try {
            // First phase: Stream to file (blocking)
            webClient
                    .get()
                    .uri(url)
                    .retrieve()
                    .bodyToFlux(DataBuffer.class)
                    .flatMap(
                            dataBuffer -> {
                                try {
                                    // Write chunk directly to file
                                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                                    dataBuffer.read(bytes);
                                    Files.write(tempFile, bytes, StandardOpenOption.APPEND);
                                    DataBufferUtils.release(dataBuffer);
                                    return Mono.empty();
                                } catch (IOException e) {
                                    DataBufferUtils.release(dataBuffer);
                                    return Mono.error(e);
                                }
                            })
                    .then()
                    .block(); // Block until file write complete

            // Second phase: Process file synchronously
            return processFileBlocking(tempFile, startTime);

        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    /**
     * Method 4: Using RestTemplate for true blocking with streaming Memory usage: Low (processes
     * line by line) Blocking: Yes (completely synchronous)
     */
    public ProcessingResult processWithRestTemplateBlocking(String url) {
        System.out.println("=== Method 4: RestTemplate Blocking Stream ===");

        long startTime = System.currentTimeMillis();

        RequestCallback requestCallback =
                request -> {
                    // Can set headers, etc. if needed
                    request.getHeaders().add("Accept", "text/plain,application/json");
                };

        ResponseExtractor<ProcessingResult> responseExtractor =
                response -> {
                    AtomicLong totalBytes = new AtomicLong(0);
                    AtomicInteger lineCount = new AtomicInteger(0);
                    List<String> processedLines = new ArrayList<>();

                    try (BufferedReader reader =
                            new BufferedReader(
                                    new InputStreamReader(
                                            response.getBody(), StandardCharsets.UTF_8))) {

                        String line;
                        while ((line = reader.readLine()) != null) {
                            totalBytes.addAndGet(line.getBytes(StandardCharsets.UTF_8).length);
                            lineCount.incrementAndGet();

                            // Process each line
                            String processed = processLine(line, lineCount.get());
                            processedLines.add(processed);

                            // Log progress
                            if (lineCount.get() % 100 == 0) {
                                System.out.printf(
                                        "Processed %d lines, %d bytes%n",
                                        lineCount.get(), totalBytes.get());
                            }
                        }

                        long duration = System.currentTimeMillis() - startTime;

                        return new ProcessingResult(
                                "RestTemplate Blocking Method",
                                totalBytes.get(),
                                lineCount.get(),
                                processedLines.size(),
                                duration);

                    } catch (IOException e) {
                        throw new RuntimeException("Error processing response", e);
                    }
                };

        return restTemplate.execute(url, HttpMethod.GET, requestCallback, responseExtractor);
    }

    /**
     * Method 5: Batched processing with blocking Memory usage: Low to medium (processes in
     * controlled batches) Blocking: Yes (blocks between batches and at the end)
     */
    public ProcessingResult batchedProcessingBlocking(String url, int batchSize) {
        System.out.println("=== Method 5: Batched Processing with Blocking ===");

        AtomicLong totalBytes = new AtomicLong(0);
        AtomicInteger totalChunks = new AtomicInteger(0);
        AtomicInteger batchCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);
        List<String> allResults = new ArrayList<>();

        long startTime = System.currentTimeMillis();

        webClient
                .get()
                .uri(url)
                .retrieve()
                .bodyToFlux(DataBuffer.class)
                .buffer(batchSize) // Process in batches
                .doOnNext(
                        batchList -> {
                            batchCount.incrementAndGet();
                            List<String> batchResults = new ArrayList<>();

                            // Process each batch synchronously
                            for (DataBuffer buffer : batchList) {
                                int size = buffer.readableByteCount();
                                byte[] bytes = new byte[size];
                                buffer.read(bytes);

                                totalBytes.addAndGet(size);
                                totalChunks.incrementAndGet();

                                String content = new String(bytes, StandardCharsets.UTF_8);
                                String processed = processChunkContent(content, totalChunks.get());
                                batchResults.add(processed);

                                DataBufferUtils.release(buffer);
                            }

                            // Simulate some processing time between batches
                            try {
                                Thread.sleep(50);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }

                            synchronized (allResults) {
                                allResults.addAll(batchResults);
                            }

                            System.out.printf(
                                    "Completed batch %d with %d chunks%n",
                                    batchCount.get(), batchList.size());
                        })
                .doOnComplete(() -> latch.countDown())
                .doOnError(error -> latch.countDown())
                .subscribe();

        // Block until all processing is complete
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Processing interrupted", e);
        }

        long duration = System.currentTimeMillis() - startTime;

        return new ProcessingResult(
                "Batched Processing Method",
                totalBytes.get(),
                totalChunks.get(),
                allResults.size(),
                duration);
    }

    // Helper methods
    private String processChunkContent(String content, int chunkNumber) {
        // Simulate processing: count lines, extract data, etc.
        long lineCount = content.lines().count();
        return String.format("Chunk %d: %d lines processed", chunkNumber, lineCount);
    }

    private String processLine(String line, int lineNumber) {
        // Simulate line processing: parsing, validation, transformation
        return String.format(
                "Line %d: %s (processed)",
                lineNumber, line.length() > 50 ? line.substring(0, 50) + "..." : line);
    }

    private ProcessingResult processFileBlocking(Path file, long startTime) throws IOException {
        AtomicLong totalBytes = new AtomicLong(Files.size(file));
        AtomicInteger lineCount = new AtomicInteger(0);
        List<String> processedLines = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.ISO_8859_1)) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineCount.incrementAndGet();
                String processed = processLine(line, lineCount.get());
                processedLines.add(processed);

                if (lineCount.get() % 100 == 0) {
                    System.out.printf("File processing: %d lines%n", lineCount.get());
                }
            }
        }

        long duration = System.currentTimeMillis() - startTime;

        return new ProcessingResult(
                "Stream to File Method",
                totalBytes.get(),
                lineCount.get(),
                processedLines.size(),
                duration);
    }

    // Data classes
    public static class ProcessingResult {
        private final String method;
        private final long totalBytes;
        private final int chunkCount;
        private final int processedItems;
        private final long durationMs;

        public ProcessingResult(
                String method,
                long totalBytes,
                int chunkCount,
                int processedItems,
                long durationMs) {
            this.method = method;
            this.totalBytes = totalBytes;
            this.chunkCount = chunkCount;
            this.processedItems = processedItems;
            this.durationMs = durationMs;
        }

        @Override
        public String toString() {
            return String.format(
                    "%s: %d bytes, %d chunks, %d items processed in %d ms (%.2f MB/s)",
                    method,
                    totalBytes,
                    chunkCount,
                    processedItems,
                    durationMs,
                    (totalBytes / 1024.0 / 1024.0) / (durationMs / 1000.0));
        }

        // Getters
        public String getMethod() {
            return method;
        }

        public long getTotalBytes() {
            return totalBytes;
        }

        public int getChunkCount() {
            return chunkCount;
        }

        public int getProcessedItems() {
            return processedItems;
        }

        public long getDurationMs() {
            return durationMs;
        }
    }

    private static class ProcessedChunk {
        private final int size;
        private final long lineCount;

        public ProcessedChunk(int size, long lineCount) {
            this.size = size;
            this.lineCount = lineCount;
        }

        public int getSize() {
            return size;
        }

        public long getLineCount() {
            return lineCount;
        }
    }

    // Demo main method
    public static void main(String[] args) throws IOException {
        LargeResponseBlockingExample example = new LargeResponseBlockingExample();

        // Example URLs - replace with actual large response endpoints
        // String largeFileUrl = "https://httpbin.org/bytes/1048576"; // 1MB test file
        String largeFileUrl = "http://localhost:8080/bytes/80000000";
        String largeTextUrl = "https://httpbin.org/base64/MTIzNDU2Nzg5MA=="; // Test text

        System.out.println("Testing different blocking approaches for large responses...\n");

        try {
            // Test each method
            ProcessingResult result1 = example.processLargeResponseWithBlockLast(largeFileUrl);
            System.out.println(result1 + "\n");

            ProcessingResult result2 = example.collectAndProcessBlocking(largeFileUrl);
            System.out.println(result2 + "\n");

            ProcessingResult result3 = example.streamToFileAndProcessBlocking(largeFileUrl);
            System.out.println(result3 + "\n");

            ProcessingResult result4 = example.processWithRestTemplateBlocking(largeTextUrl);
            System.out.println(result4 + "\n");

            ProcessingResult result5 = example.batchedProcessingBlocking(largeFileUrl, 5);
            System.out.println(result5 + "\n");

        } catch (Exception e) {
            System.err.println("Error during processing: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
