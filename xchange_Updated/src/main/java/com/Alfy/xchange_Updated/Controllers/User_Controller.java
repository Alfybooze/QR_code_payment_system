package com.Alfy.xchange_Updated.Controllers;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.Alfy.xchange_Updated.DTO.AuthResponse;
import com.Alfy.xchange_Updated.DTO.ErrorResponse;
import com.Alfy.xchange_Updated.DTO.PaymentRequest;
import com.Alfy.xchange_Updated.Models.Transaction;
import com.Alfy.xchange_Updated.Models.Users;
import com.Alfy.xchange_Updated.Repositories.UsersRepository;
import com.Alfy.xchange_Updated.Services.CurrencyService;
import com.Alfy.xchange_Updated.Services.EmailService;
import com.Alfy.xchange_Updated.Services.JwtService;
import com.Alfy.xchange_Updated.Services.QRCodeService;
import com.Alfy.xchange_Updated.Services.SmsService;
import com.Alfy.xchange_Updated.Services.TransactionService;
import com.Alfy.xchange_Updated.Services.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.mail.MessagingException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/user")
public class User_Controller {

    private final QRCodeService qrCodeService;  
    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final UsersRepository usersRepository;
    private final JwtService jwtService;
    private final TransactionService transactionService;
    private final CurrencyService currencyService;
    private final EmailService emailService;
    private final SmsService smsService;

    @Autowired
    public User_Controller(UserService userService, AuthenticationManager authenticationManager, JwtService jwtService,QRCodeService qrCodeService, UsersRepository usersRepository, TransactionService transactionService, CurrencyService currencyService, EmailService emailService, SmsService smsService) {
        this.currencyService = currencyService;
        this.transactionService = transactionService;
        this.usersRepository = usersRepository;
        this.userService = userService;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.qrCodeService = qrCodeService;
        this.emailService = emailService;
        this.smsService = smsService;
    }

      @PostMapping("/registers")
    public ResponseEntity<?> registerUser(@RequestBody Users user) {
        try {
            Users registeredUser = userService.addUser(user);
            return ResponseEntity.ok("User registered successfully with ID: " + registeredUser.getId());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
        @PostMapping("/send-code")
    public ResponseEntity<?> sendVerificationCode(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String code = request.get("code");
        
        if (email == null || code == null) {
            return ResponseEntity.badRequest().body("Email and code are required");
        }
        
        try {
            emailService.sendVerificationEmail(email, code);
            return ResponseEntity.ok().body("Verification code sent successfully");
        } catch (MessagingException e) {
            return ResponseEntity.internalServerError().body("Failed to send verification email: " + e.getMessage());
        }
    }
    @PostMapping("/logins")
    public ResponseEntity<?> login(@RequestBody Users loginRequest,HttpServletRequest request) {
        try {
            // Authenticate the user
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                    loginRequest.getUsername(), 
                    loginRequest.getPassword()
                )
            );
    
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            // Change this line to use findByUsername instead of loadUserByUsername
            Users user = userService.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    
            // Generate JWT token
            String token = jwtService.generateToken(userDetails);

            HttpSession session = request.getSession(true); // Create new session
            session.setMaxInactiveInterval(1800); // 30 minutes
            session.setAttribute("jwt_token", token);
            session.setAttribute("username", user.getUsername());
    
            // Create response
            AuthResponse response = AuthResponse.builder()
                .token("Bearer " + token)
                .username(user.getUsername())
                .role(user.getRole())
                .balance(user.getBalance()) // Added balance
                .success(true)
                .message("Login successful")
                .build();
    
            return ResponseEntity.ok(response);
        } catch (AuthenticationException e) {
            return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("Authentication failed: " + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("An error occurred during login"));
        }
    }

    @PostMapping("/generate")
    public ResponseEntity<?> generateQRCode(
        @RequestParam Double totalAmount,
        @RequestParam String currency,
        @RequestBody Map<String, Object> paymentData
    ) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            Users currentUser = userService.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
    
            // Generate QR code with amount and currency
            byte[] qrCodeImage = qrCodeService.generateQRCode(
                totalAmount,
                currentUser.getId(),
                currentUser.getUsername(),
                currency
            );
    
            Map<String, Object> response = new HashMap<>();
            response.put("qrCodeImage", Base64.getEncoder().encodeToString(qrCodeImage));
            response.put("sellerId", currentUser.getId());
            response.put("username", currentUser.getUsername());
            response.put("totalAmount", totalAmount);
            response.put("currency", currency);
    
            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    
        } catch (Exception e) {
            log.error("Failed to generate QR code: ", e);
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Failed to generate QR code: " + e.getMessage()));
        }
    }
    @PostMapping("/scan-and-pay")
    public ResponseEntity<?> processPayment(@RequestBody String qrCodeData) {
        try {
            // Step 1: Decode QR (if sent as Base64)
            byte[] decodedBytes = Base64.getDecoder().decode(qrCodeData);
            String jsonData = new String(decodedBytes, StandardCharsets.UTF_8);
    
            // Step 2: Parse JSON
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Object> qrData = objectMapper.readValue(jsonData, Map.class);
            
            // NEW - Step 2.5: Check QR code expiration
            if (qrData.containsKey("expirationTime")) {
                String expirationTimeStr = qrData.get("expirationTime").toString();
                LocalDateTime expirationTime = LocalDateTime.parse(expirationTimeStr);
                if (LocalDateTime.now().isAfter(expirationTime)) {
                    return ResponseEntity.badRequest().body(
                        Map.of("status", "FAILED", "error", "QR code has expired. Please request a new QR code.")
                    );
                }
            }
    
            // Step 3: Extract and create PaymentRequest from the qrData
            PaymentRequest paymentRequest = new PaymentRequest();
            paymentRequest.setAmount(Double.valueOf(qrData.get("totalAmount").toString()));
            paymentRequest.setCurrency(qrData.get("currency").toString().toLowerCase().trim());
            paymentRequest.setSellerId(Long.valueOf(qrData.get("userId").toString()));
    
            // Step 4: Get the current user (buyer)
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            Users buyer = userService.findByUsername(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("Buyer not found"));
    
            // Step 5: Get the seller
            Users seller = usersRepository.getUsersById(paymentRequest.getSellerId())
                    .orElseThrow(() -> new RuntimeException("Seller not found"));
    
                  // Step 6: Convert amount to Naira using live exchange rates
                  Map<String, Double> exchangeRates = currencyService.getExchangeRates("NGN");
                  String currency = paymentRequest.getCurrency().toLowerCase();
                  
                  String currencyKey = "ngn_"+ currency;
                  if (!exchangeRates.containsKey(currencyKey)) {
                      throw new RuntimeException("Unsupported currency: " + currency);
                  }
      
                  Double exchangeRate = exchangeRates.get(currencyKey);
                  Double amountInNaira = paymentRequest.getAmount() / exchangeRate;
      
                  // Validate the converted amount
                  if (amountInNaira <= 0) {
                      throw new RuntimeException("Invalid amount after conversion");
                  }
                  if (buyer.getBalance() < amountInNaira) {
                    return ResponseEntity.badRequest().body(
                        Map.of(
                            "status", "FAILED",
                            "error", "Insufficient balance",
                            "required", amountInNaira,
                            "available", buyer.getBalance(),
                            "currency", "NGN"
                        )
                    );
                }    
            // Step 7: Process payment (deduct from buyer, add to seller)
            userService.processPayment(buyer, seller, amountInNaira);
    
            // Step 8: Record transaction
            Transaction transaction = transactionService.processPayment(
                    buyer.getId(),
                    seller.getId(),
                    amountInNaira,
                    currency + "_TO_NGN"
            );
        
    
            // Step 9: Return success response
            Map<String, Object> response = new HashMap<>();
            response.put("status", "SUCCESS");
            response.put("transactionId", transaction.getId());
            response.put("originalAmount", paymentRequest.getAmount());
            response.put("originalCurrency", currency);
            response.put("convertedAmount", amountInNaira);
            response.put("exchangeRate", exchangeRate);
            response.put("baseCurrency", "NGN");
            response.put("timestamp", LocalDateTime.now().toString());

            smsService.notifyTransactionParties(
            buyer.getPhoneNumber(),
            seller.getPhoneNumber(),
            amountInNaira,
            buyer.getBalance(),
            seller.getBalance()
        );

            return ResponseEntity.ok(response);
    
        } catch (Exception e) {
            log.error("Payment failed: ", e);
            return ResponseEntity.badRequest().body(
                    Map.of("status", "FAILED", "error", e.getMessage())
            );
        }
    }
    @GetMapping("/transactions")
public ResponseEntity<?> getUserTransactions(
    @RequestParam(required = false) String filter, 
    @RequestParam(required = false, defaultValue = "10") int limit) {
    
    try {
        // Get current authenticated user
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Users currentUser = userService.findByUsername(authentication.getName())
            .orElseThrow(() -> new RuntimeException("User not found"));

        // Get transactions based on filter
        List<Transaction> transactions = transactionService.getTransactionHistory(currentUser.getId(),5);
        
        // Calculate totals
        Double incomingTotal = transactionService.getIncomingTotal(currentUser.getId());
        Double outgoingTotal = transactionService.getOutgoingTotal(currentUser.getId());
        
        // Convert transactions to DTOs with formatted data
        List<Map<String, Object>> formattedTransactions = transactions.stream()
            .limit(limit)
            .map(transaction -> {
                Map<String, Object> dto = new HashMap<>();
                dto.put("id", transaction.getId());
                
                // Determine if transaction is incoming or outgoing
                boolean isIncoming = transaction.getSeller().getId() == currentUser.getId();
                // Format amount with currency symbol
                dto.put("amount", formatAmount(transaction.getAmount()));
                dto.put("rawAmount", transaction.getAmount());
                dto.put("type", isIncoming ? "INCOMING" : "OUTGOING");
                dto.put("status", transaction.getStatus());
                
                // Format timestamp
                dto.put("timestamp", transaction.getTimestamp().format(
                    DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm:ss")
                ));
                
                // Add other party details
                Users otherParty = isIncoming ? transaction.getBuyer() : transaction.getSeller();
                dto.put("otherPartyId", otherParty.getId());
                dto.put("otherPartyUsername", otherParty.getUsername());
                
                return dto;
            })
            .collect(Collectors.toList());

        // Build response
        Map<String, Object> response = new HashMap<>();
        response.put("transactions", formattedTransactions);
        response.put("totalIncoming", formatAmount(incomingTotal));
        response.put("totalOutgoing", formatAmount(outgoingTotal));
        response.put("balance", formatAmount(currentUser.getBalance()));
        response.put("count", formattedTransactions.size());
        response.put("currency", "NGN");

        return ResponseEntity.ok(response);

    } catch (Exception e) {
        log.error("Error fetching user transactions: ", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse("Failed to fetch transactions: " + e.getMessage()));
    }
}
    @PutMapping("/role")
public ResponseEntity<?> updateRole(@RequestParam String username, @RequestParam String role) {
    try {
        Users updatedUser = userService.updateUserRole(username, role);
        return ResponseEntity.ok("User role updated to: " + updatedUser.getRole());
    } catch (IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    } catch (RuntimeException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }
}
@PutMapping("/update_bal/{id}")
public double updateUserBalance(@RequestParam String username, @RequestParam double amount) {
    try {
        Users user = userService.updateUserBalance(username, amount);
        return user.getBalance();
    } catch (IllegalArgumentException e) {
        throw new RuntimeException("Invalid amount: " + e.getMessage());
    } catch (RuntimeException e) {
        throw new RuntimeException("User not found: " + e.getMessage());
    }

}
@PutMapping("/update-bank-details")
public ResponseEntity<?> updateBankDetails(
        @RequestParam String accountNumber,
        @RequestParam String bankName) {
    try {
        // Get current authenticated user
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        Users updatedUser = userService.updateBankDetails(username, accountNumber, bankName);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("message", "Bank details updated successfully");
        response.put("accountNumber", updatedUser.getAccountNumber());
        response.put("bankName", updatedUser.getBankName());

        return ResponseEntity.ok(response);

    } catch (IllegalArgumentException e) {
        log.error("Invalid bank details: {}", e.getMessage());
        return ResponseEntity.badRequest()
            .body(new ErrorResponse("Invalid bank details: " + e.getMessage()));
    } catch (Exception e) {
        log.error("Failed to update bank details: ", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse("Failed to update bank details: " + e.getMessage()));
    }
}


// Helper method to format amount
private String formatAmount(Double amount) {
    return String.format("₦%.2f", amount);
}
@GetMapping("/balance")
public ResponseEntity<?> getUserBalance() {
    try {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Users currentUser = userService.findByUsername(authentication.getName())
            .orElseThrow(() -> new RuntimeException("User not found"));

        Map<String, Object> response = new HashMap<>();
        response.put("balance", currentUser.getBalance());
        response.put("formattedBalance", String.format("₦%.2f", currentUser.getBalance()));
        response.put("timestamp", LocalDateTime.now().toString());

        return ResponseEntity.ok(response);
    } catch (Exception e) {
        log.error("Error fetching user balance: ", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse("Failed to fetch balance: " + e.getMessage()));
    }
}
@GetMapping("/transaction-chart-data")
public ResponseEntity<?> getTransactionChartData() {
    try {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Users currentUser = userService.findByUsername(authentication.getName())
            .orElseThrow(() -> new RuntimeException("User not found"));

        Map<String, Object> chartData = transactionService.getTransactionChartData(currentUser.getId());
        
        return ResponseEntity.ok(chartData);
    } catch (Exception e) {
        log.error("Error fetching chart data: ", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse("Failed to fetch chart data: " + e.getMessage()));
    }
}
@PostMapping("/forgot-password")
public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> request) {
    String email = request.get("email");
    
    try {
        // Check if email exists in database
        Optional<Users> user = usersRepository.findByEmail(email);
        if (user.isEmpty()) {
            return ResponseEntity.badRequest()
                .body(Map.of("message", "No account found with this email address."));
        }

        // Generate reset token
        String token = UUID.randomUUID().toString();
        
        // Save token with expiry (1 hour from now)
        user.get().setResetToken(token);
        user.get().setResetTokenExpiry(LocalDateTime.now().plusHours(1));
        userService.saveUser(user.get());

        // Create reset link
        String resetLink = "http://localhost:8080/reset-password?token=" + token;
        
        // Create HTML email content
        String htmlContent = 
            "<div style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e0e0e0; border-radius: 10px;'>" +
            "<h2 style='color: #4CAF50;'>XChange Password Reset</h2>" +
            "<p>We received a request to reset your XChange account password. Click the button below to reset your password:</p>" +
            "<div style='text-align: center; margin: 25px 0;'>" +
            "<a href='" + resetLink + "' style='background-color: #4CAF50; color: white; padding: 12px 24px; text-decoration: none; border-radius: 4px; font-weight: bold;'>Reset Password</a>" +
            "</div>" +
            "<p>This link will expire in 1 hour.</p>" +
            "<p>If you didn't request this password reset, please ignore this email or contact support if you have concerns.</p>" +
            "<p>Thank you,<br>The XChange Team</p>" +
            "</div>";

        // Send email with HTML content
        emailService.sendEmail(email, "Password Reset Request", htmlContent);

        return ResponseEntity.ok(Map.of("message", "Reset link sent successfully, Please check your inbox and follow the instructions."));

    } catch (Exception e) {
        return ResponseEntity.internalServerError()
            .body(Map.of("message", "Error sending reset link: " + e.getMessage()));
    }
}
@PostMapping("/reset-password")
public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> request) {
    String token = request.get("token");
    String newPassword = request.get("password");
    
    try {
        // Use the UserService resetPassword method
        Users updatedUser = userService.resetPassword(token, newPassword);
        
        return ResponseEntity.ok(Map.of(
            "message", "Password reset successful",
            "username", updatedUser.getUsername()
        ));
    } catch (IllegalArgumentException e) {
        // Handle password validation errors
        return ResponseEntity.badRequest()
            .body(Map.of("message", e.getMessage()));
    } catch (RuntimeException e) {
        // Handle token invalid/expired errors
        return ResponseEntity.badRequest()
            .body(Map.of("message", e.getMessage()));
    } catch (Exception e) {
        // Handle other unexpected errors
        return ResponseEntity.internalServerError()
            .body(Map.of("message", "Error resetting password: " + e.getMessage()));
    }
}
}
