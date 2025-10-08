package com.wealthflow.wealthflow;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MfController {

    private final MutualFundService mutualFundService;
    private final AiInsightService aiInsightService;

    public MfController(MutualFundService mutualFundService, AiInsightService aiInsightService) {
        this.mutualFundService = mutualFundService;
        this.aiInsightService = aiInsightService;
    }

    @GetMapping("/api/mf/instruments")
    public ResponseEntity<?> getMutualFundInstruments(
            @RequestParam(defaultValue = "0") int page, 
            @RequestParam(defaultValue = "10") int size) {
        try {
            Map<String, Object> paginatedResponse = mutualFundService.getPaginatedMutualFundData(page, size);
            return ResponseEntity.ok(paginatedResponse);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error fetching or parsing data: " + e.getMessage());
        }
    }

    @PostMapping("/api/mf/insights")
    public ResponseEntity<?> generateInsights(@RequestBody Map<String, String> fund) {
        try {
            Map<String, Object> insights = aiInsightService.generateInsights(fund);
            return ResponseEntity.ok(insights);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error generating AI insights: " + e.getMessage());
        }
    }
}