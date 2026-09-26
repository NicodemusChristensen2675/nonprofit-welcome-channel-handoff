package org.learningaid.onboarding;

import org.learningaid.onboarding.OnboardingCoordinator.Channel;
import org.learningaid.onboarding.OnboardingCoordinator.Enrollment;
import org.learningaid.onboarding.OnboardingCoordinator.Notice;

public final class NonprofitWelcomeService {
    private NonprofitWelcomeService() {}

    public static void main(String[] args) {
        InfraiConfig config = InfraiConfig.fromEnvironment();
        OnboardingCoordinator coordinator = new OnboardingCoordinator(new InfraiHttpGateway(config));

        Enrollment learnerMentor = new Enrollment(
                required("MEMBER_USER_ID"),
                env("MEMBER_NAME", "Avery"),
                required("MEMBER_EMAIL"),
                required("MEMBER_PHONE"),
                Channel.valueOf(env("SIGNUP_CHANNEL", "EMAIL").toUpperCase()),
                Notice.valueOf(env("NOTICE_TYPE", "VOLUNTEER_REMINDER").toUpperCase()));

        OnboardingCoordinator.Delivery result = coordinator.onboard(learnerMentor);
        System.out.printf("{\"channel\":\"%s\",\"message_id\":\"%s\",\"summary\":\"%s\"}%n",
                result.channel().name().toLowerCase(), result.messageId(), result.summary());
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Set " + name);
        return value;
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
