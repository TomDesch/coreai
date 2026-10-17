package be.stealingdapenta.coreai.listener;

import static be.stealingdapenta.coreai.manager.SessionManager.SESSION_MANAGER;
import static be.stealingdapenta.coreai.util.ChatMessages.API_KEY_ENTRY_CANCELLED;
import static be.stealingdapenta.coreai.util.ChatMessages.API_KEY_ENTRY_EXPIRED;
import static be.stealingdapenta.coreai.util.ChatMessages.API_KEY_ENTRY_PROMPT;
import static be.stealingdapenta.coreai.util.ChatMessages.API_KEY_SAVE_FAILED;
import static be.stealingdapenta.coreai.util.ChatMessages.API_KEY_STORED;
import static be.stealingdapenta.coreai.util.ChatMessages.API_KEY_TOO_LONG;
import static be.stealingdapenta.coreai.util.ChatMessages.BUSY;
import static be.stealingdapenta.coreai.util.ChatMessages.NO_PERMISSION;
import static be.stealingdapenta.coreai.util.ChatMessages.send;

import be.stealingdapenta.coreai.CoreAI;
import be.stealingdapenta.coreai.permission.PermissionNode;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class AsyncApiKeyListener implements Listener {

    private static final int MAX_API_KEY_LENGTH = 512;
    private final PendingKeyInput pending = new PendingKeyInput();

    public void beginKeyInput(Player player) {
        if (!player.hasPermission(PermissionNode.SET_API_KEY.node())) {
            send(player, NO_PERMISSION);
            return;
        }
        if (!pending.begin(player.getUniqueId(), System.currentTimeMillis())) {
            send(player, BUSY);
            return;
        }
        send(player, API_KEY_ENTRY_PROMPT);
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        PendingKeyInput.Claim claim = pending.claim(uuid, System.currentTimeMillis());
        if (claim == PendingKeyInput.Claim.NONE) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        if (claim == PendingKeyInput.Claim.EXPIRED) {
            CoreAI.getInstance().runMain(() -> sendIfOnline(player, uuid, API_KEY_ENTRY_EXPIRED));
            return;
        }

        String apiKey = event.getMessage().trim();
        if (apiKey.equalsIgnoreCase("cancel")) {
            CoreAI.getInstance().runMain(() -> sendIfOnline(player, uuid, API_KEY_ENTRY_CANCELLED));
            return;
        }
        if (apiKey.isEmpty()) {
            CoreAI.getInstance().runMain(() -> sendIfOnline(player, uuid, API_KEY_ENTRY_CANCELLED));
            return;
        }
        if (apiKey.length() > MAX_API_KEY_LENGTH) {
            CoreAI.getInstance().runMain(() -> sendIfOnline(player, uuid, API_KEY_TOO_LONG));
            return;
        }

        SESSION_MANAGER.setPlayerAPIKey(uuid, apiKey).whenComplete((ignored, error) ->
                CoreAI.getInstance().runMain(() -> {
                    if (Bukkit.getPlayer(uuid) != player || !player.isOnline()) {
                        return;
                    }
                    send(player, error == null ? API_KEY_STORED : API_KEY_SAVE_FAILED);
                }));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }

    public void clearPendingInputs() {
        pending.clear();
    }

    private static void sendIfOnline(Player player, UUID uuid, Component message) {
        if (Bukkit.getPlayer(uuid) == player && player.isOnline()) {
            send(player, message);
        }
    }
}
