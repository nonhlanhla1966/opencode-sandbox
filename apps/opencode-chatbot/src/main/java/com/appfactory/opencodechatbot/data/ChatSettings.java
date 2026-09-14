package com.appfactory.opencodechatbot.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.appfactory.opencodechatbot.provider.ProviderConfig;
import com.appfactory.opencodechatbot.provider.ProviderConfig.Preset;

/**
 * Settings coordinator: provider config, theme, current conversation, plus
 * secure storage of the API key. The key itself is handled exclusively by
 * {@link SecurePrefs} — it is held in memory for the current session and never
 * written in plaintext anywhere.
 */
public final class ChatSettings {

    public static final String THEME_SYSTEM = "system";
    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK = "dark";

    private static final String PREFS = "opencode_chatbot_settings";
    private static final String KEY_PROVIDER_NAME = "provider_name";
    private static final String KEY_ENDPOINT = "endpoint";
    private static final String KEY_MODEL = "model";
    private static final String KEY_TEMPERATURE = "temperature";
    private static final String KEY_MAX_TOKENS = "max_tokens";
    private static final String KEY_SYSTEM_PROMPT = "system_prompt";
    private static final String KEY_THEME = "theme";
    private static final String KEY_CURRENT_CONVERSATION = "current_conversation";
    private static final String KEY_HAS_SETUP = "has_setup";
    private static final String SECRET_API_KEY = "api_key";

    private final SharedPreferences prefs;
    private final SecurePrefs secure;

    public ChatSettings(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.secure = new SecurePrefs(context);
    }

    public boolean hasCompletedSetup() {
        return prefs.getBoolean(KEY_HAS_SETUP, false);
    }

    public void markSetupComplete() {
        prefs.edit().putBoolean(KEY_HAS_SETUP, true).apply();
    }

    public String getProviderName() {
        return prefs.getString(KEY_PROVIDER_NAME, "");
    }

    public String getEndpoint() {
        return prefs.getString(KEY_ENDPOINT, "");
    }

    public String getApiKey() {
        String key = secure.get(SECRET_API_KEY);
        return key == null ? "" : key;
    }

    public String getModel() {
        return prefs.getString(KEY_MODEL, "");
    }

    public float getTemperature() {
        return prefs.getFloat(KEY_TEMPERATURE, ProviderConfig.DEFAULT_TEMPERATURE);
    }

    public int getMaxTokens() {
        return prefs.getInt(KEY_MAX_TOKENS, ProviderConfig.DEFAULT_MAX_TOKENS);
    }

    public String getSystemPrompt() {
        return prefs.getString(KEY_SYSTEM_PROMPT, ProviderConfig.DEFAULT_SYSTEM_PROMPT);
    }

    public String getTheme() {
        return prefs.getString(KEY_THEME, THEME_SYSTEM);
    }

    public void saveProviderConfig(ProviderConfig config) {
        prefs.edit()
                .putString(KEY_PROVIDER_NAME, config.getProviderName().trim())
                .putString(KEY_ENDPOINT, config.getEndpoint().trim())
                .putString(KEY_MODEL, config.getModel().trim())
                .putFloat(KEY_TEMPERATURE, SettingsValidator.clampTemperature(config.getTemperature()))
                .putInt(KEY_MAX_TOKENS, SettingsValidator.clampMaxTokens(config.getMaxTokens()))
                .apply();
        if (!config.getApiKey().isEmpty()) {
            secure.put(SECRET_API_KEY, config.getApiKey());
        }
        markSetupComplete();
    }

    public void setSystemPrompt(String prompt) {
        prefs.edit().putString(KEY_SYSTEM_PROMPT, prompt == null ? "" : prompt.trim()).apply();
    }

    public void setTheme(String theme) {
        prefs.edit().putString(KEY_THEME, theme == null ? THEME_SYSTEM : theme).apply();
    }

    public void setCurrentConversation(String id) {
        if (id == null) {
            prefs.edit().remove(KEY_CURRENT_CONVERSATION).apply();
        } else {
            prefs.edit().putString(KEY_CURRENT_CONVERSATION, id).apply();
        }
    }

    public String getCurrentConversation() {
        return prefs.getString(KEY_CURRENT_CONVERSATION, null);
    }

    public ProviderConfig providerConfig() {
        return new ProviderConfig(
                getProviderName(),
                getEndpoint(),
                getApiKey(),
                getModel(),
                getTemperature(),
                getMaxTokens());
    }

    public void clearApiKeys() {
        secure.remove(SECRET_API_KEY);
    }

    /** List of preset labels for the settings spinner. */
    public static String[] presetLabels() {
        java.util.List<Preset> presets = ProviderConfig.presets();
        String[] labels = new String[presets.size()];
        for (int i = 0; i < presets.size(); i++) {
            labels[i] = presets.get(i).label;
        }
        return labels;
    }
}