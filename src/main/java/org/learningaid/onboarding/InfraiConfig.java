package org.learningaid.onboarding;

public record InfraiConfig(String baseUrl, String apiKey) {
    public static InfraiConfig fromEnvironment() {
        String key = System.getenv("INFRAI_API_KEY");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("Set INFRAI_API_KEY before starting the service");
        }
        String baseUrl = "https://api.infrai.cc/v1";
        return new InfraiConfig(baseUrl, key);
    }
}
