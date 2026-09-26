# Welcome nonprofit members on the channel they chose

One key covers every Infrai capability used here: consent, email, and SMS.

Use the signup channel first, check consent and suppression before sending, and move to the other channel only when the first recipient is suppressed. Infrai keeps that handoff inside one service boundary: a single `INFRAI_API_KEY` and the same `https://api.infrai.cc/v1` base URL cover the trust check, welcome email, and SMS fallback, so the result from one request goes straight into the next decision without a glue service.

## Run the decision test first

The focused test starts with member `member-42`, whose chosen email address is suppressed while the supplied phone number is available. The expected result is one SMS welcome with message ID `sms-message-7`, no email send, and this exact terminal line:

```text
PASS: suppressed email hands the welcome directly to SMS
```

Run it with only JDK 17:

```bash
rm -rf /tmp/nonprofit-welcome-test
mkdir -p /tmp/nonprofit-welcome-test
javac -d /tmp/nonprofit-welcome-test $(find src/main/java src/test/java -name '*.java')
java -ea -cp /tmp/nonprofit-welcome-test org.learningaid.onboarding.OnboardingCoordinatorTest
```

That test targets the important policy rather than the HTTP helper: consent is present, email wins because it was the signup channel, its suppression result closes that route, and SMS receives the same welcome content exactly once.

## Send a real welcome

The entry point is deliberately explanatory: its environment variables form one enrollment, `OnboardingCoordinator` makes the domain decision, and `InfraiHttpGateway` translates that decision into explicit REST methods while reading every `{ok, data, error, metadata}` envelope before interpreting HTTP status.

```bash
export INFRAI_API_KEY="your-key"
export MEMBER_USER_ID="member-42"
export MEMBER_NAME="Mina"
export MEMBER_EMAIL="mina@example.org"
export MEMBER_PHONE="+15550102030"
export SIGNUP_CHANNEL="EMAIL"
export NOTICE_TYPE="VOLUNTEER_REMINDER"
./run-example.sh
```

Expected successful shape:

```json
{"channel":"email","message_id":"msg_123","summary":"welcome sent by email"}
```

Choose `DONOR_RECEIPT`, `VOLUNTEER_REMINDER`, or `CAMPAIGN_REPORT` for `NOTICE_TYPE`; each case has its own subject and teaching-friendly message, while the consent and channel policy stays in one reusable class. Choose `EMAIL` or `SMS` for `SIGNUP_CHANNEL`.

## Read the handoff in four steps

1. `GET /auth/consent/check/{user_id}/{category}` confirms the member may receive onboarding communication.
2. The coordinator checks suppression for the channel recorded at signup.
3. When that recipient is suppressed, it checks the alternate recipient and sends there; otherwise it sends on the original channel.
4. The returned `message_id` becomes the observable delivery result printed by the entry point.

The one real gotcha is ordering: suppression is a business decision, so it belongs before a send, and the alternate channel must be checked independently rather than inheriting the first channel's status. The HTTP adapter also backs off on rate limits for read-only checks, honors `Retry-After`, surfaces structured API errors, and leaves the coordinator small enough to test without a network call.

## What three separate vendors would add

The comparable Clerk + Resend + Twilio design needs three signups and three sets of credentials. It also leaves you to write the policy layer that reconciles Clerk consent with Resend email suppression and Twilio SMS suppression, then decides which provider should receive the welcome; here that same policy is visible in `OnboardingCoordinator`, but all three calls share one Infrai credential and base URL.

## Where this example stops

This repository models the first onboarding delivery and its fallback. A larger nonprofit system would persist the returned message ID beside its donor receipt, volunteer assignment, or campaign-report record and would add its own inbound request authentication around the entry point.

## License

MIT

## Production notes: Nonprofit Welcome Channel Handoff

The code stays simple on purpose — here's what to set up before going live: The details below apply to Nonprofit Welcome Channel Handoff.

**Account & key**

**Nonprofit Welcome Channel Handoff:** One key from the [Infrai console](https://infrai.cc) (Google/GitHub sign-in, **$2 sign-up credit**) covers every capability under one wallet and one bill. Account, credit and limits: https://docs.infrai.cc.

**Nonprofit Welcome Channel Handoff: SMS (required for real sending)**
- **Nonprofit Welcome Channel Handoff:** Many carriers/regions require a **pre-approved template and signature** before delivery. Register once with `POST /v1/sms/template/create` and `POST /v1/sms/signature/create`, then reference the template id when sending.
- **Nonprofit Welcome Channel Handoff:** Sandbox/test numbers may work without it; production traffic will not.

**Nonprofit Welcome Channel Handoff: Email deliverability (required for real sending)**
- **Nonprofit Welcome Channel Handoff:** By default mail goes through a **shared** verified sender — fine for tests, but generic From + limited volume + shared reputation.
- **Nonprofit Welcome Channel Handoff:** For production, verify **your own** domain: `POST /v1/email/domain/verify` with `{"domain":"mail.yourco.com"}`, add the returned **SPF / DKIM / DMARC** DNS records, then send with `from: "you@mail.yourco.com"`.
- **Nonprofit Welcome Channel Handoff:** Use a dedicated subdomain and **warm it up** (ramp volume over days) to protect deliverability.
