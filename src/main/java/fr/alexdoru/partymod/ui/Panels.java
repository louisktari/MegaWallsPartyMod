package fr.alexdoru.partymod.ui;

import fr.alexdoru.partymod.PartyConfig;
import fr.alexdoru.partymod.PartyMod;
import fr.alexdoru.partymod.PartyRuntime;
import fr.alexdoru.partymod.core.PartyTracker;
import fr.alexdoru.partymod.core.PartyTracker.Member;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Four on-screen panels that behave like extra chat windows. They are always visible
 * while you play; open chat (or press the overlay key) and they become interactive:
 * drag a title bar to move a panel, hover a player for their stats, click buttons to act.
 */
public final class Panels {

    public enum Id { LOG, MEMBERS, REVIEW, BLOCKED }

    private static final int ROW = 11, HEADER = 13, PAD = 3;
    private static final int TEXT = 0xFFE8E8F0, MUTED = 0xFF9AA0B4, ACCENT = 0xFFC78BFF;
    private static final int RED = 0xFFFF6B6B, GREEN = 0xFF7EE0A1, GOLD = 0xFFFFC857, BLUE = 0xFF8EC5FF;

    static final class Button {
        final String label, confirmKey;
        final int color;
        final Runnable action;

        Button(String label, int color, Runnable action) {
            this(label, color, action, null);
        }

        Button(String label, int color, Runnable action, String confirmKey) {
            this.label = label;
            this.color = color;
            this.action = action;
            this.confirmKey = confirmKey;
        }
    }

    static final class Row {
        String text, sub;
        final List<Button> buttons = new ArrayList<>();
        List<String> tooltip = Collections.emptyList();
        /** Buttons only show while the row is hovered, keeping crowded panels clean. */
        boolean buttonsOnHover;
    }

    static final class Model {
        final Id id;
        final String title;
        final List<Button> header = new ArrayList<>();
        final List<Row> rows = new ArrayList<>();
        String empty = "Nothing yet";
        boolean alert;

        Model(Id id, String title) {
            this.id = id;
            this.title = title;
        }
    }

    private static final class Hit {
        final int x1, y1, x2, y2;
        final Runnable action;
        final Id dragHandle;

        Hit(int x1, int y1, int x2, int y2, Runnable action, Id dragHandle) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
            this.action = action;
            this.dragHandle = dragHandle;
        }

        boolean contains(int x, int y) {
            return x >= x1 && x < x2 && y >= y1 && y < y2;
        }
    }

    private final List<Hit> hits = new ArrayList<>();
    private Id dragging;
    private float dragOffsetX, dragOffsetY;
    private String armed = "";
    private long armedAt;
    private List<String> tooltip;
    private int tooltipX, tooltipY;

    // ------------------------------------------------------------------ events

    private static boolean interactiveScreen(GuiScreen screen) {
        return screen instanceof GuiChat || screen instanceof OverlayScreen;
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (interactiveScreen(mc.currentScreen) || mc.gameSettings.showDebugInfo) return;
        if (PartyMod.runtime == null || !PartyMod.runtime.active()) return;
        draw(event.resolution, -1, -1, false);
    }

    @SubscribeEvent
    public void onScreenDraw(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!interactiveScreen(event.gui) || PartyMod.runtime == null) return;
        boolean editing = event.gui instanceof OverlayScreen;
        if (!editing && !PartyMod.runtime.active()) return;
        draw(new ScaledResolution(Minecraft.getMinecraft()), event.mouseX, event.mouseY, true);
    }

    @SubscribeEvent
    public void onMouse(GuiScreenEvent.MouseInputEvent.Pre event) {
        if (!interactiveScreen(event.gui)) return;
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution sr = new ScaledResolution(mc);
        int x = Mouse.getEventX() * sr.getScaledWidth() / mc.displayWidth;
        int y = sr.getScaledHeight() - Mouse.getEventY() * sr.getScaledHeight() / mc.displayHeight - 1;
        int button = Mouse.getEventButton();
        if (button == 0 && Mouse.getEventButtonState()) {
            if (press(x, y)) event.setCanceled(true);
        } else if (button == 0) {
            if (dragging != null) {
                dragging = null;
                PartyMod.config.save();
                event.setCanceled(true);
            }
        } else if (button == -1 && dragging != null) {
            move(dragging, x - dragOffsetX, y - dragOffsetY, sr);
        }
    }

    private boolean press(int x, int y) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (!hit.contains(x, y)) continue;
            if (hit.dragHandle != null) {
                dragging = hit.dragHandle;
                float[] pos = position(hit.dragHandle, new ScaledResolution(Minecraft.getMinecraft()));
                dragOffsetX = x - pos[0];
                dragOffsetY = y - pos[1];
            } else {
                hit.action.run();
                if (Minecraft.getMinecraft().thePlayer != null) {
                    Minecraft.getMinecraft().thePlayer.playSound("gui.button.press", 0.25F, 1.0F);
                }
            }
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ layout & drawing

    private void draw(ScaledResolution sr, int mouseX, int mouseY, boolean interactive) {
        PartyConfig c = PartyMod.config;
        if (c == null) return;
        hits.clear();
        tooltip = null;
        if (System.currentTimeMillis() - armedAt > 3000) armed = "";
        PartyTracker party = PartyMod.runtime.party;
        if (c.showReview) draw(review(party), sr, mouseX, mouseY, interactive);
        if (c.showMembers) draw(members(party), sr, mouseX, mouseY, interactive);
        if (c.showLog) draw(log(party), sr, mouseX, mouseY, interactive);
        if (c.showBlocked) draw(blocked(party), sr, mouseX, mouseY, interactive);
        if (tooltip != null) drawTooltip(tooltip, tooltipX, tooltipY, sr);
        if (interactive && Minecraft.getMinecraft().currentScreen instanceof OverlayScreen) {
            FontRenderer fr = Minecraft.getMinecraft().fontRendererObj;
            String hint = "Drag panels by their title bar  -  hover players for stats  -  ESC to close";
            fr.drawStringWithShadow(hint, (sr.getScaledWidth() - fr.getStringWidth(hint)) / 2f, sr.getScaledHeight() - 12, MUTED);
        }
    }

    private void draw(Model model, ScaledResolution sr, int mouseX, int mouseY, boolean interactive) {
        PartyConfig c = PartyMod.config;
        if (!interactive && c.hideEmpty && model.rows.isEmpty()) return;
        FontRenderer fr = Minecraft.getMinecraft().fontRendererObj;
        float scale = c.panelScale;
        int width = (int) c.panelWidth;
        int maxRows = (int) c.maxRows;
        List<Row> rows = model.rows.size() > maxRows ? model.rows.subList(0, maxRows) : model.rows;
        int hidden = model.rows.size() - rows.size();
        int height = HEADER + PAD;
        for (Row r : rows) height += ROW + (r.sub != null ? ROW - 1 : 0);
        if (rows.isEmpty() || hidden > 0) height += ROW;
        height += PAD - 1;

        float[] pos = position(model.id, sr);
        float px = clamp(pos[0], 0, sr.getScaledWidth() - width * scale);
        float py = clamp(pos[1], 0, sr.getScaledHeight() - height * scale);
        int lx = mouseX < 0 ? -1 : (int) ((mouseX - px) / scale);
        int ly = mouseY < 0 ? -1 : (int) ((mouseY - py) / scale);

        int firstHit = hits.size();
        GlStateManager.pushMatrix();
        GlStateManager.translate(px, py, 0);
        GlStateManager.scale(scale, scale, 1);
        int alpha = Math.max(0, Math.min(255, (int) (c.backgroundOpacity * 255))) << 24;
        Gui.drawRect(0, 0, width, height, alpha | 0x0E0E16);
        int headerColor = model.alert && (System.currentTimeMillis() / 500) % 2 == 0 ? 0x5A4A2A : 0x2A2140;
        Gui.drawRect(0, 0, width, HEADER, Math.min(255, (alpha >>> 24) + 60) << 24 | headerColor);
        Gui.drawRect(0, HEADER - 1, width, HEADER, 0xFF000000 | (model.alert ? 0xC89B3C : 0x7A55C8));
        fr.drawStringWithShadow(model.title, PAD, 3, model.alert ? GOLD : ACCENT);

        // Header buttons, right-aligned. The rest of the bar is the drag handle.
        int bx = width - PAD;
        if (interactive) {
            for (int i = model.header.size() - 1; i >= 0; i--) bx = drawButton(model.header.get(i), model.id + ":h" + i, bx, 3, lx, ly, px, py, scale);
            hits.add(firstHit, new Hit((int) px, (int) py, (int) (px + width * scale), (int) (py + HEADER * scale), null, model.id));
        }

        int y = HEADER + PAD;
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            int rowHeight = ROW + (r.sub != null ? ROW - 1 : 0);
            boolean hover = interactive && lx >= 0 && lx < width && ly >= y - 1 && ly < y - 1 + rowHeight;
            if (hover) {
                Gui.drawRect(1, y - 1, width - 1, y - 1 + rowHeight, 0x30FFFFFF);
                if (!r.tooltip.isEmpty()) {
                    tooltip = r.tooltip;
                    tooltipX = mouseX;
                    tooltipY = mouseY;
                }
            }
            int textRight = width - PAD;
            if (interactive && (!r.buttonsOnHover || hover)) {
                for (int b = r.buttons.size() - 1; b >= 0; b--) textRight = drawButton(r.buttons.get(b), model.id + ":" + i + ":" + b, textRight, y, lx, ly, px, py, scale);
            }
            fr.drawStringWithShadow(fr.trimStringToWidth(r.text, Math.max(10, textRight - PAD - 2)), PAD, y, TEXT);
            if (r.sub != null) fr.drawStringWithShadow(fr.trimStringToWidth(r.sub, width - PAD * 3), PAD + 6, y + ROW - 1, MUTED);
            y += rowHeight;
        }
        if (rows.isEmpty()) fr.drawStringWithShadow(model.empty, PAD, y, MUTED);
        else if (hidden > 0) fr.drawStringWithShadow("+" + hidden + " more", PAD, y, MUTED);
        GlStateManager.popMatrix();
    }

    /** Draws a [label] button ending at {@code right}; returns its left edge. */
    private int drawButton(Button b, String key, int right, int y, int lx, int ly, float px, float py, float scale) {
        FontRenderer fr = Minecraft.getMinecraft().fontRendererObj;
        boolean isArmed = b.confirmKey != null && armed.equals(b.confirmKey);
        String label = "[" + (isArmed ? "Sure?" : b.label) + "]";
        int w = fr.getStringWidth(label);
        int left = right - w;
        boolean hover = lx >= left - 1 && lx < right + 1 && ly >= y - 1 && ly < y + 9;
        int color = isArmed ? RED : b.color;
        if (hover) Gui.drawRect(left - 1, y - 1, right + 1, y + 9, 0x40FFFFFF);
        fr.drawStringWithShadow(label, left, y, hover ? 0xFFFFFFFF : color);
        Runnable action = b.confirmKey == null ? b.action : () -> {
            if (armed.equals(b.confirmKey)) {
                armed = "";
                b.action.run();
            } else {
                armed = b.confirmKey;
                armedAt = System.currentTimeMillis();
            }
        };
        hits.add(new Hit((int) (px + (left - 1) * scale), (int) (py + (y - 1) * scale),
                (int) (px + (right + 1) * scale), (int) (py + (y + 9) * scale), action, null));
        return left - 3;
    }

    private void drawTooltip(List<String> lines, int x, int y, ScaledResolution sr) {
        FontRenderer fr = Minecraft.getMinecraft().fontRendererObj;
        int w = 0;
        for (String line : lines) w = Math.max(w, fr.getStringWidth(line));
        int h = lines.size() * 10 + 4;
        int tx = x + 12, ty = y - 6;
        if (tx + w + 6 > sr.getScaledWidth()) tx = x - w - 14;
        if (ty + h > sr.getScaledHeight()) ty = sr.getScaledHeight() - h - 2;
        GlStateManager.pushMatrix();
        GlStateManager.translate(0, 0, 400);
        Gui.drawRect(tx - 3, ty - 3, tx + w + 3, ty + h - 1, 0xF0100010);
        Gui.drawRect(tx - 3, ty - 3, tx + w + 3, ty - 2, 0xFF7A55C8);
        for (int i = 0; i < lines.size(); i++) fr.drawStringWithShadow(lines.get(i), tx, ty + i * 10, TEXT);
        GlStateManager.popMatrix();
    }

    // ------------------------------------------------------------------ panel content

    private Model review(PartyTracker party) {
        PartyRuntime rt = PartyMod.runtime;
        List<Member> queue = party.toReview();
        Model m = new Model(Id.REVIEW, "To review" + (queue.isEmpty() ? "" : " (" + queue.size() + ")"));
        m.alert = !queue.isEmpty();
        m.empty = "No suspicious players";
        if (queue.size() > 1) m.header.add(new Button("Kick all", RED, rt::removeAllFlagged, "kick-all"));
        for (Member member : queue) {
            Row r = new Row();
            r.text = EnumChatFormatting.GOLD + member.name;
            r.sub = String.join(", ", member.reasons);
            r.tooltip = tooltip(member);
            String name = member.name;
            r.buttons.add(new Button("Kick", RED, () -> rt.remove(name, String.join(", ", member.reasons), false)));
            r.buttons.add(new Button("Keep", GREEN, () -> rt.dismiss(name)));
            r.buttons.add(new Button("Trust", BLUE, () -> rt.trust(name)));
            m.rows.add(r);
        }
        return m;
    }

    private Model members(PartyTracker party) {
        PartyRuntime rt = PartyMod.runtime;
        Model m = new Model(Id.MEMBERS, "Party members" + (party.members().isEmpty() ? "" : " (" + party.members().size() + ")"));
        m.empty = "Not in a party";
        m.header.add(new Button("Sync", ACCENT, rt::syncParty));
        m.header.add(new Button("Re-check", ACCENT, rt::recheckAll));
        m.header.add(new Button("Settings", MUTED, () -> PartyMod.config.openGui()));
        for (Member member : party.members()) {
            Row r = new Row();
            r.text = statusColor(member) + member.name + EnumChatFormatting.GRAY + " " + detail(member);
            r.tooltip = tooltip(member);
            r.buttonsOnHover = true;
            String name = member.name;
            if (member.status != PartyTracker.Status.REMOVED) {
                r.buttons.add(new Button("Kick", RED, () -> rt.remove(name, "Removed by host", false)));
            }
            if (member.status == PartyTracker.Status.TRUSTED) r.buttons.add(new Button("Untrust", MUTED, () -> rt.untrust(name)));
            else r.buttons.add(new Button("Trust", BLUE, () -> rt.trust(name)));
            r.buttons.add(new Button("Check", ACCENT, () -> rt.recheck(name)));
            m.rows.add(r);
        }
        return m;
    }

    private Model log(PartyTracker party) {
        Model m = new Model(Id.LOG, "Party log");
        m.header.add(new Button("Clear", MUTED, party::clearLog));
        for (String line : party.log((int) PartyMod.config.maxRows)) {
            Row r = new Row();
            r.text = line;
            m.rows.add(r);
        }
        return m;
    }

    private Model blocked(PartyTracker party) {
        PartyRuntime rt = PartyMod.runtime;
        Model m = new Model(Id.BLOCKED, "Blocked this session");
        m.empty = "Nobody blocked yet";
        m.header.add(new Button("Clear", MUTED, party::clearBlocked));
        for (PartyTracker.Blocked b : party.blocked(50)) {
            Row r = new Row();
            r.text = EnumChatFormatting.GRAY + "[" + b.time + "] " + EnumChatFormatting.RED + b.name;
            r.sub = b.reason;
            r.buttonsOnHover = true;
            String name = b.name;
            r.buttons.add(new Button("Unblock", GREEN, () -> rt.unblock(name)));
            m.rows.add(r);
        }
        return m;
    }

    private static List<String> tooltip(Member member) {
        List<String> lines = new ArrayList<>();
        lines.add(EnumChatFormatting.LIGHT_PURPLE + member.name);
        if (member.stats != null) {
            for (String line : member.stats.describe(System.currentTimeMillis())) lines.add(EnumChatFormatting.GRAY + line);
        } else if (member.status == PartyTracker.Status.CHECKING) {
            lines.add(EnumChatFormatting.YELLOW + "Checking stats...");
        } else if (!member.error.isEmpty()) {
            lines.add(EnumChatFormatting.YELLOW + member.error);
        }
        if (member.status == PartyTracker.Status.TRUSTED) lines.add(EnumChatFormatting.AQUA + "Trusted - never flagged");
        for (String reason : member.reasons) lines.add(EnumChatFormatting.GOLD + "! " + reason);
        return lines;
    }

    private static String statusColor(Member m) {
        switch (m.status) {
            case CLEAN:
            case DISMISSED:
                return EnumChatFormatting.GREEN.toString();
            case TRUSTED:
                return EnumChatFormatting.AQUA.toString();
            case FLAGGED:
                return EnumChatFormatting.GOLD.toString();
            case REMOVED:
                return EnumChatFormatting.RED.toString();
            default:
                return EnumChatFormatting.YELLOW.toString();
        }
    }

    private static String detail(Member m) {
        switch (m.status) {
            case CHECKING:
                return "checking...";
            case UNAVAILABLE:
                return "unchecked";
            case REMOVED:
                return "removing";
            case FLAGGED:
                return "flagged";
            default:
                return m.stats == null ? "" : m.stats.brief();
        }
    }

    // ------------------------------------------------------------------ positions (stored in OneConfig)

    private static float[] position(Id id, ScaledResolution sr) {
        PartyConfig c = PartyMod.config;
        float fx, fy;
        switch (id) {
            case LOG: fx = c.logX; fy = c.logY; break;
            case MEMBERS: fx = c.membersX; fy = c.membersY; break;
            case REVIEW: fx = c.reviewX; fy = c.reviewY; break;
            default: fx = c.blockedX; fy = c.blockedY; break;
        }
        return new float[]{fx * sr.getScaledWidth(), fy * sr.getScaledHeight()};
    }

    private static void move(Id id, float x, float y, ScaledResolution sr) {
        PartyConfig c = PartyMod.config;
        float fx = clamp(x / sr.getScaledWidth(), 0, 0.98f), fy = clamp(y / sr.getScaledHeight(), 0, 0.98f);
        switch (id) {
            case LOG: c.logX = fx; c.logY = fy; break;
            case MEMBERS: c.membersX = fx; c.membersY = fy; break;
            case REVIEW: c.reviewX = fx; c.reviewY = fy; break;
            default: c.blockedX = fx; c.blockedY = fy; break;
        }
    }

    private static float clamp(float v, float min, float max) {
        return max < min ? min : Math.max(min, Math.min(max, v));
    }
}
