package panscience.chatapp.dto;

public class AuthResponse {
    private String message;
    private String token;
    private String email;
    private String name;
    private Long userId;

    public AuthResponse() {}

    public AuthResponse(String message) {
        this.message = message;
    }

    public AuthResponse(String message, String token, String email, String name, Long userId) {
        this.message = message;
        this.token = token;
        this.email = email;
        this.name = name;
        this.userId = userId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}

