import java.util.*;
import java.util.concurrent.*;

/*
=========================== REQUIREMENTS ===========================

Functional Requirements
1. System should generate a CAPTCHA for a user session.
2. User must verify the CAPTCHA before OTP is generated.
3. System should generate and send a 6-digit OTP to a phone number.
4. User should be able to verify the OTP.
5. OTP should expire after a fixed duration (5 minutes).
6. Multiple users/sessions should be supported concurrently.

Non-Functional Requirements
1. Thread-safe implementation since multiple users may request OTP simultaneously.
2. Fast lookup for OTP and CAPTCHA verification.
3. OTP expiration handling.

=========================== CORE ENTITIES ===========================

1. OtpCaptchaService
   Main service responsible for generating CAPTCHA, sending OTP,
   storing them, and verifying them.

2. OtpEntry
   Wrapper class that stores OTP along with the timestamp so that
   expiration logic can be handled.

3. captchaStore
   Map storing sessionId → captcha mapping.

4. otpStore
   Map storing phoneNumber → OTP entry mapping.

=====================================================================
*/

public class OtpCaptchaService {

    // Internal class to store OTP along with the time it was generated
    static class OtpEntry {
        String otp;
        long timestamp;

        OtpEntry(String otp) {
            this.otp = otp;

            // Store the time when OTP was generated
            this.timestamp = System.currentTimeMillis();
        }

        // Checks if OTP has expired
        boolean isExpired() {
            // OTP expires after 5 minutes (300,000 ms)
            return System.currentTimeMillis() - timestamp > 300_000;
        }
    }

    /*
    ConcurrentHashMap is used to ensure thread safety
    when multiple users are generating/verifying OTPs simultaneously
    */
    private final Map<String, OtpEntry> otpStore = new ConcurrentHashMap<>();

    // Stores sessionId -> captcha
    private final Map<String, String> captchaStore = new ConcurrentHashMap<>();

    // Random generator used to create OTP values
    private final Random random = new Random();

    // Generates a 5-character CAPTCHA and stores it against a session
    public String generateCaptcha(String sessionId) {

        // Generate a random string using UUID
        String captcha = UUID.randomUUID().toString().substring(0, 5).toUpperCase();

        // Store captcha mapped to the sessionId
        captchaStore.put(sessionId, captcha);

        return captcha;
    }

    // Verifies whether the user-entered CAPTCHA matches the stored one
    public boolean verifyCaptcha(String sessionId, String input) {

        // Retrieve the expected captcha for the session
        String expected = captchaStore.get(sessionId);

        // Return true only if captcha exists and matches (case-insensitive)
        return expected != null && expected.equalsIgnoreCase(input);
    }

    // Generates a 6-digit OTP and stores it for the phone number
    public String sendOtp(String phone) {

        // Generate random number between 0 and 999999
        String otp = String.valueOf(random.nextInt(1_000_000));

        // Store OTP along with timestamp
        otpStore.put(phone, new OtpEntry(otp));

        // Simulate sending OTP via SMS
        System.out.println("Sending OTP to " + phone + ": " + otp);

        // Returned only for demo/testing
        return otp;
    }

    // Verifies OTP entered by the user
    public boolean verifyOtp(String phone, String inputOtp) {

        // Fetch stored OTP entry
        OtpEntry entry = otpStore.get(phone);

        // If no OTP exists or it is expired → verification fails
        if (entry == null || entry.isExpired()) {
            return false;
        }

        // Check if OTP matches
        return entry.otp.equals(inputOtp);
    }

    // Demo flow showing how the system works
    public static void main(String[] args) throws InterruptedException {

        OtpCaptchaService service = new OtpCaptchaService();

        String sessionId = "session123"; // Simulated session ID

        // Step 1: Generate CAPTCHA for the user session
        String captcha = service.generateCaptcha(sessionId);
        System.out.println("CAPTCHA: " + captcha);

        // Step 2: User enters CAPTCHA (simulate correct input)
        if (service.verifyCaptcha(sessionId, captcha)) {

            System.out.println("Captcha verified ✅");

            // Step 3: Generate and send OTP
            String phone = "1234567890";
            String sentOtp = service.sendOtp(phone);

            // Step 4: User enters OTP (simulate correct input)
            boolean otpValid = service.verifyOtp(phone, sentOtp);

            System.out.println("OTP verified: " + otpValid);

        } else {

            System.out.println("Captcha failed ❌");
        }
    }
}