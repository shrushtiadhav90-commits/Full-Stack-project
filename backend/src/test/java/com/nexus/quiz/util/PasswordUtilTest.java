package com.nexus.quiz.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordUtilTest {

    @Test
    void hashesAndVerifiesPasswordWithBcrypt() {
        String password = "TestPassword123";
        String hash = PasswordUtil.hash(password);

        assertTrue(hash.startsWith("$2a$") || hash.startsWith("$2b$"));
        assertTrue(PasswordUtil.verify(password, hash));
    }

    @Test
    void generatesDistinctTwelveCharacterTemporaryPasswords() {
        String first = PasswordUtil.generate();
        String second = PasswordUtil.generate();

        assertEquals(12, first.length());
        assertEquals(12, second.length());
        assertNotEquals(first, second);
    }
}
