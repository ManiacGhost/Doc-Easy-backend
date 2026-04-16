package panscience.chatapp.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

@Service
public class OtpService {

    private static final Logger logger = LoggerFactory.getLogger(OtpService.class);
    private static final String OTP_PREFIX = "otp:";
    private final RedisTemplate<String, String> redisTemplate;
    private final SecureRandom random = new SecureRandom();

    @Value("${app.otp.expiration}")
    private Long otpExpiration; // milliseconds

    public OtpService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public String generateOtp(String email) {
        String otp = String.format("%06d", random.nextInt(1000000));
        String key = OTP_PREFIX + email;

        redisTemplate.opsForValue().set(key, otp, otpExpiration, TimeUnit.MILLISECONDS);

        logger.info("OTP generated for email: {} (expires in {} ms)", email, otpExpiration);
        return otp;
    }

    public boolean verifyOtp(String email, String otp) {
        String key = OTP_PREFIX + email;
        String storedOtp = redisTemplate.opsForValue().get(key);

        if (storedOtp != null && storedOtp.equals(otp)) {
            redisTemplate.delete(key);
            logger.info("OTP verified successfully for email: {}", email);
            return true;
        }

        logger.warn("OTP verification failed for email: {}", email);
        return false;
    }

    public void deleteOtp(String email) {
        String key = OTP_PREFIX + email;
        redisTemplate.delete(key);
        logger.info("OTP deleted for email: {}", email);
    }
}

