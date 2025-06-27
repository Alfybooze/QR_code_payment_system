package com.Alfy.xchange_Updated.Controllers;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.Alfy.xchange_Updated.Models.Transaction;
import com.Alfy.xchange_Updated.Models.Users;
import com.Alfy.xchange_Updated.Services.CurrencyService;
import com.Alfy.xchange_Updated.Services.JwtService;
import com.Alfy.xchange_Updated.Services.TransactionService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@Controller
public class Webpage_Controller {
  private final JwtService jwtService;
@Autowired
private CurrencyService currencyService;
@Autowired
private TransactionService transactionService;

    @Autowired
    public Webpage_Controller(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @GetMapping("/")
    public String landingpage() {
        return "Landing";
    }

    @GetMapping("/register")
    public String signup() {
        return "Register";
    }

    @GetMapping("/login")
    public String login() {
        return "Login";
    }

  @GetMapping("/dashboard")
public String dashboard(Model model, HttpServletRequest request) {
    try {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("jwt_token") == null) {
            log.debug("No session or JWT token found");
            return "redirect:/login";
        }

        String jwtToken = (String) session.getAttribute("jwt_token");

        if (!jwtService.isTokenValid(jwtToken)) {
            log.debug("Invalid JWT token");
            session.removeAttribute("jwt_token");
            return "redirect:/login";
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            log.debug("No authentication found in security context");
            return "redirect:/login";
        }

        Users user = (Users) auth.getPrincipal();
        String username = jwtService.extractUsername(jwtToken);

        if (!user.getUsername().equals(username)) {
            log.warn("Token username mismatch");
            return "redirect:/login";
        }

        // Get exchange rates (assuming NGN is the source currency)
        Map<String, Double> rates = currencyService.getExchangeRates("ngn");

        // Format exchange rates as "X NGN = 1 Currency"
        Map<String, String> formattedRates = new HashMap<>();
        for (Map.Entry<String, Double> entry : rates.entrySet()) {
            // Convert the response key "ngn_usd" to "USD"
            String currency = entry.getKey().replace("ngn_", "").toUpperCase();
            double value = 1 / entry.getValue(); // Inverting the exchange rate

            formattedRates.put(currency, String.format("%.2f NGN = 1 %s", value, currency));
        }

        // Add user details and exchange rate to the model
        model.addAttribute("user", user);
        model.addAttribute("id", user.getId());
        model.addAttribute("username", user.getUsername());
        model.addAttribute("email", user.getEmail());
        model.addAttribute("role", user.getRole());
        model.addAttribute("balance", user.getBalance());
        model.addAttribute("jwt_token", jwtToken);
        model.addAttribute("formattedRates", formatRatesForDisplay(rates));
        model.addAttribute("exchangeRates", rates); // Add formatted rates to the model
        model.addAttribute("AccountNumber", user.getAccountNumber());
        model.addAttribute("BankName", user.getBankName());


        
        log.debug("Dashboard loaded successfully for user: {}", username);
        return "Dashboard";

    } catch (Exception e) {
        log.error("Error loading dashboard", e);
        return "redirect:/login";
    }
}private Map<String, String> formatRatesForDisplay(Map<String, Double> rates) {
    Map<String, String> formatted = new HashMap<>();
    try {
        if (rates.containsKey("ngn_usd")) {
            formatted.put("USD", String.format("₦%.2f = $1.00", 1/rates.get("ngn_usd")));
        }
        if (rates.containsKey("ngn_eur")) {
            formatted.put("EUR", String.format("₦%.2f = €1.00", 1/rates.get("ngn_eur")));
        }
        if (rates.containsKey("ngn_gbp")) {
            formatted.put("GBP", String.format("₦%.2f = £1.00", 1/rates.get("ngn_gbp")));
        }
        if (rates.containsKey("ngn_btc")) {
            formatted.put("BTC", String.format("₦%.2f = ₿1.00", 1/rates.get("ngn_btc")));
        }
        if (rates.containsKey("ngn_eth")) {
            formatted.put("ETH", String.format("₦%.2f = Ξ1.00", 1/rates.get("ngn_eth")));
        }
    } catch (Exception e) {
        log.error("Error formatting rates: {}", e.getMessage());
    }
    return formatted;
}

@GetMapping("/transactions")
public String transactionHistory(Model model, Authentication authentication,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "20") int size,
                               @RequestParam(required = false) String year,
                               @RequestParam(required = false) String month) {
    
    if (authentication == null) {
        return "redirect:/login";
    }
   
    Users user = (Users) authentication.getPrincipal();
   
    // Create pageable
    Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());
   
    Page<Transaction> transactions;
    
    // Check if year and month parameters are provided for filtering
    if (year != null && !year.isEmpty() && month != null && !month.isEmpty()) {
        try {
            int yearInt = Integer.parseInt(year);
            int monthInt = Integer.parseInt(month);
            
            // Get transactions for specific month and year
            transactions = transactionService.getTransactionsByUserAndMonth(
                user.getId(), yearInt, monthInt, pageable
            );
            
            // Add filter parameters to model for maintaining state in view
            model.addAttribute("selectedYear", year);
            model.addAttribute("selectedMonth", month);
            
        } catch (IllegalArgumentException e) {
            // If parsing fails, fall back to all transactions
            transactions = transactionService.getAllTransactionsByUser(user.getId(), pageable);
            model.addAttribute("filterError", "Invalid year or month format");
        }
    } else if (year != null && !year.isEmpty()) {
        // If only year is provided, get transactions for the entire year
        try {
            int yearInt = Integer.parseInt(year);
            transactions = transactionService.getTransactionsByUserAndYear(
                user.getId(), yearInt, pageable
            );
            model.addAttribute("selectedYear", year);
        } catch (NumberFormatException e) {
            transactions = transactionService.getAllTransactionsByUser(user.getId(), pageable);
            model.addAttribute("filterError", "Invalid year format");
        }
    } else {
        // No filters applied, get ALL transactions for the user
        transactions = transactionService.getAllTransactionsByUser(user.getId(), pageable);
    }
   
    // Add common attributes to model
    model.addAttribute("user", user);
    model.addAttribute("username", user.getUsername());
    model.addAttribute("id", user.getId());
    model.addAttribute("balance", user.getBalance());
    model.addAttribute("transactions", transactions);
    model.addAttribute("currentPage", page);
    model.addAttribute("totalPages", transactions.getTotalPages());
    model.addAttribute("totalElements", transactions.getTotalElements());
   
    return "Transactions";
}
    @PostMapping("/logout")
public String logout(HttpServletRequest request) {
    try {
        // Get the session
        HttpSession session = request.getSession(false);
        if (session != null) {
            // Remove JWT token from session
            session.removeAttribute("jwt_token");
            // Invalidate the session
            session.invalidate();
        }

        // Clear the security context
        SecurityContextHolder.clearContext();
        
        log.info("User successfully logged out");
        return "redirect:/login?logout=success";
        
    } catch (Exception e) {
        log.error("Error during logout", e);
        return "redirect:/login";
    }
}
@GetMapping("/generate-qr")
public String generateQR(Model model, Authentication authentication) {
    if (authentication != null) {
        Users user = (Users) authentication.getPrincipal();
        model.addAttribute("user", user);
    }
    return "GenerateQR";
}

@GetMapping("/scan-pay")
public String scanPay(Model model, Authentication authentication) {
    if (authentication != null) {
        Users user = (Users) authentication.getPrincipal();
        model.addAttribute("user", user);
    }
    return "Scan_pay";
}
@GetMapping("/forgot_pass")
public String forgotPass() {
    return "Forgot_pass";
}
@GetMapping("/reset-password")
public String resetPassword() {
    return "Reset_password";
}
}
