package com.wealthflow.wealthflow;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class AiInsightService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    @Value("${gemini.model:gemini-1.5-flash}")
    private String geminiModel;

    @Value("${gemini.api.url:}")
    private String geminiApiUrl;

    public Map<String, Object> generateInsights(Map<String, String> fund) {
        String endpoint;
        if (geminiApiUrl != null && !geminiApiUrl.isBlank()) {
            endpoint = geminiApiUrl;
        } else {
            endpoint = String.format("https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent", geminiModel);
        }

        String url = UriComponentsBuilder.fromHttpUrl(endpoint)
                .queryParam("key", geminiApiKey)
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        Map<String, Object> content = new HashMap<>();
        Map<String, Object> part = new HashMap<>();

        String prompt = buildPrompt(fund);
        part.put("text", prompt);
        content.put("parts", List.of(part));
        body.put("contents", List.of(content));

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, Map.class);

        Map<String, Object> responseBody = response.getBody();
        Map<String, Object> result = new HashMap<>();
        result.put("fund", fund);
        result.put("insights", extractText(responseBody));
        return result;
    }

    private String buildPrompt(Map<String, String> fund) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a financial research assistant. Analyze the following mutual fund and produce detailed, factual, and neutral insights to help a retail investor decide suitability.\n\n");
        sb.append("Fund data (CSV fields as key=value):\n");
        for (Map.Entry<String, String> entry : fund.entrySet()) {
            sb.append("- ").append(entry.getKey()).append(" = ").append(entry.getValue()).append("\n");
        }
        sb.append("\nOutput a well-structured markdown report with these sections:\n");
        sb.append("1. Historical performance: CAGR, drawdowns, rolling returns (if known or infer using general category trends; avoid fabricating unknowns).\n");
        sb.append("2. Description & objectives: investment objective, category, style, benchmarks.\n");
        sb.append("3. Sector-wise holdings & diversification: typical sector allocation and concentration risks for this fund/category.\n");
        sb.append("4. Statistical & analytics: risk metrics (volatility, Sharpe-like discussion), expense ratio considerations, minimum SIP/lump sum if relevant.\n");
        sb.append("5. Suitability: who it suits, investment horizon, and key risks.\n\n");
        sb.append("Important: If specific numeric data is not provided in input, base insights on the fund's category and general market knowledge without inventing exact numbers. Use clear headings and bullet points.");
        return sb.toString();
    }

    private String extractText(Map<String, Object> responseBody) {
        if (responseBody == null) {
            return "No response from AI model.";
        }
        try {
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) responseBody.get("candidates");
            if (candidates == null || candidates.isEmpty()) {
                return "No insights generated.";
            }
            Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
            if (content == null) {
                return "No content in AI response.";
            }
            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            if (parts == null || parts.isEmpty()) {
                return "No parts in AI response.";
            }
            Object text = parts.get(0).get("text");
            return text == null ? "" : text.toString();
        } catch (Exception e) {
            return "Failed to parse AI response.";
        }
    }
}


