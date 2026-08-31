package be.stealingdapenta.coreai.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ChatAgentTest {

    @Test
    void toStringRedactsApiKey() {
        ChatAgent agent = new ChatAgent("sk-1234567890", "gpt-4o-mini", 5000);

        String value = agent.toString();

        assertTrue(value.contains("apiKey="));
        assertTrue(value.contains("..."));
        assertFalse(value.contains("sk-1234567890"));
    }

    @Test
    void toStringHandlesEmptyToken() {
        ChatAgent agent = new ChatAgent("", "gpt-4o-mini", 5000);

        assertTrue(agent.toString().contains("apiKey=<unset>"));
    }
}
