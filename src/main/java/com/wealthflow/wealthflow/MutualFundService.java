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

import org.springframework.beans.factory.annotation.Value;
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

    @Value("${kite.api.url}")
    private String apiUrl;
    
    @Value("${kite.api.key}")
    private String apiKey;
    
    @Value("${kite.api.access-token}")
    private String accessToken;

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
        headers.set("Authorization", "token " + apiKey + ":" + accessToken);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(apiUrl, HttpMethod.GET, entity, String.class);
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

    public Map<String, Object> getFundWithAiInsights(String tradingsymbol) {
        List<Map<String, String>> allData = fetchMutualFundData();
        
        // Find the specific fund
        Map<String, String> fund = allData.stream()
            .filter(f -> tradingsymbol.equals(f.get("tradingsymbol")))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Fund not found: " + tradingsymbol));
        
        // Create overview from fund data
        Map<String, Object> overview = new HashMap<>();
        overview.put("tradingsymbol", fund.get("tradingsymbol"));
        overview.put("name", fund.get("name"));
        overview.put("amc", fund.get("amc"));
        overview.put("scheme_type", fund.get("scheme_type"));
        overview.put("plan", fund.get("plan"));
        overview.put("last_price", fund.get("last_price"));
        overview.put("last_price_date", fund.get("last_price_date"));
        
        // Generate AI insights with structured output
        Map<String, Object> aiInsights = generateStructuredInsights(fund);
        
        // Create response structure expected by frontend
        Map<String, Object> response = new HashMap<>();
        response.put("overview", overview);
        response.put("ai", aiInsights);
        
        return response;
    }

    private Map<String, Object> generateStructuredInsights(Map<String, String> fund) {
        String schemeType = fund.getOrDefault("scheme_type", "");
        String plan = fund.getOrDefault("plan", "");
        String dividendType = fund.getOrDefault("dividend_type", "");
        
        Map<String, Object> insights = new HashMap<>();
        
        // Generate description based on fund characteristics
        String description = generateDescription(schemeType, plan, dividendType, fund.get("name"));
        insights.put("description", description);
        
        // Generate objectives based on scheme type
        insights.put("objectives", generateObjectives(schemeType));
        
        // Mock historical performance (in real implementation, fetch from data provider)
        insights.put("historicalPerformance", generateMockPerformance());
        
        // Generate sector holdings based on scheme type
        insights.put("sectorHoldings", generateSectorHoldings(schemeType));
        
        // Generate statistical metrics
        insights.put("stats", generateStatistics(fund));
        
        // Generate investment recommendation
        insights.put("summaryRecommendation", generateRecommendation(fund));
        
        return insights;
    }

    private String generateDescription(String schemeType, String plan, String dividendType, String name) {
        StringBuilder desc = new StringBuilder();
        desc.append("This is a ");
        
        if (schemeType.toLowerCase().contains("equity")) {
            desc.append("equity mutual fund that primarily invests in stocks and equity-related instruments. ");
            desc.append("It aims to provide long-term capital appreciation through diversified equity investments. ");
        } else if (schemeType.toLowerCase().contains("debt") || schemeType.toLowerCase().contains("bond")) {
            desc.append("debt mutual fund that invests in fixed-income securities like bonds, government securities, and money market instruments. ");
            desc.append("It aims to provide regular income with capital preservation. ");
        } else if (schemeType.toLowerCase().contains("hybrid") || schemeType.toLowerCase().contains("balanced")) {
            desc.append("hybrid mutual fund that invests in both equity and debt instruments to balance growth and income. ");
            desc.append("It aims to provide capital appreciation with lower volatility than pure equity funds. ");
        } else {
            desc.append("mutual fund that follows a specific investment strategy based on its mandate. ");
        }
        
        if (plan.toLowerCase().contains("growth")) {
            desc.append("The growth plan reinvests all earnings to compound returns over time. ");
        } else if (plan.toLowerCase().contains("dividend")) {
            desc.append("This plan may distribute periodic dividends to investors. ");
        }
        
        desc.append("Suitable for investors with appropriate risk tolerance and investment horizon.");
        
        return desc.toString();
    }

    private List<String> generateObjectives(String schemeType) {
        List<String> objectives = new ArrayList<>();
        
        if (schemeType.toLowerCase().contains("equity")) {
            objectives.add("Long-term capital appreciation");
            objectives.add("Wealth creation through equity investments");
            objectives.add("Beat inflation over long term");
        } else if (schemeType.toLowerCase().contains("debt")) {
            objectives.add("Regular income generation");
            objectives.add("Capital preservation");
            objectives.add("Low to moderate risk");
        } else if (schemeType.toLowerCase().contains("hybrid")) {
            objectives.add("Balanced growth and income");
            objectives.add("Moderate risk profile");
            objectives.add("Diversification across asset classes");
        } else {
            objectives.add("Achieve investment objectives as per scheme mandate");
            objectives.add("Professional fund management");
        }
        
        return objectives;
    }

    private List<Map<String, Object>> generateMockPerformance() {
        List<Map<String, Object>> performance = new ArrayList<>();
        
        // Generate last 12 months mock data
        double baseNAV = 50.0;
        for (int i = 12; i >= 0; i--) {
            Map<String, Object> point = new HashMap<>();
            java.time.LocalDate date = java.time.LocalDate.now().minusMonths(i);
            point.put("date", date.toString());
            // Add some realistic variation
            double variation = (Math.random() - 0.5) * 10; // ±5% variation
            point.put("nav", Math.round((baseNAV + variation) * 100.0) / 100.0);
            baseNAV = (Double) point.get("nav");
            performance.add(point);
        }
        
        return performance;
    }

    private List<Map<String, Object>> generateSectorHoldings(String schemeType) {
        List<Map<String, Object>> sectors = new ArrayList<>();
        
        if (schemeType.toLowerCase().contains("equity")) {
            // Mock equity sector allocation
            sectors.add(createSector("Financial Services", 25.5));
            sectors.add(createSector("Information Technology", 18.2));
            sectors.add(createSector("Consumer Goods", 15.8));
            sectors.add(createSector("Healthcare", 12.3));
            sectors.add(createSector("Energy", 10.1));
            sectors.add(createSector("Automobiles", 8.7));
            sectors.add(createSector("Others", 9.4));
        } else if (schemeType.toLowerCase().contains("debt")) {
            // Mock debt instrument allocation
            sectors.add(createSector("Government Securities", 45.2));
            sectors.add(createSector("Corporate Bonds", 28.7));
            sectors.add(createSector("Money Market Instruments", 15.3));
            sectors.add(createSector("Bank Deposits", 10.8));
        }
        
        return sectors;
    }

    private Map<String, Object> createSector(String sector, double percentage) {
        Map<String, Object> sectorMap = new HashMap<>();
        sectorMap.put("sector", sector);
        sectorMap.put("percentage", percentage);
        return sectorMap;
    }

    private List<Map<String, String>> generateStatistics(Map<String, String> fund) {
        List<Map<String, String>> stats = new ArrayList<>();
        
        // Add fund-specific statistics
        stats.add(createStat("Minimum Investment", "₹" + formatAmount(fund.get("minimum_purchase_amount"))));
        stats.add(createStat("Additional Investment", "₹" + formatAmount(fund.get("minimum_additional_purchase_amount"))));
        stats.add(createStat("Current NAV", "₹" + fund.get("last_price")));
        stats.add(createStat("NAV Date", formatDate(fund.get("last_price_date"))));
        
        // Add estimated metrics based on fund type
        String schemeType = fund.getOrDefault("scheme_type", "");
        if (schemeType.toLowerCase().contains("equity")) {
            stats.add(createStat("Risk Level", "High"));
            stats.add(createStat("Investment Horizon", "5+ years"));
            stats.add(createStat("Volatility", "High"));
        } else if (schemeType.toLowerCase().contains("debt")) {
            stats.add(createStat("Risk Level", "Low to Moderate"));
            stats.add(createStat("Investment Horizon", "1-3 years"));
            stats.add(createStat("Volatility", "Low"));
        }
        
        return stats;
    }

    private Map<String, String> createStat(String label, String value) {
        Map<String, String> stat = new HashMap<>();
        stat.put("label", label);
        stat.put("value", value);
        return stat;
    }

    private String formatAmount(String amount) {
        try {
            double amt = Double.parseDouble(amount);
            if (amt >= 100000) {
                return String.format("%.1fL", amt / 100000);
            } else if (amt >= 1000) {
                return String.format("%.0fK", amt / 1000);
            } else {
                return String.format("%.0f", amt);
            }
        } catch (Exception e) {
            return amount;
        }
    }

    private String formatDate(String date) {
        try {
            // Assuming date is in YYYY-MM-DD format
            String[] parts = date.split("-");
            if (parts.length == 3) {
                return parts[2] + "/" + parts[1] + "/" + parts[0];
            }
        } catch (Exception e) {
            // Return as is if parsing fails
        }
        return date;
    }

    private String generateRecommendation(Map<String, String> fund) {
        String schemeType = fund.getOrDefault("scheme_type", "");
        StringBuilder rec = new StringBuilder();
        
        if (schemeType.toLowerCase().contains("equity")) {
            rec.append("Suitable for investors seeking long-term wealth creation with high risk appetite. ");
            rec.append("Recommended investment horizon: 5+ years. Consider SIP for rupee cost averaging.");
        } else if (schemeType.toLowerCase().contains("debt")) {
            rec.append("Suitable for conservative investors seeking steady income with capital protection. ");
            rec.append("Good for short to medium-term goals. Lower volatility compared to equity funds.");
        } else if (schemeType.toLowerCase().contains("hybrid")) {
            rec.append("Ideal for moderate risk investors wanting balanced exposure to equity and debt. ");
            rec.append("Provides diversification and moderate growth potential with lower volatility than pure equity.");
        } else {
            rec.append("Please consult with a financial advisor to understand if this fund aligns with your investment goals and risk profile.");
        }
        
        return rec.toString();
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
