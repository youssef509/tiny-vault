package com.youssef.storageservice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Simple health check endpoint.
 * This is the ONLY public endpoint — no API key required.
 * Used for monitoring and VPS uptime checks.
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "storage-service",
            "timestamp", Instant.now().toString()
        ));
    }
}
