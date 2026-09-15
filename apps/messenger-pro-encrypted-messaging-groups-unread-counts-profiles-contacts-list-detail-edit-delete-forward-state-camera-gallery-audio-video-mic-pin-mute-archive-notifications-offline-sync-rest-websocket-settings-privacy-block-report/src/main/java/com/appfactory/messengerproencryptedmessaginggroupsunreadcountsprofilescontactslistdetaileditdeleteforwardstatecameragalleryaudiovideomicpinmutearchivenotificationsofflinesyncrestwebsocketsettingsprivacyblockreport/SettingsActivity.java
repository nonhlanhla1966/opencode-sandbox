package com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.data.MessengerFileStore;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.security.SecretBox;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.settings.MessengerSettings;
import com.appfactory.modules.json.Json;
import com.appfactory.modules.json.JsonObject;
import com.appfactory.modules.storage.FileUtil;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Settings: theme, privacy, notifications, backend, encrypted backup. */
public final class SettingsActivity extends BaseActivity {

    private static final int REQ_IMPORT = 3001;

    private static final String ENVELOPE_REL = "envelope.json";

    private LinearLayout root;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(12), dp(24), dp(24));
        scroll.addView(root);

        buildTheme();
        buildPrivacy();
        buildBackend();
        buildBackup();
        buildStorage();
        buildNav();

        setContentView(scroll);
    }

    private void buildTheme() {
        root.addView(section(getString(R.string.pref_theme)));
        final MessengerSettings s = settings();
        RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.VERTICAL);
        final RadioButton sys = chip(this, getString(R.string.pref_theme_system));
        final RadioButton light = chip(this, getString(R.string.pref_theme_light));
        final RadioButton dark = chip(this, getString(R.string.pref_theme_dark));
        switch (s.themeMode()) {
            case LIGHT: light.setChecked(true); break;
            case DARK: dark.setChecked(true); break;
            default: sys.setChecked(true);
        }
        group.addView(sys);
        group.addView(light);
        group.addView(dark);
        group.setOnCheckedChangeListener((g, checkedId) -> {
            if (checkedId == light.getId()) s.setThemeMode(MessengerSettings.ThemeMode.LIGHT);
            else if (checkedId == dark.getId()) s.setThemeMode(MessengerSettings.ThemeMode.DARK);
            else s.setThemeMode(MessengerSettings.ThemeMode.SYSTEM);
            recreate();
        });
        root.addView(group);
    }

    private void buildPrivacy() {
        root.addView(section(getString(R.string.pref_privacy)));
        root.addView(toggle(getString(R.string.pref_read_receipts),
                settings().readReceiptsEnabled(),
                (b, checked) -> settings().setReadReceiptsEnabled(checked)));
        root.addView(toggle(getString(R.string.pref_typing),
                settings().typingIndicator(),
                (b, checked) -> settings().setTypingIndicator(checked)));
        root.addView(toggle(getString(R.string.pref_notifications),
                settings().notificationsEnabled(),
                (b, checked) -> settings().setNotificationsEnabled(checked)));
        root.addView(toggle(getString(R.string.pref_media_auto_download),
                settings().mediaDownload() == MessengerSettings.MediaDownload.ALWAYS,
                (b, checked) -> settings().setMediaDownload(checked
                        ? MessengerSettings.MediaDownload.ALWAYS
                        : MessengerSettings.MediaDownload.WIFI_ONLY)));
    }

    private void buildBackend() {
        root.addView(section(getString(R.string.label_backend)));

        final EditText base = new EditText(this);
        base.setHint(R.string.label_backend_url_hint);
        base.setText(settings().apiBaseUrl());
        root.addView(base);

        final EditText token = new EditText(this);
        token.setHint(R.string.label_backend_token_hint);
        token.setText(settings().authToken());
        root.addView(token);

        final TextView status = new TextView(this);
        status.setTextSize(13);
        status.setPadding(0, dp(4), 0, dp(4));
        status.setText(backendStatus());
        root.addView(status);

        Button save = new Button(this);
        save.setText(R.string.action_save);
        save.setOnClickListener(v -> {
            String url = base.getText().toString().trim();
            if (!url.isEmpty()) {
                String err = com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.network.RestContract.validateBase(url);
                if (err != null) {
                    toast(err);
                    status.setText(err);
                    return;
                }
            }
            settings().setApiBaseUrl(url);
            settings().setAuthToken(token.getText().toString().trim());
            status.setText(backendStatus());
            toast(getString(R.string.label_saved));
        });
        root.addView(save);
    }

    private void buildBackup() {
        root.addView(section(getString(R.string.pref_backup)));

        Button export = new Button(this);
        export.setText(R.string.action_export);
        export.setOnClickListener(v -> exportBackup());
        root.addView(export);

        Button importBtn = new Button(this);
        importBtn.setText(R.string.action_import);
        importBtn.setOnClickListener(v -> pickImport());
        root.addView(importBtn);
    }

    private void buildStorage() {
        root.addView(section(getString(R.string.pref_storage_used)));
        TextView t = new TextView(this);
        t.setText(formatBytes(app().store().snapshotBytes())
                + " snapshot, " + repo().outboxSize() + " queued, "
                + repo().conversations().size() + " conversations");
        t.setTextSize(14);
        root.addView(t);
    }

    private void buildNav() {
        root.addView(section(getString(R.string.pref_security)));

        Button about = new Button(this);
        about.setText(R.string.pref_about);
        about.setOnClickListener(v -> startActivity(new Intent(this, AboutActivity.class)));
        root.addView(about);

        Button snapshots = new Button(this);
        snapshots.setText(R.string.action_snapshot_view);
        snapshots.setOnClickListener(v -> startActivity(new Intent(this, ItemsActivity.class)));
        root.addView(snapshots);

        Button backBtn = new Button(this);
        backBtn.setText(R.string.action_back);
        backBtn.setOnClickListener(v -> finish());
        root.addView(backBtn);
    }

    private void exportBackup() {
        final EditText pinA = new EditText(this);
        pinA.setHint(R.string.label_pin);
        pinA.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        final EditText pinB = new EditText(this);
        pinB.setHint(R.string.label_pin_again);
        pinB.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(24), dp(8), dp(24), dp(4));
        row.addView(pinA);
        row.addView(pinB);

        new AlertDialog.Builder(this)
                .setTitle(R.string.action_export)
                .setMessage(R.string.label_export_pin_prompt)
                .setView(row)
                .setPositiveButton(R.string.action_export, (d, w) -> {
                    String a = pinA.getText().toString();
                    String b = pinB.getText().toString();
                    if (a.isEmpty() || !a.equals(b)) {
                        toast(getString(R.string.label_pin_mismatch));
                        return;
                    }
                    String snap = app().store().readSnapshot();
                    if (snap == null) snap = repo().snapshot().toString();
                    try {
                        String envelope = SecretBox.seal(snap, a);
                        writeBackupZip(envelope);
                        settings().setBackupPinConfigured(true);
                    } catch (Exception e) {
                        toast(getString(R.string.state_error));
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void writeBackupZip(String envelope) {
        File external = getExternalFilesDir(null);
        if (external == null) external = getCacheDir();
        String stamp = String.valueOf(System.currentTimeMillis());
        File dir = new File(external, "backup-" + stamp);
        try {
            FileUtil.safeChild(dir, ENVELOPE_REL);
        } catch (Exception ignored) { }
        if (!dir.exists()) dir.mkdirs();
        try {
            Files.write(new File(dir, ENVELOPE_REL).toPath(),
                    envelope.getBytes(StandardCharsets.UTF_8));
            File zip = new File(external, "messenger-backup-" + stamp + ".zip");
            FileUtil.zipDir(dir, zip);
            new AlertDialog.Builder(this)
                    .setTitle(R.string.label_backup_created)
                    .setMessage(zip.getAbsolutePath() + "\n" + formatBytes(zip.length()))
                    .setPositiveButton(R.string.action_cancel, null)
                    .show();
        } catch (Exception e) {
            toast(getString(R.string.state_error));
        }
    }

    private void pickImport() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/zip");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/zip", "application/octet-stream"});
        startActivityForResult(i, REQ_IMPORT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_IMPORT && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            File cache = new File(getCacheDir(), "import.zip");
            try (java.io.InputStream in = getContentResolver().openInputStream(uri);
                 java.io.FileOutputStream out = new java.io.FileOutputStream(cache)) {
                byte[] buf = new byte[8192];
                int n;
                while ((in != null && (n = in.read(buf)) != -1)) out.write(buf, 0, n);
            } catch (Exception e) {
                toast(getString(R.string.state_error));
                return;
            }
            doImport(cache);
        }
    }

    private void doImport(final File zipFile) {
        final File dir = new File(getCacheDir(), "imported-" + System.currentTimeMillis());
        try {
            MessengerFileStore.importZip(zipFile, dir);
        } catch (Exception e) {
            toast(getString(R.string.state_error));
            return;
        }
        final File envelopeFile = findFile(dir, ENVELOPE_REL);
        if (envelopeFile == null) {
            toast(getString(R.string.state_error));
            return;
        }
        final EditText pin = new EditText(this);
        pin.setHint(R.string.label_pin);
        pin.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        new AlertDialog.Builder(this)
                .setTitle(R.string.action_import)
                .setMessage(R.string.label_import_pin_prompt)
                .setView(pin)
                .setPositiveButton(R.string.action_import, (d, w) -> {
                    try {
                        String snap = SecretBox.unseal(new String(
                                        Files.readAllBytes(envelopeFile.toPath()),
                                        StandardCharsets.UTF_8),
                                pin.getText().toString().trim());
                        JsonObject parsed = Json.parseObject(snap);
                        if (parsed == null) throw new IllegalStateException("bad snapshot");
                        Files.write(app().store().file(MessengerFileStore.SNAPSHOT_FILE).toPath(),
                                snap.getBytes(StandardCharsets.UTF_8));
                        settings().setBackupPinConfigured(true);
                        toast(getString(R.string.label_restart_required));
                        finish();
                    } catch (Exception e) {
                        toast(getString(R.string.label_pin_wrong));
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private File findFile(File root, String name) {
        File[] all = root.listFiles();
        if (all == null) return null;
        for (File f : all) {
            if (f.isDirectory()) {
                File hit = findFile(f, name);
                if (hit != null) return hit;
            } else if (f.getName().equals(name)) {
                return f;
            }
        }
        return null;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        return (bytes / (1024 * 1024)) + " MB";
    }

    private String backendStatus() {
        String url = settings().apiBaseUrl();
        if (url.isEmpty()) return getString(R.string.label_backend_mock);
        String err = settings().apiValidationError();
        return err == null ? getString(R.string.label_backend_rest) : err;
    }

    private TextView section(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(16);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(14), 0, dp(6));
        return t;
    }

    private static RadioButton chip(android.content.Context c, String label) {
        RadioButton r = new RadioButton(c);
        r.setText(label);
        return r;
    }

    private android.widget.Switch toggle(String label, boolean checked,
                                         CompoundButton.OnCheckedChangeListener listener) {
        android.widget.Switch sw = new android.widget.Switch(this);
        sw.setText(label);
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener(listener);
        return sw;
    }
}