package com.wealthflow.wealthflow;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.*;

@RestController
public class MfController {

    private final RestTemplate restTemplate = new RestTemplate();

    private static final String API_URL = "https://api.kite.trade/mf/instruments";
    private static final String API_KEY = "o2f0xfrwmuvcmhwg";
    private static final String ACCESS_TOKEN = "PZA418"; // Replace with valid access token

    @GetMapping("/api/mf/instruments")
    public ResponseEntity<?> getMutualFundInstruments(
            @RequestParam int page, @RequestParam int size) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Kite-Version", "3");
        headers.set("Authorization", "token " + API_KEY + ":" + ACCESS_TOKEN);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(API_URL, HttpMethod.GET, entity, String.class);

            String csv = response.getBody();
            List<Map<String, String>> parsedData = parseCsv(csv);

            return ResponseEntity.ok(parsedData);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error fetching or parsing data: " + e.getMessage());
        }
    }

    private List<Map<String, String>> parseCsv(String csv) throws Exception {
        List<Map<String, String>> result = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new StringReader(csv))) {
            String headerLine = reader.readLine();
            if (headerLine == null) return result;

            String[] headers = headerLine.split(",");

            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.split(",", -1); // -1 keeps trailing empty strings
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.length && i < fields.length; i++) {
                    row.put(headers[i], fields[i]);
                }
                result.add(row);
            }
        }

        return result;
    }
}