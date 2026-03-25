# MineCast

A Fabric mod that broadcasts TTS (Text-to-Speech) audio to all players on a Minecraft server. The server administrator types `/cast <text>`, the server calls the Typecast TTS API, and the audio plays for every connected player simultaneously.

> **Powered by [Typecast](https://typecast.ai?utm_source=minecast&utm_medium=github)** — AI voice platform with a wide range of natural-sounding voices.

[한국어 README](README.ko.md)

---

## How It Works

```
/cast Hello everyone
     │
     ▼
Server mod calls Typecast API → receives MP3
     │
     ▼
MP3 split into chunks → sent to all players via custom packets
     │
     ▼
Client mod receives chunks → decodes MP3 → plays via OpenAL
```

---

## Requirements

- Minecraft **26.1**
- Fabric Loader **0.18.4+**
- Fabric API **0.143.15+26.1**
- Java **25**
- A [Typecast](https://typecast.ai) API key and voice ID

---

## Installation

### Server

1. Install [Fabric Loader 0.18.4](https://fabricmc.net/use/server/) for Minecraft 26.1
2. Place the following in your server's `mods/` folder:
   - `minecast-server-<version>.jar`
   - `fabric-api-<version>+26.1.jar`
3. Start the server — `config/minecast.json` is created automatically
4. Edit `config/minecast.json` with your API credentials (see [Configuration](#configuration))
5. Restart the server

### Client

Players who want to hear TTS audio must install the client mod:

1. Install [Fabric Loader 0.18.4](https://fabricmc.net/use/installer/) for Minecraft 26.1
2. Place the following in your `mods/` folder:
   - `minecast-client-<version>.jar`
   - `fabric-api-<version>+26.1.jar`

> Players without the client mod can still join and play normally — they just won't hear TTS audio.

---

## Getting Your API Key

1. Sign up at [typecast.ai](https://typecast.ai?utm_source=minecast&utm_medium=github)
2. Go to [Developers → API → API Keys](https://typecast.ai/developers/api/api-key?utm_source=minecast&utm_medium=github) and generate an API key
3. Go to [Developers → API → Voices](https://typecast.ai/developers/api/voices?utm_source=minecast&utm_medium=github), pick a voice, and copy its **Voice ID**

---

## Configuration

`config/minecast.json` (created on first server start):

```json
{
  "apiKey": "your-typecast-api-key",
  "voiceId": "your-voice-id",
  "maxTextLength": 200
}
```

| Field | Description |
|-------|-------------|
| `apiKey` | Typecast API key |
| `voiceId` | Voice ID to use (default: `tc_60e5426de8b95f1d3000d7b5` — Jack) |
| `maxTextLength` | Maximum character length for input text |

---

## Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/cast <text>` | OP level 2+ | Broadcasts TTS audio to all players using the default voice |
| `/cast <text> --voice <voice_id>` | OP level 2+ | Broadcasts TTS audio using a specific voice |

### Examples

```
/cast Welcome to the server!
/cast The dragon has been slain! --voice tc_abc123
```

---

## Event Integration

You can trigger `/cast` from command blocks, allowing map makers to integrate TTS into their maps without any code changes.

**Command block example:**
```
cast A village is under attack!
```

Place a button or redstone signal next to the command block to activate it.

---

## Building from Source

```bash
git clone https://github.com/jaebong-human/minecast.git
cd minecast
./gradlew :minecast-server:build
./gradlew :minecast-client:build
```

Built JARs are output to:
- `minecast-server/build/libs/minecast-server-<version>.jar`
- `minecast-client/build/libs/minecast-client-<version>.jar`

---

## License

MIT
