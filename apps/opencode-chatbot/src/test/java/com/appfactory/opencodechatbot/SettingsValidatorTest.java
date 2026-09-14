package com.appfactory.opencodechatbot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.appfactory.opencodechatbot.data.SettingsValidator;

import org.junit.Test;

public class SettingsValidatorTest {

    @Test
    public void endpointMustBeHttpOrHttps() {
        assertTrue(SettingsValidator.isValidEndpoint("https://api.openai.com/v1"));
        assertTrue(SettingsValidator.isValidEndpoint("http://10.0.2.2:11434/v1"));
        assertFalse(SettingsValidator.isValidEndpoint("ftp://example.com"));
        assertFalse(SettingsValidator.isValidEndpoint("example.com/v1"));
        assertFalse(SettingsValidator.isValidEndpoint(""));
        assertFalse(SettingsValidator.isValidEndpoint("https://"));
    }

    @Test
    public void clampsTemperature() {
        assertEquals(2f, SettingsValidator.clampTemperature(5f), 0.0001f);
        assertEquals(0f, SettingsValidator.clampTemperature(-1f), 0.0001f);
        assertEquals(0.7f, SettingsValidator.clampTemperature(Float.NaN), 0.0001f);
        assertEquals(1f, SettingsValidator.clampTemperature(1f), 0.0001f);
    }

    @Test
    public void clampsMaxTokens() {
        assertEquals(1, SettingsValidator.clampMaxTokens(0));
        assertEquals(32768, SettingsValidator.clampMaxTokens(999999));
        assertEquals(100, SettingsValidator.clampMaxTokens(100));
    }

    @Test
    public void parsesTemperatures() {
        assertEquals(0.5f, SettingsValidator.parseTemperature("0.5", 0.7f), 0.0001f);
        assertEquals(0.7f, SettingsValidator.parseTemperature("abc", 0.7f), 0.0001f);
        assertEquals(0.7f, SettingsValidator.parseTemperature("", 0.7f), 0.0001f);
    }

    @Test
    public void parsesMaxTokens() {
        assertEquals(512, SettingsValidator.parseMaxTokens("512", 256));
        assertEquals(256, SettingsValidator.parseMaxTokens("not-a-number", 256));
    }

    @Test
    public void stripsTrailingSlashForDisplay() {
        assertEquals("https://api.example.com/v1",
                SettingsValidator.normalizeEndpointForDisplay("https://api.example.com/v1/"));
        assertEquals("https://api.example.com/v1",
                SettingsValidator.normalizeEndpointForDisplay("https://api.example.com/v1"));
    }

    @Test
    public void lowerTrimNormalizes() {
        assertEquals("chat", SettingsValidator.lowerTrim("  CHAT  "));
        assertEquals("", SettingsValidator.lowerTrim(null));
    }
}