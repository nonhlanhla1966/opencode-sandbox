package com.appfactory.flashlight;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TorchLogicTest {

    @Test
    public void actionDescribesState() {
        assertEquals("Turn on the flashlight", TorchLogic.action(false));
        assertEquals("Turn off the flashlight", TorchLogic.action(true));
    }

    @Test
    public void buttonLabelMatchesState() {
        assertEquals("TAP TO TURN ON", TorchLogic.buttonLabel(false));
        assertEquals("TAP TO TURN OFF", TorchLogic.buttonLabel(true));
    }

    @Test
    public void statusMatchesState() {
        assertEquals("Flashlight is OFF", TorchLogic.status(false));
        assertEquals("Flashlight is ON", TorchLogic.status(true));
    }

    @Test
    public void canToggleRequiresSupportAndPermission() {
        assertTrue(TorchLogic.canToggle(true, true));
        assertFalse(TorchLogic.canToggle(false, true));
        assertFalse(TorchLogic.canToggle(true, false));
        assertFalse(TorchLogic.canToggle(false, false));
    }
}