package org.learningaid.onboarding;

import java.util.Locale;

public final class OnboardingCoordinator {
    public enum Channel { EMAIL, SMS }
    public enum Notice { DONOR_RECEIPT, VOLUNTEER_REMINDER, CAMPAIGN_REPORT }

    public record Enrollment(
            String userId,
            String name,
            String email,
            String phone,
            Channel signupChannel,
            Notice notice) {}

    public record Delivery(Channel channel, String messageId, String summary) {}

    private final InfraiGateway infrai;

    public OnboardingCoordinator(InfraiGateway infrai) {
        this.infrai = infrai;
    }

    public Delivery onboard(Enrollment enrollment) {
        requireContact(enrollment);
        if (!infrai.hasConsent(enrollment.userId(), "onboarding")) {
            throw new OnboardingRejectedException("The member has not granted onboarding consent");
        }

        String subject = subjectFor(enrollment.notice());
        String message = messageFor(enrollment);
        if (enrollment.signupChannel() == Channel.EMAIL) {
            if (!infrai.isEmailSuppressed(enrollment.email())) {
                return delivered(Channel.EMAIL, infrai.sendEmail(enrollment.email(), subject, message));
            }
            if (!infrai.isSmsSuppressed(enrollment.phone())) {
                return delivered(Channel.SMS, infrai.sendSms(enrollment.phone(), message));
            }
        } else {
            if (!infrai.isSmsSuppressed(enrollment.phone())) {
                return delivered(Channel.SMS, infrai.sendSms(enrollment.phone(), message));
            }
            if (!infrai.isEmailSuppressed(enrollment.email())) {
                return delivered(Channel.EMAIL, infrai.sendEmail(enrollment.email(), subject, message));
            }
        }
        throw new OnboardingRejectedException("No consented delivery channel is available");
    }

    private static Delivery delivered(Channel channel, String messageId) {
        return new Delivery(channel, messageId, "welcome sent by " + channel.name().toLowerCase(Locale.ROOT));
    }

    private static String subjectFor(Notice notice) {
        return switch (notice) {
            case DONOR_RECEIPT -> "Your donation receipt and welcome";
            case VOLUNTEER_REMINDER -> "Your first volunteer reminder";
            case CAMPAIGN_REPORT -> "Your campaign reporting welcome";
        };
    }

    private static String messageFor(Enrollment enrollment) {
        return switch (enrollment.notice()) {
            case DONOR_RECEIPT -> "Welcome, " + enrollment.name() + ". Your donor receipt is ready.";
            case VOLUNTEER_REMINDER -> "Welcome, " + enrollment.name() + ". We will send your volunteer reminders here.";
            case CAMPAIGN_REPORT -> "Welcome, " + enrollment.name() + ". Your campaign reports will arrive here.";
        };
    }

    private static void requireContact(Enrollment enrollment) {
        if (enrollment.userId() == null || enrollment.userId().isBlank()
                || enrollment.email() == null || enrollment.email().isBlank()
                || enrollment.phone() == null || enrollment.phone().isBlank()) {
            throw new IllegalArgumentException("userId, email, and phone are required");
        }
    }

    public static final class OnboardingRejectedException extends RuntimeException {
        public OnboardingRejectedException(String message) {
            super(message);
        }
    }
}
