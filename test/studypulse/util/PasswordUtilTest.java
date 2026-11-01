package studypulse.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.Base64;
import org.junit.Test;

/** SHA-256 hashing with a random 16-byte salt. */
public class PasswordUtilTest {

    @Test
    public void saltIs16RandomBytes() {
        String a = PasswordUtil.generateSalt();
        String b = PasswordUtil.generateSalt();
        assertEquals(16, Base64.getDecoder().decode(a).length);
        assertNotEquals(a, b);
    }

    @Test
    public void sameInputGivesSameHash() {
        String salt = PasswordUtil.generateSalt();
        assertEquals(PasswordUtil.hash("secret1".toCharArray(), salt), PasswordUtil.hash("secret1".toCharArray(), salt));
        assertEquals(32, Base64.getDecoder().decode(PasswordUtil.hash("secret1".toCharArray(), salt)).length);
    }

    @Test
    public void differentSaltGivesDifferentHash() {
        assertNotEquals(PasswordUtil.hash("secret1".toCharArray(), PasswordUtil.generateSalt()),
                PasswordUtil.hash("secret1".toCharArray(), PasswordUtil.generateSalt()));
    }

    @Test
    public void verifyAcceptsOnlyTheRightPassword() {
        String salt = PasswordUtil.generateSalt();
        String hash = PasswordUtil.hash("secret1".toCharArray(), salt);
        assertTrue(PasswordUtil.verify("secret1".toCharArray(), salt, hash));
        assertFalse(PasswordUtil.verify("Secret1".toCharArray(), salt, hash));
        assertFalse(PasswordUtil.verify("secret1".toCharArray(), PasswordUtil.generateSalt(), hash));
        assertFalse(PasswordUtil.verify("secret1".toCharArray(), null, hash));
    }

    @Test
    public void hashNeverContainsThePassword() {
        String salt = PasswordUtil.generateSalt();
        assertFalse(PasswordUtil.hash("secret1".toCharArray(), salt).contains("secret1"));
    }
}
