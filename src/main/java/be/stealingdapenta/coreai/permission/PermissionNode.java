package be.stealingdapenta.coreai.permission;

/**
 * Centralized registry of all permission nodes used by CoreAI.
 */
public enum PermissionNode {
    /**
     * Allows players to set their personal OpenAI API key.
     */
    SET_API_KEY("coreai.setapikey"),

    /**
     * Allows chatting with the AI.
     */
    CHAT("coreai.chat"),

    /**
     * Allows opening the model selection GUI.
     */
    MODELS("coreai.models"),

    /**
     * Allows using the /modelinfo command to set the AI model.
     */
    MODEL_INFO("coreai.modelinfo"),

    /**
     * Allows using the /imagemap command to create a map from an image URL.
     */
    IMAGE_MAP("coreai.imagemap"),

    /**
     * Allows using the /imagegenmap command to create a map from an image URL.
     */
    IMAGE_GEN_MAP("coreai.imagegenmap");

    private final String node;


    PermissionNode(String node) {
        this.node = node;

    }

    /**
     * Returns the permission node string, e.g. "coreai.chat".
     */
    public String node() {
        return node;
    }

}
