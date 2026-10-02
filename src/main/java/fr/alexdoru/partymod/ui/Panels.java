package fr.alexdoru.partymod.ui;

import fr.alexdoru.partymod.PartyConfig;
import fr.alexdoru.partymod.PartyMod;
import fr.alexdoru.partymod.PartyRuntime;
import fr.alexdoru.partymod.core.Flagger;
import fr.alexdoru.partymod.core.PartyTracker;
import fr.alexdoru.partymod.core.PartyTracker.Member;
import fr.alexdoru.partymod.core.PartyTracker.Status;
import fr.alexdoru.partymod.core.StatFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Four on-screen panels that behave like extra chat windows. Always visible while you
 * play; open chat (or press the overlay key) and they become interactive: drag a title
 * bar to move a panel, scroll with the mouse wheel, hover a player for full stats, and
 * click buttons to act.
 */
public final class Panels {

    public enum Id { REVIEW, MEMBERS, LOG, BLOCKED }

    private static final int LINE = 10, HEADER = 12, PAD = 3, CARD_GAP = 3;
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

    /** One entry: a title line (with badge or buttons on the right) plus optional detail lines. */
    static final class Card {
        String title = "", badge = "";
        final List<String> lines = new ArrayList<>();
        /** Wrap long detail lines instead of trimming them. */
        boolean wrap = true;
        final List<Button> buttons = new ArrayList<>();
        /** Buttons replace the badge only while hovered, so busy panels stay calm. */
        boolean buttonsOnHover;
        List<String> tooltip = Collections.emptyList();
    }

    static final class Model {
        final Id id;
        final String title;
        final List<Button> toolbar = new ArrayList<>();
        final List<Card> cards = new ArrayList<>();
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

        Hit(float x1, float y1, float x2, float y2, Runnable action, Id dragHandle) {
            this.x1 = (int) x1;
            this.y1 = (int) y1;
            this.x2 = (int) Math.ceil(x2);
            this.y2 = (int) Math.ceil(y2);
            this.action = action;
            this.dragHandle = dragHandle;
        }

        boolean contains(int x, int y) {
            return x >= x1 && x < x2 && y >= y1 && y < y2;
        }
    }

    private final List<Hit> hits = new ArrayList<>();
    private final Map<Id, Hit> bounds = new EnumMap<>(Id.class);
    private final Map<Id, Integer> scroll = new EnumMap<>(Id.class);
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
        if (!(event.gui instanceof OverlayScreen) && !PartyMod.runtime.active()) return;
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
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            for (Map.Entry<Id, Hit> e : bounds.entrySet()) {
                if (e.getValue().contains(x, y)) {
                    scroll.put(e.getKey(), Math.max(0, scroll.getOrDefault(e.getKey(), 0) + (wheel < 0 ? 1 : -1)));
                    event.setCanceled(true);
                    return;
                }
            }
        }
        if (button == 0 && Mouse.getEventButtonState()) {
            if (press(x, y, sr)) event.setCanceled(true);
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

    private boolean press(int x, int y, ScaledResolution sr) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (!hit.contains(x, y)) continue;
            if (hit.dragHandle != null) {
                dragging = hit.dragHandle;
                float[] pos = position(hit.dragHandle, sr);
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

    // ------------------------------------------------------------------ drawing

    private void draw(ScaledResolution sr, int mouseX, int mouseY, boolean interactive) {
        PartyConfig c = PartyMod.config;
        if (c == null) return;
        hits.clear();
        bounds.clear();
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
            String hint = "Drag title bars to move  -  scroll to see more  -  hover players for stats  -  ESC to close";
            fr.drawStringWithShadow(hint, (sr.getScaledWidth() - fr.getStringWidth(hint)) / 2f, sr.getScaledHeight() - 12, MUTED);
        }
    }

    /** A card laid out for the current width: title line + wrapped detail lines. */
    private static List<String> detailLines(Card card, FontRenderer fr, int width) {
        List<String> out = new ArrayList<>();
        for (String line : card.lines) {
            if (card.wrap) out.addAll(fr.listFormattedStringToWidth(line, width));
            else out.add(fr.trimStringToWidth(line, width));
        }
        return out;
    }

    private void draw(Model model, ScaledResolution sr, int mouseX, int mouseY, boolean interactive) {
        PartyConfig c = PartyMod.config;
        if (!interactive && c.hideEmpty && model.cards.isEmpty()) return;
        FontRenderer fr = Minecraft.getMinecraft().fontRendererObj;
        float scale = c.panelScale;
        int width = (int) c.panelWidth;
        int textWidth = width - PAD * 2;
        int maxCards = (int) c.maxRows;

        int first = Math.max(0, Math.min(scroll.getOrDefault(model.id, 0), model.cards.size() - maxCards));
        scroll.put(model.id, first);
        List<Card> visible = model.cards.subList(first, Math.min(model.cards.size(), first + maxCards));
        List<List<String>> details = new ArrayList<>();
        for (Card card : visible) details.add(detailLines(card, fr, textWidth - 4));

        boolean toolbar = interactive && !model.toolbar.isEmpty();
        int height = HEADER + (toolbar ? LINE + 2 : 0) + PAD;
        if (first > 0) height += LINE;
        for (List<String> d : details) height += LINE * (1 + d.size()) + CARD_GAP;
        int below = model.cards.size() - first - visible.size();
        if (visible.isEmpty() || below > 0) height += LINE;
        height += PAD - CARD_GAP + 1;

        float[] pos = position(model.id, sr);
        float px = clamp(pos[0], 0, sr.getScaledWidth() - width * scale);
        float py = clamp(pos[1], 0, sr.getScaledHeight() - height * scale);
        int lx = mouseX < 0 ? -1 : (int) ((mouseX - px) / scale);
        int ly = mouseY < 0 ? -1 : (int) ((mouseY - py) / scale);
        bounds.put(model.id, new Hit(px, py, px + width * scale, py + height * scale, null, null));
        int firstHit = hits.size();

        GlStateManager.pushMatrix();
        GlStateManager.translate(px, py, 0);
        GlStateManager.scale(scale, scale, 1);
        int alpha = Math.max(0, Math.min(255, (int) (c.backgroundOpacity * 255)));
        Gui.drawRect(0, 0, width, height, alpha << 24 | 0x0E0E16);
        boolean flash = model.alert && (System.currentTimeMillis() / 500) % 2 == 0;
        Gui.drawRect(0, 0, width, HEADER, Math.min(255, alpha + 70) << 24 | (flash ? 0x5A4A2A : 0x2A2140));
        Gui.drawRect(0, HEADER - 1, width, HEADER, 0xFF000000 | (model.alert ? 0xC89B3C : 0x7A55C8));
        fr.drawStringWithShadow(fr.trimStringToWidth(model.title, textWidth), PAD, 2, model.alert ? GOLD : ACCENT);

        int y = HEADER + 2;
        if (toolbar) {
            int bx = PAD;
            for (int i = 0; i < model.toolbar.size(); i++) {
                bx = drawButtonLeft(model.toolbar.get(i), bx, y, lx, ly, px, py, scale) + 4;
            }
            y += LINE + 2;
        }
        if (interactive) hits.add(firstHit, new Hit(px, py, px + width * scale, py + HEADER * scale, null, model.id));

        if (first > 0) {
            fr.drawStringWithShadow("^ " + first + " more above", PAD, y, MUTED);
            y += LINE;
        }
        for (int i = 0; i < visible.size(); i++) {
            Card card = visible.get(i);
            List<String> d = details.get(i);
            int cardHeight = LINE * (1 + d.size());
            boolean hover = interactive && lx >= 0 && lx < width && ly >= y - 1 && ly < y + cardHeight;
            if (hover) {
                Gui.drawRect(1, y - 1, width - 1, y + cardHeight - 1, 0x28FFFFFF);
                if (!card.tooltip.isEmpty()) {
                    tooltip = card.tooltip;
                    tooltipX = mouseX;
                    tooltipY = mouseY;
                }
            }
            int titleRight = width - PAD;
            if (interactive && !card.buttons.isEmpty() && (!card.buttonsOnHover || hover)) {
                for (int b = card.buttons.size() - 1; b >= 0; b--) {
                    titleRight = drawButtonRight(card.buttons.get(b), titleRight, y, lx, ly, px, py, scale) - 3;
                }
            } else if (!card.badge.isEmpty()) {
                titleRight -= fr.getStringWidth(card.badge);
                fr.drawStringWithShadow(card.badge, titleRight, y, TEXT);
                titleRight -= 4;
            }
            fr.drawStringWithShadow(fr.trimStringToWidth(card.title, Math.max(20, titleRight - PAD)), PAD, y, TEXT);
            for (int l = 0; l < d.size(); l++) fr.drawStringWithShadow(d.get(l), PAD + 4, y + LINE * (l + 1), TEXT);
            y += cardHeight + CARD_GAP;
        }
        if (visible.isEmpty()) fr.drawStringWithShadow(model.empty, PAD, y, MUTED);
        else if (below > 0) fr.drawStringWithShadow("v " + below + " more" + (interactive ? " - scroll" : ""), PAD, y - CARD_GAP + 1, MUTED);
        GlStateManager.popMatrix();
    }

    private String label(Button b) {
        return "[" + (b.confirmKey != null && armed.equals(b.confirmKey) ? "Sure?" : b.label) + "]";
    }

    private int drawButtonRight(Button b, int right, int y, int lx, int ly, float px, float py, float scale) {
        int left = right - Minecraft.getMinecraft().fontRendererObj.getStringWidth(label(b));
        drawButton(b, left, y, lx, ly, px, py, scale);
        return left;
    }

    private int drawButtonLeft(Button b, int left, int y, int lx, int ly, float px, float py, float scale) {
        return drawButton(b, left, y, lx, ly, px, py, scale);
    }

    /** Draws a [label] button at {@code left}; returns its right edge. */
    private int drawButton(Button b, int left, int y, int lx, int ly, float px, float py, float scale) {
        FontRenderer fr = Minecraft.getMinecraft().fontRendererObj;
        String label = label(b);
        int right = left + fr.getStringWidth(label);
        boolean hover = lx >= left - 1 && lx < right + 1 && ly >= y - 1 && ly < y + 9;
        boolean isArmed = b.confirmKey != null && armed.equals(b.confirmKey);
        if (hover) Gui.drawRect(left - 1, y - 1, right + 1, y + 9, 0x40FFFFFF);
        fr.drawStringWithShadow(label, left, y, hover ? 0xFFFFFFFF : isArmed ? RED : b.color);
        Runnable action = b.confirmKey == null ? b.action : () -> {
            if (armed.equals(b.confirmKey)) {
                armed = "";
                b.action.run();
            } else {
                armed = b.confirmKey;
                armedAt = System.currentTimeMillis();
            }
        };
        hits.add(new Hit(px + (left - 1) * scale, py + (y - 1) * scale, px + (right + 1) * scale, py + (y + 9) * scale, action, null));
        return right;
    }

    private void drawTooltip(List<String> lines, int x, int y, ScaledResolution sr) {
        FontRenderer fr = Minecraft.getMinecraft().fontRendererObj;
        int w = 0;
        for (String line : lines) w = Math.max(w, fr.getStringWidth(line));
        int h = lines.size() * 10 + 2;
        int tx = x + 12, ty = y - 6;
        if (tx + w + 6 > sr.getScaledWidth()) tx = Math.max(2, x - w - 14);
        if (ty + h > sr.getScaledHeight()) ty = sr.getScaledHeight() - h - 2;
        if (ty < 4) ty = 4;
        GlStateManager.pushMatrix();
        GlStateManager.translate(0, 0, 400);
        Gui.drawRect(tx - 3, ty - 3, tx + w + 3, ty + h, 0xF0100010);
        Gui.drawRect(tx - 3, ty - 3, tx + w + 3, ty - 2, 0xFF7A55C8);
        for (int i = 0; i < lines.size(); i++) fr.drawStringWithShadow(lines.get(i), tx, ty + i * 10, TEXT);
        GlStateManager.popMatrix();
    }

    // ------------------------------------------------------------------ panel content

    private static Flagger.Options options() {
        return PartyMod.config.flagOptions();
    }

    /** The stat card shared by the review queue and member list. */
    private static Card playerCard(Member m, boolean showReasons) {
        Card card = new Card();
        long now = System.currentTimeMillis();
        card.title = (m.isNew(now) ? "\u00a7d\u00a7lNEW " : "") + StatFormat.rankedName(m.stats, m.name);
        switch (m.status) {
            case CHECKING:
                card.badge = StatFormat.YELLOW + "checking...";
                break;
            case UNAVAILABLE:
                card.badge = StatFormat.YELLOW + "unchecked";
                card.lines.add(StatFormat.YELLOW + m.error);
                break;
            case FLAGGED:
                card.badge = StatFormat.RED + "[" + m.reasons.size() + (m.reasons.size() == 1 ? " FLAG]" : " FLAGS]");
                break;
            case TRUSTED:
                card.badge = StatFormat.AQUA + "[TRUSTED]";
                break;
            case DISMISSED:
                card.badge = StatFormat.GREEN + "[KEPT]";
                break;
            case REMOVED:
                card.badge = StatFormat.RED + "[REMOVING]";
                break;
            default:
                card.badge = StatFormat.GREEN + "[OK]";
                break;
        }
        card.lines.addAll(StatFormat.card(m.stats, options(), now));
        if (showReasons && !m.reasons.isEmpty()) card.lines.add(StatFormat.GOLD + "! " + String.join(", ", m.reasons));
        card.tooltip = tooltip(m, now);
        return card;
    }

    private Model review(PartyTracker party) {
        PartyRuntime rt = PartyMod.runtime;
        List<Member> queue = party.toReview();
        Model m = new Model(Id.REVIEW, "To review" + (queue.isEmpty() ? "" : " (" + queue.size() + ")"));
        m.alert = !queue.isEmpty();
        m.empty = "No suspicious players";
        if (queue.size() > 1) m.toolbar.add(new Button("Kick all " + queue.size(), RED, rt::removeAllFlagged, "kick-all"));
        for (Member member : queue) {
            Card card = playerCard(member, true);
            String name = member.name;
            card.buttons.add(new Button("Kick", RED, () -> rt.remove(name, String.join(", ", member.reasons) + " - " + rt.removedBy(), false)));
            card.buttons.add(new Button("Keep", GREEN, () -> rt.dismiss(name)));
            card.buttons.add(new Button("Trust", BLUE, () -> rt.trust(name)));
            m.cards.add(card);
        }
        return m;
    }

    private Model members(PartyTracker party) {
        PartyRuntime rt = PartyMod.runtime;
        Model m = new Model(Id.MEMBERS, "Party" + (party.members().isEmpty() ? "" : " (" + party.members().size() + ")"));
        m.empty = "Not in a party";
        m.toolbar.add(new Button("Sync", ACCENT, rt::syncParty));
        m.toolbar.add(new Button("Re-check all", ACCENT, rt::recheckAll));
        m.toolbar.add(new Button("Settings", MUTED, () -> PartyMod.config.openGui()));
        // Newest first so the player who just joined is always at the top.
        List<Member> list = new ArrayList<>(party.members());
        Collections.reverse(list);
        for (Member member : list) {
            Card card = playerCard(member, false);
            card.buttonsOnHover = true;
            String name = member.name;
            if (member.status != Status.REMOVED) {
                card.buttons.add(new Button("Kick", RED, () -> rt.remove(name, rt.removedBy(), false)));
            }
            if (member.status == Status.TRUSTED) card.buttons.add(new Button("Untrust", MUTED, () -> rt.untrust(name)));
            else card.buttons.add(new Button("Trust", BLUE, () -> rt.trust(name)));
            card.buttons.add(new Button("Check", ACCENT, () -> rt.recheck(name)));
            m.cards.add(card);
        }
        return m;
    }

    private Model log(PartyTracker party) {
        Model m = new Model(Id.LOG, "Party log");
        m.toolbar.add(new Button("Clear", MUTED, party::clearLog));
        for (String line : party.log(100)) {
            Card card = new Card();
            // Log entries use the title slot only; long ones wrap onto detail lines.
            List<String> wrapped = Minecraft.getMinecraft().fontRendererObj.listFormattedStringToWidth(line, (int) PartyMod.config.panelWidth - PAD * 2);
            card.title = wrapped.isEmpty() ? "" : wrapped.get(0);
            for (int i = 1; i < wrapped.size(); i++) card.lines.add(wrapped.get(i));
            m.cards.add(card);
        }
        return m;
    }

    private Model blocked(PartyTracker party) {
        PartyRuntime rt = PartyMod.runtime;
        Model m = new Model(Id.BLOCKED, "Blocked this session");
        m.empty = "Nobody blocked yet";
        m.toolbar.add(new Button("Clear list", MUTED, party::clearBlocked));
        for (PartyTracker.Blocked b : party.blocked(100)) {
            Card card = new Card();
            card.title = StatFormat.RED + b.name;
            card.badge = StatFormat.DARK_GRAY + b.time;
            card.lines.add(StatFormat.GRAY + b.reason);
            card.buttonsOnHover = true;
            String name = b.name;
            card.buttons.add(new Button("Unblock", GREEN, () -> rt.unblock(name)));
            m.cards.add(card);
        }
        return m;
    }

    private static List<String> tooltip(Member member, long now) {
        List<String> lines = new ArrayList<>();
        lines.add(StatFormat.rankedName(member.stats, member.name));
        if (member.stats != null) {
            lines.addAll(StatFormat.describe(member.stats, options(), now));
        } else if (member.status == Status.CHECKING) {
            lines.add(StatFormat.YELLOW + "Checking stats...");
        } else if (!member.error.isEmpty()) {
            lines.add(StatFormat.YELLOW + member.error);
        }
        if (member.status == Status.TRUSTED) lines.add(StatFormat.AQUA + "Trusted - never flagged");
        for (String reason : member.reasons) lines.add(StatFormat.GOLD + "! " + reason);
        return lines;
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
