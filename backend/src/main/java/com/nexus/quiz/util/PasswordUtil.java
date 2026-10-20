package com.nexus.quiz.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.security.SecureRandom;

public final class PasswordUtil {

    private static final int BCRYPT_STRENGTH = 12;
    private static final int TEMPORARY_PASSWORD_LENGTH = 12;
    private static final char[] ALPHANUMERIC =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final BCryptPasswordEncoder PASSWORD_ENCODER =
            new BCryptPasswordEncoder(BCRYPT_STRENGTH);

    private PasswordUtil() {}

    public static String hash(String plaintext) {
        return PASSWORD_ENCODER.encode(plaintext);
    }

    public static boolean verify(String plaintext, String hash) {
        return PASSWORD_ENCODER.matches(plaintext, hash);
    }

    public static String generate() {
        StringBuilder password = new StringBuilder(TEMPORARY_PASSWORD_LENGTH);
        for (int i = 0; i < TEMPORARY_PASSWORD_LENGTH; i++) {
            password.append(ALPHANUMERIC[SECURE_RANDOM.nextInt(ALPHANUMERIC.length)]);
        }
        return password.toString();
    }
}
