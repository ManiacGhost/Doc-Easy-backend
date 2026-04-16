package panscience.chatapp.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import panscience.chatapp.dto.*;
import panscience.chatapp.entity.User;
import panscience.chatapp.exception.BadRequestException;
import panscience.chatapp.repository.UserRepository;

@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final OtpService otpService;
    private final EmailService emailService;
    private final AuthenticationManager authenticationManager;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            OtpService otpService,
            EmailService emailService,
            AuthenticationManager authenticationManager
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.otpService = otpService;
        this.emailService = emailService;
        this.authenticationManager = authenticationManager;
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email already registered");
        }

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        userRepository.save(user);

        logger.info("User registered successfully: {}", request.getEmail());
        return new AuthResponse("Registration successful. Please login.");
    }

    public AuthResponse login(LoginRequest request) {
        // Authenticate user
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadRequestException("User not found"));

        // Generate and send OTP
        String otp = otpService.generateOtp(request.getEmail());
        emailService.sendOtpEmail(request.getEmail(), otp);

        logger.info("OTP sent to user: {}", request.getEmail());
        return new AuthResponse("OTP sent to your email. Please verify to complete login.");
    }

    public AuthResponse verifyOtp(VerifyOtpRequest request) {
        if (!otpService.verifyOtp(request.getEmail(), request.getOtp())) {
            throw new BadRequestException("Invalid or expired OTP");
        }

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadRequestException("User not found"));

        String jwtToken = jwtService.generateToken(user);

        logger.info("User logged in successfully: {}", request.getEmail());
        return new AuthResponse(
                "Login successful",
                jwtToken,
                user.getEmail(),
                user.getName(),
                user.getId()
        );
    }
}

