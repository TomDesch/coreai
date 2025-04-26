package be.stealingdapenta.coreai;

import be.stealingdapenta.coreai.service.ChatGPTService;
import org.bukkit.plugin.java.JavaPlugin;

public class CoreAI extends JavaPlugin {

    private String openAiKey;
    private String openAiModel;
    private int openAiTimeout;

    @Override
    public void onEnable() {
        // 1) Ensure the default config.yml is created
        saveDefaultConfig();

        // 2) Load values (with sensible defaults/fallbacks)
        openAiKey = getConfig().getString("openai.api-key", "")
                               .trim();
        openAiModel = getConfig().getString("openai.model", "gpt-3.5-turbo");
        openAiTimeout = getConfig().getInt("openai.timeout-ms", 60000);

        // 3) Optionally override with an environment variable
        String envKey = System.getenv("OPENAI_API_KEY");
        if (envKey != null && !envKey.isBlank()) {
            openAiKey = envKey.trim();
            getLogger().info("Using OpenAI key from environment");
        }

        // 4) Validate
        if (openAiKey.isEmpty()) {
            getLogger().severe("No OpenAI API key set! " + "Please either fill openai.api-key in config.yml or set the OPENAI_API_KEY env var.");
            getServer().getPluginManager()
                       .disablePlugin(this);
            return;
        }

        // 5) Now you can pass openAiKey/openAiModel/openAiTimeout
        //    into your ChatGPTService (that you’ll build next).
        ChatGPTService chatService = new ChatGPTService(openAiKey, openAiModel, openAiTimeout, getLogger());

        // …register commands, listeners, etc., handing them chatService…
        getLogger().info("CoreAI ready to roll!");
    }
}