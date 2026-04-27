package com.bankapplication.management.controllers;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/diagnostics")
public class SystemDiagnosticsController {
    // endpoint to retrieve specific system metrics based on the provided metric name, with error handling for invalid metric names
    @GetMapping(value = "/metric/{metricName}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> getSystemMetric(@PathVariable String metricName) {
        String metricValue = System.getProperty(metricName);
        if (metricValue == null) {
            throw new IllegalArgumentException(metricName);
        }
        return ResponseEntity.ok(Map.of(metricName, metricValue));
    }
}