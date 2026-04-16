package panscience.chatapp;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import panscience.chatapp.service.OpenAiService;
import panscience.chatapp.service.MediaProcessingService;

/**
 * This is a test utility to verify your configuration is correct.
 * Run this after starting the application to see initialized values.
 */
public class ConfigurationVerifier {

    public static void main(String[] args) {
        ApplicationContext context = SpringApplication.run(ChatappApplication.class, args);

        // Get environment
        Environment env = context.getEnvironment();

        System.out.println("\n========================================");
        System.out.println("CONFIGURATION VERIFICATION");
        System.out.println("========================================\n");

        System.out.println("AWS Configuration:");
        System.out.println("  Access Key: " + (env.getProperty("app.aws.access-key") != null ? "SET" : "NOT SET"));
        System.out.println("  Secret Key: " + (env.getProperty("app.aws.secret-key") != null ? "SET" : "NOT SET"));
        System.out.println("  Region: " + env.getProperty("app.aws.region"));
        System.out.println("  Bucket: " + env.getProperty("app.aws.bucket-name"));

        System.out.println("\nCloudinary Configuration:");
        System.out.println("  Cloud Name: " + env.getProperty("app.cloudinary.cloud-name"));
        System.out.println("  API Key: " + (env.getProperty("app.cloudinary.api-key") != null ? "SET" : "NOT SET"));
        System.out.println("  API Secret: " + (env.getProperty("app.cloudinary.api-secret") != null ? "SET" : "NOT SET"));
        System.out.println("  Upload Preset: " + env.getProperty("app.cloudinary.upload-preset"));

        System.out.println("\nOpenAI Configuration:");
        System.out.println("  API Key: " + (env.getProperty("app.ai.openai.api-key") != null && !env.getProperty("app.ai.openai.api-key").isEmpty() ? "SET" : "NOT SET"));
        System.out.println("  Base URL: " + env.getProperty("app.ai.openai.base-url"));
        System.out.println("  Chat Model: " + env.getProperty("app.ai.openai.model"));
        System.out.println("  Vision Model: " + env.getProperty("app.ai.openai.vision-model"));
        System.out.println("  Chat Endpoint: " + env.getProperty("app.ai.openai.chat-endpoint"));

        System.out.println("\nDatabase Configuration:");
        System.out.println("  URL: " + env.getProperty("spring.datasource.url"));
        System.out.println("  Username: " + env.getProperty("spring.datasource.username"));
        System.out.println("  Password: " + (env.getProperty("spring.datasource.password") != null ? "SET" : "NOT SET"));

        System.out.println("\nService Beans:");
        try {
            OpenAiService openAiService = context.getBean(OpenAiService.class);
            System.out.println("  OpenAiService: INITIALIZED");
            System.out.println("    - Configured: " + openAiService.isConfigured());
        } catch (Exception e) {
            System.out.println("  OpenAiService: ERROR - " + e.getMessage());
        }

        try {
            MediaProcessingService mediaProcessingService = context.getBean(MediaProcessingService.class);
            System.out.println("  MediaProcessingService: INITIALIZED");
            System.out.println("    - Configured: " + mediaProcessingService.isConfigured());
        } catch (Exception e) {
            System.out.println("  MediaProcessingService: ERROR - " + e.getMessage());
        }

        System.out.println("\n========================================\n");
    }
}

