package com.Alfy.xchange_Updated.DTO;

import lombok.Data;

@Data
public class PaymentRequest {
    private Long sellerId;
    private String currency;
    private Double amount;
    private String timestamp; // Or use `LocalDateTime`
}