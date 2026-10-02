package fr.alexdoru.partymod.ui;

import cc.polyfrost.oneconfig.config.annotations.Slider;
import cc.polyfrost.oneconfig.config.annotations.Switch;
import cc.polyfrost.oneconfig.hud.TextHud;
import fr.alexdoru.partymod.PartyMod;
import fr.alexdoru.partymod.core.PartyTracker;
import fr.alexdoru.partymod.core.PartyTracker.Member;
import net.minecraft.util.EnumChatFormatting;

import java.util.List;

/**
 * Four independent on-screen panels, each movable and scalable through OneConfig's
 * HUD editor - like extra chat windows dedicated to the party.
 */
public final class Overlays {
    private static final String GRAY = EnumChatFormatting.GRAY.toString();
    private static final String WHITE = EnumChatFormatting.WHITE.toString();

    private Overlays() {}

    /** Shared behaviour: a title line, then up to {@code maxLines} entries. */
    public abstract static class Panel extends TextHud {
        @Slider(name = "Max lines", min = 1, max = 30, step = 1)
        public float maxLines = 8;

        @Switch(name = "Hide when empty")
        public boolean hideWhenEmpty = false;

        private final transient String title;

        Panel(String title, int x, int y) {
            super(true, x, y);
            this.title = title;
        }

        @Override
        protected void getLines(List<String> lines, boolean example) {
            int start = lines.size();
            if (example) {
                example(lines);
            } else if (PartyMod.runtime != null && PartyMod.runtime.active()) {
                body(lines, PartyMod.runtime.party, (int) maxLines);
            } else {
                return;
            }
            if (lines.size() == start) {
                if (hideWhenEmpty) return;
                lines.add(GRAY + "Nothing yet");
            }
            lines.add(start, EnumChatFormatting.LIGHT_PURPLE + "" + EnumChatFormatting.BOLD + title);
        }

        abstract void body(List<String> lines, PartyTracker party, int max);

        abstract void example(List<String> lines);
    }

    public static final class LogHud extends Panel {
        public LogHud() {
            super("Party log", 4, 40);
        }

        @Override
        void body(List<String> lines, PartyTracker party, int max) {
            lines.addAll(party.log(max));
        }

        @Override
        void example(List<String> lines) {
            lines.add(GRAY + "[21:04] " + WHITE + "Steve" + GRAY + " joined");
            lines.add(GRAY + "[21:04] " + EnumChatFormatting.GOLD + "Alex" + GRAY + " flagged: New account (3d old)");
            lines.add(GRAY + "[21:05] " + EnumChatFormatting.RED + "Blocking + kicking Alex");
        }
    }

    public static final class MembersHud extends Panel {
        public MembersHud() {
            super("Party members", 4, 140);
        }

        @Override
        void body(List<String> lines, PartyTracker party, int max) {
            int shown = 0;
            for (Member m : party.members()) {
                if (shown++ >= max) break;
                lines.add(statusColor(m) + m.name + GRAY + "  " + detail(m));
            }
        }

        @Override
        void example(List<String> lines) {
            lines.add(EnumChatFormatting.GREEN + "Steve" + GRAY + "  [MVP+] 2,104 games");
            lines.add(EnumChatFormatting.GOLD + "Alex" + GRAY + "  flagged");
            lines.add(EnumChatFormatting.YELLOW + "Notch" + GRAY + "  checking...");
        }

        private static String statusColor(Member m) {
            switch (m.status) {
                case CLEAN:
                case DISMISSED:
                    return EnumChatFormatting.GREEN.toString();
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
                case FLAGGED:
                    return "flagged";
                case REMOVED:
                    return "removing";
                default:
                    if (m.stats == null) return "";
                    String rank = m.stats.rank == null ? "" : "[" + m.stats.rank + "] ";
                    return rank + String.format("%,d games", m.stats.games() == null ? 0 : m.stats.games());
            }
        }
    }

    public static final class ReviewHud extends Panel {
        public ReviewHud() {
            super("To review", 4, 240);
        }

        @Override
        void body(List<String> lines, PartyTracker party, int max) {
            int shown = 0;
            for (Member m : party.toReview()) {
                if (shown++ >= max) break;
                lines.add(EnumChatFormatting.GOLD + m.name + GRAY + "  " + String.join(", ", m.reasons));
            }
            if (!party.toReview().isEmpty()) lines.add(GRAY + "/mwp remove <name>  or  /mwp dismiss <name>");
        }

        @Override
        void example(List<String> lines) {
            lines.add(EnumChatFormatting.GOLD + "Alex" + GRAY + "  New account (3d old), No rank");
            lines.add(GRAY + "/mwp remove <name>  or  /mwp dismiss <name>");
        }
    }

    public static final class BlockedHud extends Panel {
        public BlockedHud() {
            super("Blocked players", 4, 320);
        }

        @Override
        void body(List<String> lines, PartyTracker party, int max) {
            for (PartyTracker.Blocked b : party.blocked(max)) {
                lines.add(GRAY + "[" + b.time + "] " + EnumChatFormatting.RED + b.name + GRAY + "  " + b.reason);
            }
        }

        @Override
        void example(List<String> lines) {
            lines.add(GRAY + "[21:05] " + EnumChatFormatting.RED + "Alex" + GRAY + "  Cannot play competitive games");
        }
    }
}
