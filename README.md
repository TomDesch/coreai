# CoreAI

CoreAI adds AI chat, model selection, image-to-map rendering, and AI image
generation to Minecraft servers. It supports Spigot and Paper 26.3 and targets
Java 21 bytecode. Java 25 or newer is recommended for Paper; Java 26 has been
verified as the runtime for both Paper and Spigot 26.3. Building has been
verified with JDK 27.

## Install

1. Download the shaded `coreai-1.2.0.jar` from a release or build it as
   described below.
2. Place the JAR in the server's `plugins` directory and start the server.
3. Configure an optional server-wide OpenAI API key in
   `plugins/CoreAI/config.yml`, or let players set personal keys with
   `/setapikey`.

The configuration file is created on first start and is not overwritten on
subsequent starts. Personal API keys are encrypted on disk. Back up the
`plugins/CoreAI` data directory together with the server; losing its encryption
key makes saved personal keys unavailable.

## Configuration

```yaml
openai:
  api-key: ""            # Optional server-wide key
  model: "gpt-6-astra"   # Default model; players can choose another with /models
  timeout-ms: 60000      # Chat request timeout
  timeout-image-ms: 300000 # Image-generation request timeout

cleanup:
  auto-enabled: false    # Age-based cleanup is opt-in
  max-age-days: 30
```

Invalid or out-of-range settings are replaced with safe defaults in memory and
reported in the server log. Age-based cleanup tracks maps in online player
inventories and ender chests, item frames, dropped items, and loaded
containers, including maps nested in shulker boxes and bundles. Maps kept only
in offline inventories or unloaded chunks cannot be observed while absent. If
those maps must be preserved, keep cleanup disabled.

## Commands and permissions

| Command                                    | Description                                                                                              | Permission (default)            |
|--------------------------------------------|----------------------------------------------------------------------------------------------------------|---------------------------------|
| `/chat <message>`                          | Send a message to the configured AI model.                                                               | `coreai.chat` (everyone)        |
| `/setapikey`                               | Start the private personal-key setup prompt. Enter the key in chat; do not put it in a command argument. | `coreai.setapikey` (everyone)   |
| `/models`                                  | Fetch available models and open the model-selection menu.                                                | `coreai.models` (everyone)      |
| `/modelinfo`                               | Show the active model and key status.                                                                    | `coreai.modelinfo` (everyone)   |
| `/imagemap <url> [<columns>x<rows>]`       | Download a public HTTP(S) image and create map tiles.                                                    | `coreai.imagemap` (everyone)    |
| `/imagegenmap <prompt> [<columns>x<rows>]` | Generate an image and create map tiles.                                                                  | `coreai.imagegenmap` (everyone) |
| `/cleanup confirm`                         | Remove old map images according to the configured age limit.                                             | `coreai.cleanup` (operators)    |

Cancelled chat events can still be observed or logged by other server plugins;
use a trusted server and review its chat-logging plugins before entering a
personal key.

## Build and test

The Maven wrapper is included. Building has been verified with JDK 27:

```powershell
.\mvnw.cmd -B -ntp clean verify
python .github/scripts/verify_artifact.py
```

The shaded plugin JAR is written to `target/coreai-1.2.0.jar`. The Spigot API
is compile-time-only; Paper is not required to build the plugin. Runtime
libraries are included and relocated in the shaded artifact.

## License

CoreAI is licensed under the MIT License. See [LICENSE](LICENSE).
