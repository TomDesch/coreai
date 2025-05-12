# CoreAI

[![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21.4-blue)](https://papermc.io/)
[![Java 21](https://img.shields.io/badge/Java-21-orange)](https://openjdk.java.net/projects/jdk/21/)
[![Discord](https://img.shields.io/badge/Discord-Join%20Discord-brightgreen?logo=discord)](https://discord.com/invite/Zgds9u353N)

**CoreAI** is an extensible, AI-powered Minecraft toolkit built on Spigot/Paper – now with in-game **image generation**, **map rendering**, and automatic
cleanup of unused assets.

---

## 🚀 Features

- 💬 **Interactive Chat**  
  Use `/chat <message>` to talk with ChatGPT using full per-player context.

- 🔐 **Per-Player API Keys**  
  Players can securely store OpenAI keys with `/setapikey` (AES-GCM encrypted).

- 📦 **Model GUI**  
  Use `/models` to open a GUI with all available models tied to your key.

- 📄 **Model Info**  
  Use `/modelinfo` to get full details about your active model.

- 🖼️ **Image-to-Map Rendering**  
  Use `/imagemap <url>` to convert any image into in-game maps (supports `WxH` tiling).

- 🎨 **AI Image Generation**  
  Use `/imagegenmap <prompt>` to generate a DALL·E image and get it as Minecraft maps.

- 💾 **Map Persistence & Recovery**  
  Generated map tiles are stored as `.png` files and restored after server restarts.

- 🧹 **Automatic Cleanup**  
  Unused images are cleaned up after a configurable number of days.
  - Manual cleanup with `/cleanup`
  - Tracks "last seen" map usage (inventory, item frames, ground, etc.)

---

## 📋 Table of Contents
1. [Installation](#installation)
2. [Configuration](#configuration)
3. [Usage](#usage)
4. [Permissions](#permissions)
5. [Development](#development)
6. [Roadmap](#roadmap)
7. [License](#license)

---

## 🔧 Installation

1. Drop `CoreAI.jar` into your server’s `plugins/` directory.
2. Restart the server.
3. Set an optional global API key in `config.yml` or let players use `/setapikey`.

---

## ⚙️ Configuration

```yaml
openai:
  api-key: ""                  # Optional: server-wide default key
  model: "gpt-3.5-turbo"       # Default model to use
  timeout-ms: 60000            # Max wait time for chat completions
  timeout-image-ms: 300000     # Max wait time for image generation

cleanup:
  auto-enabled: true           # Enable automatic cleanup
  max-age-days: 30             # Delete unused map images older than this
````

---

## 🛠️ Usage

### Commands

| Command                 | Description                                                     |
|-------------------------|-----------------------------------------------------------------|
| `/setapikey`            | Store your OpenAI key (AES-encrypted on disk)                   |
| `/chat <message>`       | Chat with AI using your configured model and key                |
| `/models`               | Choose an AI model using a GUI                                  |
| `/modelinfo`            | View info about your current model                              |
| `/imagemap <url>`       | Render an image from a URL into map tiles (supports WxH tiling) |
| `/imagegenmap <prompt>` | Generate an AI image from a prompt and render it as a map grid  |
| `/cleanup`              | Manually remove old unused image maps                           |

---

## 🔐 Permissions

```yaml
coreai.setapikey:
  default: true

coreai.chat:
  default: true

coreai.models:
  default: true

coreai.modelinfo:
  default: true

coreai.imagemap:
  default: true

coreai.imagegenmap:
  default: true

coreai.cleanup:
  default: op
```

---

## 💡 Development

Want to contribute? Fork this repo and open a PR!

* Java 21
* PaperMC 1.21.4+
* IntelliJ recommended

---

## 🔮 Roadmap ( ? )

* 🤖 NPCs with AI-generated speech and personalities
* 🎭 Integration with Citizens and Denizen
* 💬 Support for audio (TTS via browser mod support)

---

## 📄 License

MIT © StealingDaPenta 2025

> Join our [Discord](https://www.stealingdapenta.be) to give feedback, suggest features, or show off your creations!