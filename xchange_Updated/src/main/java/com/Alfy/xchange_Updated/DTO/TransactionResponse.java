package com.Alfy.xchange_Updated.DTO;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TransactionResponse {
    private boolean success;
    private String message;
    private Long transactionId;
}