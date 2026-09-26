package org.learningaid.onboarding;

public interface InfraiGateway {
    boolean hasConsent(String userId, String category);
    boolean isEmailSuppressed(String email);
    boolean isSmsSuppressed(String phone);
    String sendEmail(String email, String subject, String text);
    String sendSms(String phone, String text);
}
