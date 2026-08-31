package be.stealingdapenta.coreai.listener;

import static be.stealingdapenta.coreai.manager.SessionManager.SESSION_MANAGER;
import static be.stealingdapenta.coreai.util.ChatMessages.API_KEY_ENTRY_CANCELLED;
import static be.stealingdapenta.coreai.util.ChatMessages.API_KEY_ENTRY_PROMPT;
import static be.stealingdapenta.coreai.util.ChatMessages.API_KEY_STORED;

import static be.stealingdapenta.coreai.util.ChatMessages.send;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChatEvent;

public class AsyncApiKeyListener implements Listener {

    public static final String SET_API_KEY = "setapikey";
    private static final Set<UUID> awaitingKeyInput = ConcurrentHashMap.newKeySet();

    /**
     * Intercepts setapikey typed in chat and starts key input session.
     */
    @EventHandler
    public void onChat(PlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String msg = event.getMessage()
                          .trim();

        if (awaitingKeyInput.contains(uuid)) {
            // They are typing their API key
            event.setCancelled(true);
            awaitingKeyInput.remove(uuid);

            String apiKey = msg.trim();
            if (apiKey.isEmpty()) { // can theoretically never happen
                send(player, API_KEY_ENTRY_CANCELLED);
                return;
            }

            SESSION_MANAGER.setPlayerAPIKey(uuid, apiKey);
            send(player, API_KEY_STORED);
            return;
        }

        if (msg.equalsIgnoreCase(SET_API_KEY)) {
            // Player initiates key setting
            event.setCancelled(true);
            awaitingKeyInput.add(uuid);
            send(player, API_KEY_ENTRY_PROMPT);
        }
    }
}
