package com.wealthflow.wealthflow;

import java.io.BufferedReader;
import java.io.StringReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class MutualFundService {

    private final RestTemplate restTemplate = new RestTemplate();
    
    // Custom cache with TTL
    private final Map<String, CacheEntry<List<Map<String, String>>>> cache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_HOURS = 12;

    private static final String API_URL = "https://api.kite.trade/mf/instruments";
    private static final String API_KEY = "o2f0xfrwmuvcmhwg";
    private static final String ACCESS_TOKEN = "PZA418"; // Replace with valid access token

    public Map<String, Object> getPaginatedMutualFundData(int page, int size) {
        List<Map<String, String>> allData = fetchMutualFundData();

        // Calculate pagination
        int totalElements = allData.size();
        int totalPages = (int) Math.ceil((double) totalElements / size);
        
        // Validate page parameter
        if (page < 0) {
            page = 0;
        }
        if (page >= totalPages && totalPages > 0) {
            page = totalPages - 1;
        }

        // Calculate start and end indices
        int startIndex = page * size;
        int endIndex = Math.min(startIndex + size, totalElements);

        // Extract the page of data
        List<Map<String, String>> pageData = new ArrayList<>();
        if (startIndex < totalElements) {
            pageData = allData.subList(startIndex, endIndex);
        }

        // Create paginated response
        Map<String, Object> paginatedResponse = new HashMap<>();
        paginatedResponse.put("content", pageData);
        paginatedResponse.put("page", page);
        paginatedResponse.put("size", size);
        paginatedResponse.put("totalElements", totalElements);
        paginatedResponse.put("totalPages", totalPages);
        paginatedResponse.put("first", page == 0);
        paginatedResponse.put("last", page >= totalPages - 1);
        paginatedResponse.put("numberOfElements", pageData.size());

        return paginatedResponse;
    }

    private List<Map<String, String>> fetchMutualFundData() {
        String cacheKey = "mutualFundData";
        CacheEntry<List<Map<String, String>>> cachedEntry = cache.get(cacheKey);

        // Check if cache entry exists and is not expired
        if (cachedEntry != null && !cachedEntry.isExpired()) {
            return cachedEntry.getData();
        }

        // Fetch fresh data from API
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Kite-Version", "3");
        headers.set("Authorization", "token " + API_KEY + ":" + ACCESS_TOKEN);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(API_URL, HttpMethod.GET, entity, String.class);
        String csv = response.getBody();

        List<Map<String, String>> data = parseCsv(csv);

        // Cache the data with TTL
        cache.put(cacheKey, new CacheEntry<>(data, CACHE_TTL_HOURS));

        return data;
    }

    private List<Map<String, String>> parseCsv(String csv) {
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
        } catch (Exception e) {
            throw new RuntimeException("Error parsing CSV data", e);
        }

        return result;
    }
    
    // Inner class for cache entry with TTL
    private static class CacheEntry<T> {
        private final T data;
        private final Instant expiryTime;
        
        public CacheEntry(T data, long ttlHours) {
            this.data = data;
            this.expiryTime = Instant.now().plusSeconds(ttlHours * 3600);
        }
        
        public T getData() {
            return data;
        }
        
        public boolean isExpired() {
            return Instant.now().isAfter(expiryTime);
        }
    }
}
