package com.appfactory.flashlight;

/** Pure logic for the flashlight UI; fully unit-tested. */
public final class TorchLogic {

    private TorchLogic() {}

    /** Human-readable action for the current LED state. */
    public static String action(boolean isOn) {
        return isOn ? "Turn off the flashlight" : "Turn on the flashlight";
    }

    /** Big-button label for the current LED state. */
    public static String buttonLabel(boolean isOn) {
        return isOn ? "TAP TO TURN OFF" : "TAP TO TURN ON";
    }

    /** Status line under the button for the current LED state. */
    public static String status(boolean isOn) {
        return isOn ? "Flashlight is ON" : "Flashlight is OFF";
    }

    /** Whether a tap should be able to change the light. */
    public static boolean canToggle(boolean flashAvailable, boolean permissionGranted) {
        return flashAvailable && permissionGranted;
    }
}