# CoreAI

[![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21.4-blue)](https://papermc.io/) [![Java 21](https://img.shields.io/badge/Java-21-orange)](https://openjdk.java.net/projects/jdk/21/) [![Discord](https://img.shields.io/badge/Discord-Join%20Discord-brightgreen?logo=discord)](https://discord.com/invite/Zgds9u353N)

An **extensible, AI-powered Minecraft toolkit** built on Spigot/Paper — bridging the gap between in-game commands and OpenAI’s powerful models. Whether you need chat intelligence, in-server image generation, or advanced model selection, CoreAI has you covered.

---

## 🚀 Features

- **Interactive Chat**: `/chat <message>` engages ChatGPT with full conversation history and per-player API keys.
- **Per-Player API Keys**: Securely store your own OpenAI key via `setapikey` (AES-GCM encrypted on disk)
- **Model Selection GUI**: `/models` opens a paginated inventory of available models; click to choose your active model.
- **Model Details**: `/modelinfo` fetches and displays detailed metadata for the current model (engine, owner, permissions, etc.).
- **Session Management**: Automatic agent creation and cleanup on player join/quit; per-player model overrides persisted across restarts.
- **Config-Driven**: Customize default API key, model, and timeout in `config.yml` under `openai.*` keys.

---

## 📋 Table of Contents
1. [Installation](#installation)
2. [Configuration](#configuration)
3. [Usage](#usage)
   - [Commands](#commands)
4. [Permissions](#permissions)
5. [Development & Contribution](#development--contribution)
6. [Roadmap](#roadmap)
7. [License](#license)

---

## 🔧 Installation
1. Drop `CoreAI.jar` into your server’s `plugins/` directory.
2. Start the server to generate the default `config.yml` and data folder.
3. (Optional) Operators: set a global API key in `config.yml` or let players manage their own.

---

## ⚙️ Configuration
Located at `plugins/CoreAI/config.yml`. Key options:
```yaml
enable-feature-x: true           # Toggle advanced features
openai:
  api-key: ""                # Default API key (if you don’t use per-player keys)
  model: "gpt-3.5-turbo"     # Default model
  timeout-ms: 60000            # Request timeout in milliseconds
```  

---

## 🛠️ Usage
### Commands

| Command       | Description                                                       | Permission         |
|---------------|-------------------------------------------------------------------|--------------------|
| `setapikey`   | Store your personal OpenAI key securely (AES-GCM encrypted).      | `coreai.setapikey` |
| `/chat <msg>` | Chat with the AI, keeping full context.                           | `coreai.chat`      |
| `/models`     | Open a paginated GUI to select available models for your key.     | `coreai.models`    |
| `/modelinfo`  | Display detailed info about your current model (engine metadata). | `coreai.modelinfo` |

### Permissions
```yaml
coreai.setapikey:
  description: Allows storing a personal API key
  default: true
coreai.chat:
  description: Allows chatting with the AI
  default: true
coreai.models:
  description: Allows browsing and selecting AI models
  default: true
coreai.modelinfo:
  description: Allows fetching information about the set AI model
  default: true
```

---

## 🛠️ Development & Contribution
CoreAI is open source! To contribute:
1. Fork the repository
2. Clone & open in IntelliJ (Java 21 + Maven)
3. Implement features, fix bugs, and write tests
4. Submit a pull request with a clear description

<br/>

## 🔮 Roadmap
- 🖼️ **In-game Image Generation**: Render AI-generated images onto Minecraft maps.
- 🌐 **Webhook Integration**: Post chat transcripts to Discord or Webhooks.
- 📊 **Metrics & Usage**: Track API usage per player & model.
- 📦 **Plugin Hub**: Provide optional modules (e.g., AI shop, NPC dialogue).

---

## 📄 License
MIT © StealingDaPenta 2025

*Join our [Discord](https://discord.gg/your-server) for previews, feedback, and community support!*

