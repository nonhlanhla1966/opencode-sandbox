package com.appfactory.modules.validation;

import org.junit.Test;
import static org.junit.Assert.*;
import static com.appfactory.modules.validation.Validator.*;

public class ValidatorTest {
    @Test public void requiredRule() {
        assertTrue(all("abc", required("need")).ok);
        assertFalse(all("", required("need")).ok);
        assertFalse(all("   ", required("need")).ok);
    }
    @Test public void emailRule() {
        assertTrue(all("a@b.co", email("bad")).ok);
        assertFalse(all("not-an-email", email("bad")).ok);
    }
    @Test public void rangeRule() {
        assertTrue(all("5", range(0, 10, "rng")).ok);
        assertFalse(all("11", range(0, 10, "rng")).ok);
        assertFalse(all("abc", range(0, 10, "rng")).ok);
    }
    @Test public void combinedChainability() {
        Result r = all("hello", required("r"), minLength(3, "l"), maxLength(10, "m"));
        assertTrue(r.ok);
        Result bad = all("x", minLength(3, "l"));
        assertFalse(bad.ok);
        assertEquals("minLength", bad.key);
    }
}