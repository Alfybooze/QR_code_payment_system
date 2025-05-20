package com.Alfy.xchange_Updated.Services;

import com.Alfy.xchange_Updated.Models.Users;
import com.Alfy.xchange_Updated.Repositories.UsersRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Transactional
public class UserService implements UserDetailsService {
    public static final String ROLE_BUYER = "ROLE_BUYER";
    public static final String ROLE_SELLER = "ROLE_SELLER";
    private final UsersRepository usersRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public UserService(UsersRepository usersRepository, PasswordEncoder passwordEncoder) {
        this.usersRepository = usersRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Users addUser(Users user) {
        validateNewUser(user);
        prepareNewUser(user);
        return usersRepository.save(user);
    }

    private void validateNewUser(Users user) {
        if (usersRepository.findByEmail(user.getEmail()).isPresent()) {
            throw new RuntimeException("User with this email already exists");
        }
        if (usersRepository.findByUsername(user.getUsername()).isPresent()) {
            throw new RuntimeException("Username already taken");
        }
    }

    @SuppressWarnings("null")
    private void prepareNewUser(Users user) {
        // Set default balance
        if (user.getBalance() == null) {
            user.setBalance(0.0);
        }
    
        // Encode password
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        
        // Validate and set role
        String role = user.getRole();
        if (role == null || role.isEmpty()) {
            user.setRole("BUYER"); // Remove ROLE_ prefix, it's added in getAuthorities
        } else if (!isValidRole(role)) {
            throw new IllegalArgumentException("Invalid role. Must be either BUYER or SELLER");
        }
        
        // Store role without ROLE_ prefix
        role = role.replace("ROLE_", "").toUpperCase();
        user.setRole(role);
    }

    private boolean isValidRole(String role) {
        String normalizedRole = role.replace("ROLE_", "").toUpperCase();
        return normalizedRole.equals("BUYER") || normalizedRole.equals("SELLER");
    }

    public Users updateUserRole(String username, String newRole) {
        if (!isValidRole(newRole)) {
            throw new IllegalArgumentException("Invalid role. Must be either BUYER or SELLER");
        }

        Users user = usersRepository.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        user.setRole(newRole);
        return usersRepository.save(user);
    }
    @Transactional
public void processPayment(Users buyer, Users seller, double amount) {
    // Validate the transaction
    if (amount <= 0) {
        throw new IllegalArgumentException("Payment amount must be greater than 0");
    }

    // Check if buyer has sufficient balance
    if (buyer.getBalance() < amount) {
        throw new RuntimeException("Insufficient balance to complete transaction");
    }

    try {
        // Deduct from buyer's balance
        double newBuyerBalance = buyer.getBalance() - amount;
        buyer.setBalance(newBuyerBalance);
        usersRepository.save(buyer);

        // Add to seller's balance
        double newSellerBalance = seller.getBalance() + amount;
        seller.setBalance(newSellerBalance);
        usersRepository.save(seller);

        log.info("Payment processed successfully: {} transferred from {} to {}", 
            amount, buyer.getUsername(), seller.getUsername());

    } catch (Exception e) {
        log.error("Error processing payment: ", e);
        throw new RuntimeException("Failed to process payment: " + e.getMessage());
    }
}

    public Optional<Users> findByEmail(String email) {
        return usersRepository.findByEmail(email);
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Users user = usersRepository.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        
        return user; // Return Users object directly since it implements UserDetails
    }

    public Optional<Users> findByUsername(String username) {
        return usersRepository.findByUsername(username);
    }

    public boolean validateCredentials(String username, String password) {
        return usersRepository.findByUsername(username)
            .map(user -> passwordEncoder.matches(password, user.getPassword()))
            .orElse(false);
    }

    public Users updateUserBalance(String username, Double newBalance) {
        Users user = usersRepository.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("User not found"));
        user.setBalance(newBalance);
        return usersRepository.save(user);
    }

    //Enter Bank Details
    @Transactional
public Users updateBankDetails(String username, String accountNumber, String bankName) {
    Users user = usersRepository.findByUsername(username)
        .orElseThrow(() -> new RuntimeException("User not found"));
    
    // Validate account number format (assuming 10 digits)
    if (!accountNumber.matches("\\d{10}")) {
        throw new IllegalArgumentException("Account number must be 10 digits");
    }
    
    // Validate bank name
    if (bankName == null || bankName.trim().isEmpty()) {
        throw new IllegalArgumentException("Bank name cannot be empty");
    }

    user.setAccountNumber(accountNumber);
    user.setBankName(bankName);
    
    return usersRepository.save(user);
}
public void saveUser(Users user) {
    usersRepository.save(user);
}
    @Transactional
    public Users resetPassword(String token, String newPassword) {
        // Validate token and get user
        Users user = usersRepository.findByResetToken(token)
            .orElseThrow(() -> new RuntimeException("Invalid reset token"));

        // Check if token is expired
        if (user.getResetTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Reset token has expired");
        }

        // Validate new password
        validatePassword(newPassword);

        // Update password and clear reset token
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setResetToken(null);
        user.setResetTokenExpiry(null);

        // Save and return updated user
        return usersRepository.save(user);
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters long");
        }
        // if (!password.matches(".*[A-Z].*")) {
        //     throw new IllegalArgumentException("Password must contain at least one uppercase letter");
        // }
        // if (!password.matches(".*[a-z].*")) {
        //     throw new IllegalArgumentException("Password must contain at least one lowercase letter");
        // }
        // if (!password.matches(".*\\d.*")) {
        //     throw new IllegalArgumentException("Password must contain at least one number");
        // }
    }
    
}