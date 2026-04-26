package com.bankapplication.management.controllers;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

// do not check this class
@RestController
@RequestMapping("/api/diagnostics")
public class SystemDiagnosticsController {

    @GetMapping(value = "/metric/{metricName}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> getSystemMetric(@PathVariable String metricName) {
        String metricValue = System.getProperty(metricName);
        if (metricValue == null) {
            throw new IllegalArgumentException(metricName);
        }
        return ResponseEntity.ok(Map.of(metricName, metricValue));
    }
}