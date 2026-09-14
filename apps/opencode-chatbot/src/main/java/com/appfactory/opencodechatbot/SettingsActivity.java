package com.appfactory.opencodechatbot;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import com.appfactory.opencodechatbot.data.ChatSettings;
import com.appfactory.opencodechatbot.data.ConversationStore;
import com.appfactory.opencodechatbot.data.SettingsValidator;
import com.appfactory.opencodechatbot.provider.ProviderConfig;
import com.appfactory.opencodechatbot.provider.ProviderConfig.Preset;
import com.appfactory.opencodechatbot.provider.ModelsFetcher;
import com.appfactory.opencodechatbot.util.AppTheme;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/** Settings: provider config, model/temperature/tokens, theme, data tools. */
public final class SettingsActivity extends Activity {

    private static final int REQ_EXPORT = 101;
    private static final int REQ_IMPORT = 102;

    private ChatSettings settings;
    private ConversationStore store;

    private Spinner presetSpinner;
    private EditText etProviderName;
    private EditText etEndpoint;
    private EditText etApiKey;
    private EditText etModel;
    private EditText etTemperature;
    private EditText etMaxTokens;
    private EditText etSystemPrompt;
    private Spinner themeSpinner;
    private boolean keyVisible;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        settings = new ChatSettings(this);
        AppTheme.apply(this, settings);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        store = new ConversationStore(this);

        presetSpinner = findViewById(R.id.spinner_preset);
        etProviderName = findViewById(R.id.et_provider_name);
        etEndpoint = findViewById(R.id.et_endpoint);
        etApiKey = findViewById(R.id.et_api_key);
        etModel = findViewById(R.id.et_model);
        etTemperature = findViewById(R.id.et_temperature);
        etMaxTokens = findViewById(R.id.et_max_tokens);
        etSystemPrompt = findViewById(R.id.et_system_prompt);
        themeSpinner = findViewById(R.id.spinner_theme);

        presetSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, ChatSettings.presetLabels()));
        presetSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                applyPreset(ProviderConfig.preset(position));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        themeSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, new String[]{
                        getString(R.string.theme_system),
                        getString(R.string.theme_light),
                        getString(R.string.theme_dark)}));
        themeSpinner.setSelection(themeIndex());

        // Prefill with saved values by first positioning the preset spinner at
        // the first (generic) item without firing the listener.
        presetSpinner.setSelection(0, false);
        etProviderName.setText(settings.getProviderName());
        etEndpoint.setText(settings.getEndpoint());
        etApiKey.setText(settings.getApiKey());
        etModel.setText(settings.getModel());
        etTemperature.setText(formatTemp(settings.getTemperature()));
        etMaxTokens.setText(String.valueOf(settings.getMaxTokens()));
        etSystemPrompt.setText(settings.getSystemPrompt());

        Button toggle = findViewById(R.id.btn_toggle_key);
        toggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleKeyVisibility(toggle);
            }
        });

        findViewById(R.id.btn_save).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
            }
        });

        findViewById(R.id.btn_fetch_models).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fetchModels();
            }
        });

        findViewById(R.id.btn_export).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exportConversations();
            }
        });
        findViewById(R.id.btn_import).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                importConversations();
            }
        });
        findViewById(R.id.btn_clear_keys).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmClearKeys();
            }
        });
        findViewById(R.id.btn_clear_chats).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmClearChats();
            }
        });
        findViewById(R.id.btn_about).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(SettingsActivity.this, AboutActivity.class));
            }
        });
    }

    private void applyPreset(Preset preset) {
        if (preset == null) {
            return;
        }
        etProviderName.setText(preset.label);
        etEndpoint.setText(preset.endpoint);
        if (preset.model != null && !preset.model.isEmpty()) {
            etModel.setText(preset.model);
        }
    }

    /**
     * Query the configured endpoint's OpenAI-compatible /models list and let
     * the user pick one of the models the provider actually serves. The API key
     * is used only in the outgoing Authorization header and is never logged.
     */
    private void fetchModels() {
        String endpoint = etEndpoint.getText().toString().trim();
        if (!SettingsValidator.isValidEndpoint(endpoint)) {
            Toast.makeText(this, R.string.invalid_endpoint, Toast.LENGTH_LONG).show();
            return;
        }
        final String base = endpoint;
        final String apiKey = etApiKey.getText().toString().trim();
        final Button btn = findViewById(R.id.btn_fetch_models);
        btn.setEnabled(false);
        final Thread task = new Thread(() -> {
            List<String> models = null;
            String error = null;
            try {
                models = ModelsFetcher.fetch(base, apiKey);
            } catch (Exception e) {
                error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            }
            final List<String> fetched = models;
            final String fetchError = error;
            runOnUiThread(() -> {
                btn.setEnabled(true);
                if (fetched == null) {
                    Toast.makeText(SettingsActivity.this,
                            getString(R.string.fetch_models_failed, fetchError), Toast.LENGTH_LONG).show();
                    return;
                }
                if (fetched.isEmpty()) {
                    Toast.makeText(SettingsActivity.this,
                            R.string.fetch_models_empty, Toast.LENGTH_LONG).show();
                    return;
                }
                showModelsDialog(fetched);
            });
        });
        task.start();
    }

    private void showModelsDialog(final List<String> models) {
        final List<String> sorted = new ArrayList<>(models);
        java.util.Collections.sort(sorted);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.fetch_models_title);
        builder.setItems(sorted.toArray(new String[0]), (dialog, which) ->
                etModel.setText(sorted.get(which)));
        builder.setNegativeButton(R.string.action_cancel, null);
        builder.show();
    }

    private int themeIndex() {
        String t = settings.getTheme();
        if (ChatSettings.THEME_DARK.equals(t)) {
            return 2;
        }
        if (ChatSettings.THEME_LIGHT.equals(t)) {
            return 1;
        }
        return 0;
    }

    private void toggleKeyVisibility(Button toggle) {
        keyVisible = !keyVisible;
        etApiKey.setInputType(keyVisible
                ? android.text.InputType.TYPE_CLASS_TEXT
                : android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        etApiKey.setSelection(etApiKey.length());
        toggle.setText(keyVisible ? R.string.action_hide_key : R.string.action_show_key);
    }

    private void save() {
        String providerName = etProviderName.getText().toString().trim();
        String endpoint = etEndpoint.getText().toString().trim();
        String apiKey = etApiKey.getText().toString().trim();
        String model = etModel.getText().toString().trim();
        float temperature = SettingsValidator.clampTemperature(
                SettingsValidator.parseTemperature(etTemperature.getText().toString(),
                        ProviderConfig.DEFAULT_TEMPERATURE));
        int maxTokens = SettingsValidator.clampMaxTokens(
                SettingsValidator.parseMaxTokens(etMaxTokens.getText().toString(),
                        ProviderConfig.DEFAULT_MAX_TOKENS));

        if (!SettingsValidator.isNonBlank(providerName)) {
            Toast.makeText(this, R.string.invalid_provider_name, Toast.LENGTH_LONG).show();
            return;
        }
        if (!SettingsValidator.isValidEndpoint(endpoint)) {
            Toast.makeText(this, R.string.invalid_endpoint, Toast.LENGTH_LONG).show();
            return;
        }

        settings.saveProviderConfig(new ProviderConfig(providerName, endpoint, apiKey,
                model, temperature, maxTokens));
        settings.setSystemPrompt(etSystemPrompt.getText().toString());
        settings.setTheme(themeFromIndex(themeSpinner.getSelectedItemPosition()));
        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    private static String themeFromIndex(int index) {
        if (index == 2) {
            return ChatSettings.THEME_DARK;
        }
        if (index == 1) {
            return ChatSettings.THEME_LIGHT;
        }
        return ChatSettings.THEME_SYSTEM;
    }

    private static String formatTemp(float value) {
        if (value == Math.rint(value)) {
            return String.valueOf((int) value);
        }
        return String.valueOf(value);
    }

    // ----------------------------------------------------------------- data

    private void exportConversations() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, getString(R.string.export_filename));
        startActivityForResult(intent, REQ_EXPORT);
    }

    private void importConversations() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQ_IMPORT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_EXPORT && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri == null) {
                return;
            }
            try {
                OutputStream os = getContentResolver().openOutputStream(uri, "wt");
                if (os != null) {
                    store.exportAll(os);
                    os.close();
                    toastQuietly(getString(R.string.settings_saved));
                }
            } catch (Exception e) {
                toastQuietly(getString(R.string.export_failed));
            }
        } else if (requestCode == REQ_IMPORT && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri == null) {
                return;
            }
            try {
                InputStream is = getContentResolver().openInputStream(uri);
                if (is != null) {
                    int count = store.importDocument(is);
                    is.close();
                    toastQuietly(getString(R.string.import_done, count));
                }
            } catch (Exception e) {
                toastQuietly(getString(R.string.import_failed));
            }
        }
    }

    private void toastQuietly(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void confirmClearKeys() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.action_clear_keys)
                .setMessage(R.string.clear_keys_confirm)
                .setPositiveButton(R.string.action_confirmed_yes, (d, w) -> {
                    settings.clearApiKeys();
                    etApiKey.setText("");
                    toastQuietly(getString(R.string.settings_saved));
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void confirmClearChats() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.action_clear_chats)
                .setMessage(R.string.clear_chats_confirm)
                .setPositiveButton(R.string.action_confirmed_yes, (d, w) -> {
                    store.deleteAll();
                    toastQuietly(getString(R.string.settings_saved));
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }
}