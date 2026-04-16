package panscience.chatapp.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String fromEmail;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendOtpEmail(String toEmail, String otp) {
        // ALWAYS log OTP to console for development
        logger.warn("===========================================");
        logger.warn("OTP for {}: {}", toEmail, otp);
        logger.warn("===========================================");

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("Your OTP for ChatApp Login");
            message.setText(String.format(
                "Hello,\n\n" +
                "Your OTP for ChatApp login is: %s\n\n" +
                "This OTP will expire in 10 minutes.\n\n" +
                "If you didn't request this, please ignore this email.\n\n" +
                "Best regards,\n" +
                "ChatApp Team",
                otp
            ));

            logger.info("Attempting to send OTP email to: {} from: {}", toEmail, fromEmail);
            mailSender.send(message);
            logger.info("✅ OTP email sent successfully to: {}", toEmail);

        } catch (Exception e) {
            logger.error("❌ Failed to send OTP email to: {}", toEmail, e);
            logger.error("Error details: {}", e.getMessage());
            logger.warn("⚠️  Please use the OTP from console logs above");
        }
    }
}

