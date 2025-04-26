package be.stealingdapenta.coreai.permission;

import org.bukkit.permissions.PermissionDefault;

/**
 * Centralized registry of all permission nodes used by CoreAI.
 */
public enum PermissionNode {
    /**
     * Allows players to set their personal OpenAI API key.
     */
    SET_API_KEY("coreai.setapikey", PermissionDefault.OP, "Allows setting your personal OpenAI API key"),

    /**
     * Allows chatting with the AI.
     */
    CHAT("coreai.chat", PermissionDefault.TRUE, "Allows using /chat to talk with the AI"),

    /**
     * Allows opening the model selection GUI.
     */
    MODELS("coreai.models", PermissionDefault.TRUE, "Allows selecting the AI model via GUI");

    private final String node;
    private final PermissionDefault defaultValue;
    private final String description;

    PermissionNode(String node, PermissionDefault defaultValue, String description) {
        this.node = node;
        this.defaultValue = defaultValue;
        this.description = description;
    }

    /**
     * Returns the permission node string, e.g. "coreai.chat".
     */
    public String node() {
        return node;
    }

    /**
     * Returns the Bukkit {@link PermissionDefault} value for this node.
     */
    public PermissionDefault defaultValue() {
        return defaultValue;
    }

    /**
     * Returns a human-readable description for this permission.
     */
    public String description() {
        return description;
    }
}
