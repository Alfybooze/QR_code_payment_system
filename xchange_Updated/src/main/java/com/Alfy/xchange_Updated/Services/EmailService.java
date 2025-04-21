package com.Alfy.xchange_Updated.Services;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    public void sendVerificationEmail(String to, String code) throws MessagingException {
        log.info("Preparing to send verification email to: {}", to);
        
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            
            helper.setTo(to);
            helper.setSubject("Your XChange Email Verification Code");
            helper.setFrom("noreply@xchange.com"); // Add this line
            
            String htmlContent = 
                "<div style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e0e0e0; border-radius: 10px;'>" +
                "<h2 style='color: #4CAF50;'>XChange Email Verification</h2>" +
                "<p>Thank you for registering with XChange. Please use the verification code below to complete your registration:</p>" +
                "<div style='background-color: #f5f5f5; padding: 15px; border-radius: 8px; text-align: center; margin: 20px 0;'>" +
                "<h1 style='font-size: 36px; letter-spacing: 5px; margin: 0; color: #333;'>" + code + "</h1>" +
                "</div>" +
                "<p>This code will expire in 3 minutes.</p>" +
                "<p>If you didn't request this code, please ignore this email.</p>" +
                "<p>Thank you,<br>The XChange Team</p>" +
                "</div>";
                
            helper.setText(htmlContent, true);
            
            mailSender.send(message);
            log.info("Verification email sent successfully to: {}", to);
        } catch (MessagingException e) {
            log.error("Failed to send verification email to: " + to, e);
            throw new MessagingException("Failed to send verification email: " + e.getMessage());
        }
    }
    public void reset_password(){
        
    }
    public void sendEmail(String to, String subject, String content) throws MessagingException {
        log.info("Preparing to send email to: {}", to);
        
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setFrom("noreply@xchange.com");
            
            // Check if content is HTML
            boolean isHtml = content.contains("<") && content.contains(">");
            helper.setText(content, isHtml);
            
            mailSender.send(message);
            log.info("Email sent successfully to: {}", to);
        } catch (MessagingException e) {
            log.error("Failed to send email to: " + to, e);
            throw new MessagingException("Failed to send email: " + e.getMessage());
        }
    }
}
