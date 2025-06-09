package com.Alfy.xchange_Updated.Services;

import java.util.HashMap;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import lombok.extern.slf4j.Slf4j;

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
            sourceCurrency = sourceCurrency.toLowerCase();
            JSONObject requestBody = new JSONObject();
            requestBody.put("source", sourceCurrency);
            requestBody.put("targets", new JSONArray(new String[]{"usd", "gbp", "eur", "btc", "eth", "ngn"}));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("apy-token", apiKey);

            log.info("Making API request to: {} with source currency: {}", apiUrl, sourceCurrency);
            log.debug("Request headers: {}", headers);
            log.debug("Request body: {}", requestBody);

            HttpEntity<String> entity = new HttpEntity<>(requestBody.toString(), headers);

            ResponseEntity<String> response = restTemplate.exchange(apiUrl, HttpMethod.POST, entity, String.class);

            log.debug("API Response status: {}", response.getStatusCode());
            log.debug("API Response body: {}", response.getBody());

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JSONObject responseBody = new JSONObject(response.getBody());

                if (responseBody.isEmpty()) {
                    log.error("Empty response body from API");
                    throw new RuntimeException("Empty response from currency API");
                }

                // Extract exchange rates and store them using only the TARGET currency code as key
                final String srcCurrencyFinal = sourceCurrency;
                responseBody.keySet().forEach(key -> {
                    try {
                        double rate = responseBody.getDouble(key);
                        if (rate <= 0) {
                            log.warn("Invalid rate received for {}: {}", key, rate);
                            return;
                        }
                        // Extract target currency (e.g., "usd" from "ngn_usd")
                        String targetCurrency = key.replace(srcCurrencyFinal + "_", "").toUpperCase();
                        rates.put(targetCurrency, rate); // Store as "USD", "GBP", etc.
                        log.info("Rate for {}: {}", targetCurrency, rate);
                    } catch (Exception e) {
                        log.error("Error processing rate for {}: {}", key, e.getMessage());
                    }
                });

                if (rates.isEmpty()) {
                    log.error("No valid rates extracted from API response");
                    throw new RuntimeException("No valid rates received");
                }

                // Add change rates (which are already in the "TARGET_change" format)
                addChangeRates(rates);

                log.info("Successfully fetched {} live exchange rates", rates.size());
            } else {
                log.error("Invalid API response: Status={}, Body={}", response.getStatusCode(), response.getBody());
                throw new RuntimeException("Invalid response from currency API");
            }
        } catch (Exception e) {
            log.error("Error fetching exchange rates: {}", e.getMessage(), e);
            log.warn("Using fallback rates due to error");
            addFallbackRates(rates); // Fallback already adds correct keys
        }

        return rates;
    }

     private void addFallbackRates(Map<String, Double> rates) {
        // Update fallback rates to be in correct format (how much foreign currency equals 1 NGN)
        rates.put("USD", 0.000704);  // 1 NGN = 0.000704 USD
        rates.put("EUR", 0.000654);  // 1 NGN = 0.000654 EUR
        rates.put("GBP", 0.000548);  // 1 NGN = 0.000548 GBP
        rates.put("BTC", 0.0000000150); // 1 NGN = 0.0000000150 BTC
        rates.put("ETH", 0.000000234);  // 1 NGN = 0.000000234 ETH
        rates.put("NGN", 1.0);          // 1 NGN = 1 NGN

        // Add change percentages
        addChangeRates(rates);
    }

    private void addChangeRates(Map<String, Double> rates) {
        // These keys are already in the "TARGET_change" format, which is fine.
        rates.put("USD_change", -2.5);
        rates.put("EUR_change", -1.8);
        rates.put("GBP_change", -2.1);
        rates.put("BTC_change", 1.4);
        rates.put("ETH_change", 0.7);
        rates.put("NGN_change", 0.0); // No change for NGN
    }

    public Map<String, String> getFormattedExchangeRates() {
        Map<String, Double> rates = getExchangeRates("ngn");
        Map<String, String> formattedRates = new HashMap<>();
        
        // Format each rate to show how many NGN equals 1 unit of foreign currency
        for (Map.Entry<String, Double> entry : rates.entrySet()) {
            String currency = entry.getKey();
            Double rate = entry.getValue();
            
            if (!currency.contains("_change") && rate != 0) {
                // Calculate how many NGN equals 1 unit of foreign currency
                double ngnRate = 1 / rate;
                formattedRates.put(currency, String.format("₦%.2f = 1 %s", ngnRate, currency));
            }
        }
        
        return formattedRates;
    }
}