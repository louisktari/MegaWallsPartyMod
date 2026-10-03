package fr.alexdoru.partymod.core;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the Hypixel server messages this mod reacts to. Patterns are anchored so
 * ordinary player chat can never be mistaken for a party or queue event.
 */
public final class ChatEvents {

    public enum Type { JOIN, SELF_JOIN, LEAVE, REMOVED, MEMBER_COUNT, MEMBER_LIST, ROLE_CHANGE, NOT_IN_PARTY, DISBAND, COMPETITIVE_BLOCK, THROTTLED, NONE }

    public static final class Event {
        public final Type type;
        /** Player name, or for MEMBER_LIST the raw list of names after the colon. */
        public final String value;
        /** For MEMBER_LIST and ROLE_CHANGE: the party role involved. */
        public final Role role;

        Event(Type type, String value) {
            this(type, value, null);
        }

        Event(Type type, String value, Role role) {
            this.type = type;
            this.value = value;
            this.role = role;
        }
    }

    public enum Role {
        LEADER, MODERATOR, MEMBER;

        static Role of(String word) {
            return word.startsWith("Leader") ? LEADER : word.startsWith("Moderator") ? MODERATOR : MEMBER;
        }
    }

    /** Optional rank prefix like "[MVP+] " followed by a Minecraft username. */
    private static final String NAME = "(?:\\[[^\\]]{1,24}\\] )?([A-Za-z0-9_]{1,16})";

    private static final Pattern JOIN = Pattern.compile("^" + NAME + " joined the party\\.$");
    private static final Pattern SELF_JOIN = Pattern.compile("^You have joined " + NAME + "'s? party!$");
    private static final Pattern LEAVE = Pattern.compile("^" + NAME + " has left the party\\.$");
    private static final Pattern REMOVED = Pattern.compile("^" + NAME + " (?:has been removed from the party|was removed from (?:your|the) party because they disconnected)\\.$");
    private static final Pattern MEMBER_COUNT = Pattern.compile("^Party Members \\((\\d{1,3})\\)$");
    private static final Pattern MEMBER_LIST = Pattern.compile("^Party (Leader|Moderators|Members): (.+)$");
    private static final Pattern PROMOTED = Pattern.compile("^" + NAME + " has (?:promoted|demoted) " + NAME + " to Party (Leader|Moderator|Member)[!.]?$");
    private static final Pattern TRANSFERRED = Pattern.compile("^The party was transferred to " + NAME + " by " + NAME + "[!.]?$");
    private static final Pattern COMPETITIVE = Pattern.compile("^You cannot queue for this mode due to " + NAME + " not being able to play competitive games!$");
    private static final Pattern DISBANDED_BY = Pattern.compile("^" + NAME + " has disbanded the party!$");
    private static final Pattern KICKED_BY = Pattern.compile("^You have been kicked from the party by " + NAME + "$");

    private ChatEvents() {}

    /**
     * Anything a player typed: lobby chat "[VIP] Name: hi", "Party > Name: hi", guild/officer/co-op,
     * DMs "From Name: hi", and channel tags like "[SHOUT] Name: hi". These are never parsed as
     * events, so a player can't fake a join, kick, transfer or competitive-ban line by typing it.
     */
    private static final Pattern PLAYER_CHAT = Pattern.compile(
            "^(?:(?:Party|Guild|Officer|Co-op|Friend) > |From |To |\\[[^\\]]{1,24}\\] )*"
                    + "(?:\\[[^\\]]{1,24}\\] )?[A-Za-z0-9_]{1,16}(?: \\[[^\\]]{1,24}\\])?: ");

    public static boolean isPlayerChat(String text) {
        return PLAYER_CHAT.matcher(text).find() && !MEMBER_LIST.matcher(text).matches();
    }

    public static Event parse(String raw) {
        if (raw == null) return new Event(Type.NONE, "");
        String text = stripFormatting(raw).trim();
        if (isPlayerChat(text)) return new Event(Type.NONE, "");
        Matcher m;
        if ((m = COMPETITIVE.matcher(text)).matches()) return new Event(Type.COMPETITIVE_BLOCK, m.group(1));
        if ((m = JOIN.matcher(text)).matches()) return new Event(Type.JOIN, m.group(1));
        if ((m = SELF_JOIN.matcher(text)).matches()) return new Event(Type.SELF_JOIN, m.group(1));
        if ((m = LEAVE.matcher(text)).matches()) return new Event(Type.LEAVE, m.group(1));
        if ((m = REMOVED.matcher(text)).matches()) return new Event(Type.REMOVED, m.group(1));
        if ((m = MEMBER_LIST.matcher(text)).matches()) return new Event(Type.MEMBER_LIST, m.group(2), Role.of(m.group(1)));
        if ((m = PROMOTED.matcher(text)).matches()) return new Event(Type.ROLE_CHANGE, m.group(2), Role.of(m.group(3)));
        if ((m = TRANSFERRED.matcher(text)).matches()) return new Event(Type.ROLE_CHANGE, m.group(1), Role.LEADER);
        if ((m = MEMBER_COUNT.matcher(text)).matches()) return new Event(Type.MEMBER_COUNT, m.group(1));
        if (text.equals("That player is not in your party!")) return new Event(Type.NOT_IN_PARTY, "");
        if (DISBANDED_BY.matcher(text).matches() || KICKED_BY.matcher(text).matches()
                || text.startsWith("The party was disbanded")
                || text.equals("You left the party.")
                || text.equals("You have disbanded the party!")
                || text.equals("You are not currently in a party.")) {
            return new Event(Type.DISBAND, "");
        }
        if (text.equals("You are sending commands too fast! Please slow down.")
                || text.equals("Woah slow down, you're doing that too fast!")) {
            return new Event(Type.THROTTLED, "");
        }
        return new Event(Type.NONE, "");
    }

    /** Splits a "/p list" row such as "[MVP+] Alice ● Bob ●" into usernames. */
    public static java.util.List<String> names(String row) {
        java.util.List<String> result = new java.util.ArrayList<>();
        String plain = row.replaceAll("\\[[^\\]]*\\]", " ").replaceAll("[\\u25cf\\u25cb,]", " ");
        for (String token : plain.trim().split("\\s+")) {
            if (validName(token)) result.add(token);
        }
        return result;
    }

    /**
     * The player's name exactly as Hypixel coloured it in a message, including the rank
     * prefix - e.g. "§b[MVP§c+§b] Steve" from "§b[MVP§c+§b] Steve §ejoined the party.".
     * Returns null if the name isn't in the message.
     */
    public static String formattedName(String formatted, String name) {
        if (formatted == null || name == null) return null;
        int at = formatted.lastIndexOf(name);
        if (at < 0) return null;
        int colon = formatted.lastIndexOf(": ", at), bullet = formatted.lastIndexOf('\u25cf', at);
        int start = Math.max(formatted.lastIndexOf('\n', at) + 1,
                Math.max(colon < 0 ? 0 : colon + 2, bullet < 0 ? 0 : bullet + 1));
        if (start > at) start = formatted.lastIndexOf('\n', at) + 1;
        String shown = formatted.substring(start, at + name.length());
        shown = shown.replaceAll("^((?:\u00a7.)*)\\s+", "$1");
        while (shown.startsWith("\u00a7r")) shown = shown.substring(2);
        shown = shown.trim();
        return stripFormatting(shown).trim().endsWith(name) ? shown : null;
    }

    public static boolean validName(String value) {
        return value != null && value.matches("[A-Za-z0-9_]{1,16}");
    }

    public static String stripFormatting(String text) {
        return text.replaceAll("(?i)\\u00a7[0-9A-FK-OR]", "");
    }

    public static boolean isHypixel(String serverIp) {
        if (serverIp == null) return false;
        String host = serverIp.toLowerCase(Locale.ROOT).trim().replaceFirst(":\\d+$", "");
        return host.equals("hypixel.net") || host.endsWith(".hypixel.net");
    }
}
