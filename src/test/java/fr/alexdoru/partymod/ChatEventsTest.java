package fr.alexdoru.partymod;

import fr.alexdoru.partymod.core.ChatEvents;
import fr.alexdoru.partymod.core.ChatEvents.Type;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ChatEventsTest {

    private static void assertEvent(String text, Type type, String value) {
        ChatEvents.Event e = ChatEvents.parse(text);
        assertEquals(text, type, e.type);
        assertEquals(text, value, e.value);
    }

    @Test
    public void competitiveRefusalGivesPlayerName() {
        assertEvent("You cannot queue for this mode due to WaterIntoWine not being able to play competitive games!",
                Type.COMPETITIVE_BLOCK, "WaterIntoWine");
        assertEvent("You cannot queue for this mode due to [MVP+] Water_2 not being able to play competitive games!",
                Type.COMPETITIVE_BLOCK, "Water_2");
    }

    @Test
    public void formattingCodesAreIgnored() {
        assertEvent("\u00a7cYou cannot queue for this mode due to \u00a7bWaterIntoWine\u00a7c not being able to play competitive games!",
                Type.COMPETITIVE_BLOCK, "WaterIntoWine");
        assertEvent("\u00a7b[MVP\u00a7c+\u00a7b] Steve \u00a7ejoined the party.", Type.JOIN, "Steve");
    }

    @Test
    public void partyMembershipMessages() {
        assertEvent("[VIP] Steve joined the party.", Type.JOIN, "Steve");
        assertEvent("Steve joined the party.", Type.JOIN, "Steve");
        assertEvent("You have joined [MVP++] Leader's party!", Type.SELF_JOIN, "Leader");
        assertEvent("[MVP+] Steve has left the party.", Type.LEAVE, "Steve");
        assertEvent("Steve has been removed from the party.", Type.REMOVED, "Steve");
        assertEvent("Steve was removed from your party because they disconnected.", Type.REMOVED, "Steve");
        assertEquals(Type.DISBAND, ChatEvents.parse("[MVP+] Leader has disbanded the party!").type);
        assertEquals(Type.DISBAND, ChatEvents.parse("The party was disbanded because all invites expired and the party was empty.").type);
        assertEquals(Type.DISBAND, ChatEvents.parse("You left the party.").type);
    }

    @Test
    public void playerChatIsNeverAnEvent() {
        assertEquals(Type.NONE, ChatEvents.parse("[VIP] Steve: Alex joined the party.").type);
        assertEquals(Type.NONE, ChatEvents.parse("Party > [VIP] Steve: You cannot queue for this mode due to Alex not being able to play competitive games!").type);
        assertEquals(Type.NONE, ChatEvents.parse("hello").type);
    }

    @Test
    public void partyListRowsSplitIntoNames() {
        ChatEvents.Event e = ChatEvents.parse("Party Members: [VIP] Steve \u25cf Alex \u25cf [MVP+] Notch_1 \u25cf");
        assertEquals(Type.MEMBER_LIST, e.type);
        assertEquals(Arrays.asList("Steve", "Alex", "Notch_1"), ChatEvents.names(e.value));
        assertEquals(Type.MEMBER_LIST, ChatEvents.parse("Party Leader: [MVP++] Leader \u25cf").type);
    }

    @Test
    public void roleChangesRecognised() {
        ChatEvents.Event promote = ChatEvents.parse("[MVP++] Louis has promoted [MVP++] johnush to Party Moderator");
        assertEquals(Type.ROLE_CHANGE, promote.type);
        assertEquals("johnush", promote.value);
        assertEquals(ChatEvents.Role.MODERATOR, promote.role);
        ChatEvents.Event demote = ChatEvents.parse("Louis has demoted johnush to Party Member");
        assertEquals(ChatEvents.Role.MEMBER, demote.role);
        ChatEvents.Event transfer = ChatEvents.parse("The party was transferred to [VIP] Alex by [MVP+] Louis");
        assertEquals("Alex", transfer.value);
        assertEquals(ChatEvents.Role.LEADER, transfer.role);
        ChatEvents.Event mods = ChatEvents.parse("Party Moderators: [MVP++] johnush \u25cf");
        assertEquals(Type.MEMBER_LIST, mods.type);
        assertEquals(ChatEvents.Role.MODERATOR, mods.role);
        assertEquals(ChatEvents.Role.LEADER, ChatEvents.parse("Party Leader: [MVP++] Louis \u25cf").role);
        // Player chat that merely quotes it is ignored.
        assertEquals(Type.NONE, ChatEvents.parse("Party > Louis: has promoted Alex to Party Moderator").type);
    }

    @Test
    public void typedChatCannotSpoofAnyEvent() {
        String[] payloads = {
                "The party was transferred to Evil by Louis",
                "You cannot queue for this mode due to Victim not being able to play competitive games!",
                "Steve joined the party.",
                "Steve has left the party.",
                "Steve has been removed from the party.",
                "Louis has promoted Evil to Party Moderator",
                "The party was disbanded because all invites expired and the party was empty.",
                "Party Members (1)",
                "Party Leader: Evil",
                "That player is not in your party!",
                "You are sending commands too fast! Please slow down.",
        };
        String[] wrappers = {
                "Evil: %s", "[VIP] Evil: %s", "[MVP+] Evil_2: %s", "Party > [MVP++] Evil: %s", "Party > Evil: %s",
                "Guild > [VIP] Evil [MOD]: %s", "Officer > Evil: %s", "From [MVP+] Evil: %s", "To Evil: %s",
                "Co-op > Evil: %s", "[SHOUT] [VIP] Evil: %s", "[123] [MVP+] Evil: %s",
        };
        for (String payload : payloads) {
            for (String wrapper : wrappers) {
                String typed = String.format(wrapper, payload);
                assertEquals(typed, Type.NONE, ChatEvents.parse(typed).type);
                // Formatting codes don't help either (players can't type them, but be thorough).
                assertEquals(typed, Type.NONE, ChatEvents.parse("\u00a7b" + typed.replace(": ", "\u00a7f: ")).type);
            }
        }
    }

    @Test
    public void realSystemLinesStillParse() {
        assertEquals(Type.MEMBER_LIST, ChatEvents.parse("Party Leader: [MVP++] Louis \u25cf").type);
        assertEquals(Type.MEMBER_LIST, ChatEvents.parse("Party Members: Alex \u25cf Steve \u25cf").type);
        assertEquals(Type.ROLE_CHANGE, ChatEvents.parse("The party was transferred to Alex by Louis").type);
        assertEquals(Type.COMPETITIVE_BLOCK,
                ChatEvents.parse("You cannot queue for this mode due to Alex not being able to play competitive games!").type);
    }

    @Test
    public void publicPartyCreationRecognised() {
        assertEvent("Created a public party! Players can join with /party join Louis", Type.PARTY_CREATED, "Louis");
        assertEvent("Party is capped at 100 players.", Type.PARTY_CAP, "100");
        assertEvent("Party is capped at 10 players.", Type.PARTY_CAP, "10");
        assertEquals(Type.NONE, ChatEvents.parse("Party > Evil: Party is capped at 2 players.").type);
    }

    @Test
    public void throttleMessagesRecognised() {
        assertEquals(Type.THROTTLED, ChatEvents.parse("You are sending commands too fast! Please slow down.").type);
    }

    @Test
    public void syncAndKickFeedbackRecognised() {
        assertEvent("Party Members (3)", Type.MEMBER_COUNT, "3");
        assertEquals(Type.NOT_IN_PARTY, ChatEvents.parse("That player is not in your party!").type);
        assertEvent("[VIP] cold_feet was removed from the party because they disconnected.", Type.REMOVED, "cold_feet");
    }

    @Test
    public void formattedNameKeepsHypixelRankColours() {
        assertEquals("\u00a7b[MVP\u00a7c+\u00a7b] Steve",
                ChatEvents.formattedName("\u00a7r\u00a7b[MVP\u00a7c+\u00a7b] Steve \u00a7r\u00a7ejoined the party.\u00a7r", "Steve"));
        assertEquals("\u00a77Steve", ChatEvents.formattedName("\u00a79\u00a7m-----\n\u00a77Steve \u00a7ejoined the party.", "Steve"));
        assertEquals("\u00a7a[VIP] Alex", ChatEvents.formattedName("\u00a7eParty Members: \u00a7a[VIP] Alex\u00a7a \u25cf", "Alex"));
        assertNull(ChatEvents.formattedName("Someone else joined", "Steve"));
    }

    @Test
    public void hypixelHostDetection() {
        assertTrue(ChatEvents.isHypixel("mc.hypixel.net"));
        assertTrue(ChatEvents.isHypixel("hypixel.net:25565"));
        assertFalse(ChatEvents.isHypixel("hypixel.net.evil.com"));
        assertFalse(ChatEvents.isHypixel(null));
    }
}
