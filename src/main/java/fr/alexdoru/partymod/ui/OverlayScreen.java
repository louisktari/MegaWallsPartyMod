package fr.alexdoru.partymod.ui;

import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

/**
 * An empty, see-through screen that frees the mouse so the party panels can be
 * clicked and dragged without opening chat. All drawing and input lives in {@link Panels}.
 */
public final class OverlayScreen extends GuiScreen {
    private final int toggleKey;

    public OverlayScreen(int toggleKey) {
        this.toggleKey = toggleKey;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE || keyCode == toggleKey || keyCode == mc.gameSettings.keyBindInventory.getKeyCode()) {
            mc.displayGuiScreen(null);
        }
    }
}
