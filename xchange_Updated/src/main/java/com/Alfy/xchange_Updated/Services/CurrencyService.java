package com.Alfy.xchange_Updated.Services;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.json.JSONObject;
import org.json.JSONArray;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class CurrencyService {
    
    @Value("${currency.api.url}")
    private String apiUrl;
    
    @Value("${apy.hub.token}")
    private String apiKey;
    
    private final RestTemplate restTemplate;

    public CurrencyService() {
        this.restTemplate = new RestTemplate();
    }
    
    public Map<String, Double> getExchangeRates(String sourceCurrency) {
        Map<String, Double> rates = new HashMap<>();

        try {
            // Prepare the request body
            JSONObject requestBody = new JSONObject();
            requestBody.put("source", sourceCurrency);
            requestBody.put("targets", new JSONArray(new String[]{"usd", "gbp", "eur", "btc", "eth","ngn"}));
            
            // Set headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("apy-token", apiKey);
            
            // Build request entity
            HttpEntity<String> entity = new HttpEntity<>(requestBody.toString(), headers);
            
            // Make API POST request
            ResponseEntity<String> response = restTemplate.exchange(apiUrl, HttpMethod.POST, entity, String.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JSONObject responseBody = new JSONObject(response.getBody());
                
                // Extract exchange rates and invert them (1 foreign currency = X NGN)
                responseBody.keySet().forEach(key -> {
                    double rate = responseBody.getDouble(key);
                    rates.put(key.toUpperCase(), 1 / rate);
                });

                // Add mock change rates (since API doesn't provide them)
                addChangeRates(rates);
                
                log.info("Successfully fetched live exchange rates");
            } else {
                log.warn("Empty or invalid response from currency API, using fallback rates");
                addFallbackRates(rates);
            }
        } catch (Exception e) {
            log.error("Error fetching exchange rates: {}", e.getMessage());
            addFallbackRates(rates);
        }

        return rates;
    }
    
    private void addFallbackRates(Map<String, Double> rates) {
        // Fallback rates (showing how many NGN equals 1 unit of foreign currency)
        rates.put("USD", 1420.0);  // 1 USD = ₦1420
        rates.put("EUR", 1530.0);  // 1 EUR = ₦1530
        rates.put("GBP", 1825.0);  // 1 GBP = ₦1825
        rates.put("BTC", 66450000.0); // 1 BTC = ₦66,450,000
        rates.put("ETH", 4280000.0);
        rates.put("NGN", 1.0); // 1 NGN = 1 NGN (self-reference)
        
        // Add change percentages
        addChangeRates(rates);
    }

    private void addChangeRates(Map<String, Double> rates) {
        rates.put("USD_change", -2.5);
        rates.put("EUR_change", -1.8);
        rates.put("GBP_change", -2.1);
        rates.put("BTC_change", 1.4);
        rates.put("ETH_change", 0.7);
        rates.put("NGN_change", 0.0); // No change for NGN
    }
}