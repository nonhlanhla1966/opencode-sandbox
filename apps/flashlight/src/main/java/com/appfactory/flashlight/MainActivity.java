package com.appfactory.flashlight;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {

    private static final int REQ_CAMERA = 1000;

    private Torch torch;
    private Button toggleButton;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        torch = new Torch(this);
        toggleButton = findViewById(R.id.toggleButton);
        status = findViewById(R.id.status);

        toggleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onTogglePressed();
            }
        });

        render();
    }

    private void onTogglePressed() {
        if (!torch.isSupported()) {
            Toast.makeText(this, R.string.no_flash, Toast.LENGTH_SHORT).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, R.string.permission_needed, Toast.LENGTH_SHORT).show();
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
            return;
        }
        switchTorch(!torch.isOn());
    }

    private void switchTorch(boolean target) {
        try {
            torch.setTorch(target);
        } catch (RuntimeException e) {
            Toast.makeText(this, getString(R.string.error_toggle, e.getMessage()),
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (torch.isOn()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        render();
    }

    private void render() {
        boolean supported = torch.isSupported();
        boolean on = torch.isOn();

        toggleButton.setEnabled(supported);
        toggleButton.setSelected(on);
        toggleButton.setText(TorchLogic.buttonLabel(on));
        toggleButton.setContentDescription(TorchLogic.action(on));

        if (!supported) {
            status.setText(R.string.no_flash);
        } else {
            status.setText(TorchLogic.status(on));
            status.setTextColor(getResources().getColor(on ? R.color.status_on : R.color.text_secondary));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                switchTorch(true);
            } else {
                Toast.makeText(this, R.string.permission_needed, Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (torch != null && torch.isOn()) {
            torch.release();
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            render();
        }
    }
}