package org.learningaid.onboarding;

import org.learningaid.onboarding.OnboardingCoordinator.Channel;
import org.learningaid.onboarding.OnboardingCoordinator.Enrollment;
import org.learningaid.onboarding.OnboardingCoordinator.Notice;

public final class OnboardingCoordinatorTest {
    public static void main(String[] args) {
        emailSignupFallsBackToSmsWhenEmailIsSuppressed();
        System.out.println("PASS: suppressed email hands the welcome directly to SMS");
    }

    private static void emailSignupFallsBackToSmsWhenEmailIsSuppressed() {
        RecordingGateway gateway = new RecordingGateway();
        OnboardingCoordinator coordinator = new OnboardingCoordinator(gateway);
        Enrollment input = new Enrollment("member-42", "Mina", "mina@example.org", "+15550102030",
                Channel.EMAIL, Notice.VOLUNTEER_REMINDER);

        OnboardingCoordinator.Delivery result = coordinator.onboard(input);

        check(result.channel() == Channel.SMS, "expected SMS fallback");
        check("sms-message-7".equals(result.messageId()), "expected SMS message id");
        check(gateway.emailSends == 0, "suppressed email must not be sent");
        check(gateway.smsSends == 1, "fallback must be sent once");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class RecordingGateway implements InfraiGateway {
        int emailSends;
        int smsSends;

        public boolean hasConsent(String userId, String category) { return true; }
        public boolean isEmailSuppressed(String email) { return true; }
        public boolean isSmsSuppressed(String phone) { return false; }
        public String sendEmail(String email, String subject, String text) { emailSends++; return "email-message-3"; }
        public String sendSms(String phone, String text) { smsSends++; return "sms-message-7"; }
    }
}
