package fr.alexdoru.partymod;

import fr.alexdoru.partymod.core.ChatEvents;
import fr.alexdoru.partymod.core.ChatEvents.Type;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
    public void throttleMessagesRecognised() {
        assertEquals(Type.THROTTLED, ChatEvents.parse("You are sending commands too fast! Please slow down.").type);
    }

    @Test
    public void hypixelHostDetection() {
        assertTrue(ChatEvents.isHypixel("mc.hypixel.net"));
        assertTrue(ChatEvents.isHypixel("hypixel.net:25565"));
        assertFalse(ChatEvents.isHypixel("hypixel.net.evil.com"));
        assertFalse(ChatEvents.isHypixel(null));
    }
}
