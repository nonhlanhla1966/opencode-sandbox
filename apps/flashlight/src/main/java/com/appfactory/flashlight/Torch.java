package com.appfactory.flashlight;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.util.Log;

import java.util.List;

/**
 * Turns the camera LED (flashlight) on and off.
 *
 * Uses {@link CameraManager#setTorchMode(String, boolean)} on API 23+ and the
 * legacy camera1 {@link Camera} API on API 21-22. Throws IllegalStateException
 * on any failure so the UI can surface a clear message.
 */
public final class Torch {

    private static final String TAG = "Flashlight";

    private final Context appContext;
    private final CameraManager cameraManager;
    private final String cameraId;

    private boolean torchOn;
    private Camera legacyCamera;

    public Torch(Context context) {
        appContext = context.getApplicationContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            cameraManager = (CameraManager) appContext.getSystemService(Context.CAMERA_SERVICE);
            cameraId = findTorchCameraId();
        } else {
            cameraManager = null;
            cameraId = null;
        }
    }

    /** Whether this device exposes a usable flash unit. */
    public boolean isSupported() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return cameraId != null;
        }
        PackageManager pm = appContext.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
                && Camera.getNumberOfCameras() > 0;
    }

    /** Current LED state. */
    public boolean isOn() {
        return torchOn;
    }

    /** Switches the LED on/off. No-op if already in the requested state. */
    public void setTorch(boolean on) {
        if (on == torchOn) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            setTorchApi23(on);
        } else {
            setTorchLegacy(on);
        }
        torchOn = on;
    }

    /** Turns the LED off and releases any held camera. Safe to call anytime. */
    public void release() {
        try {
            setTorch(false);
        } catch (RuntimeException e) {
            Log.w(TAG, "release failed", e);
        }
    }

    private String findTorchCameraId() {
        try {
            for (String id : cameraManager.getCameraIdList()) {
                CameraCharacteristics ch = cameraManager.getCameraCharacteristics(id);
                Boolean flash = ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (flash != null && flash) {
                    return id;
                }
            }
        } catch (CameraAccessException e) {
            Log.w(TAG, "cannot inspect cameras", e);
        }
        return null;
    }

    private void setTorchApi23(boolean on) {
        if (cameraId == null) {
            throw new IllegalStateException("No flash unit on this device");
        }
        try {
            cameraManager.setTorchMode(cameraId, on);
        } catch (CameraAccessException | RuntimeException e) {
            throw new IllegalStateException("Flash unavailable right now: " + e.getMessage(), e);
        }
    }

    private void setTorchLegacy(boolean on) {
        try {
            if (on) {
                if (legacyCamera != null) {
                    return;
                }
                Camera cam = Camera.open();
                try {
                    Camera.Parameters p = cam.getParameters();
                    List<String> modes = p.getSupportedFlashModes();
                    if (modes == null || !modes.contains(Camera.Parameters.FLASH_MODE_TORCH)) {
                        throw new IllegalStateException("This camera has no torch mode");
                    }
                    p.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                    cam.setParameters(p);
                    legacyCamera = cam;
                } catch (RuntimeException e) {
                    cam.release();
                    throw e;
                }
            } else {
                if (legacyCamera != null) {
                    legacyCamera.release();
                    legacyCamera = null;
                }
            }
        } catch (RuntimeException e) {
            throw new IllegalStateException("Could not toggle flashlight: " + e.getMessage(), e);
        }
    }

    /** Convenience so the manifest's camera permission intent stays explicit. */
    public static String requiredPermission() {
        return Manifest.permission.CAMERA;
    }
}