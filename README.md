# CompanionRobot

CompanionRobot is a production-oriented, offline-first Windows desktop shell for a future home companion robot. The repository delivers a runnable .NET 8 WPF application in Mock mode today while establishing clean seams for later local speech recognition, local LLM inference, and hardware integrations.

## Overview

The app provides:
- A WPF MVVM chat interface for typed and speech-driven input.
- A unified message pipeline so typed and recognized speech follow the same application flow.
- Replaceable AI and speech abstractions with immediate Mock mode support.
- Thread-safe internal eventing for robot-style orchestration.
- Mock hardware services for future actuator, sensor, and display integration.
- Local JSON persistence for restoring the last session.
- Structured logging, graceful startup/shutdown, and typed configuration.

Default configuration starts in Mock mode, so no external model files, runtimes, or hardware are required to test the app.

The repository itself is the deliverable: all project, source, XAML, test, and configuration files live directly in the repo, and no `.zip` or other binary workspace artifact is required to build it.

## Architecture Summary

### Projects
- `src/CompanionRobot.Core` – domain models, event contracts, and interfaces.
- `src/CompanionRobot.Application` – orchestration, memory, persistence, app state, and event bus implementation.
- `src/CompanionRobot.Infrastructure` – mock/future-ready provider implementations plus typed options.
- `src/CompanionRobot.App` – WPF UI, MVVM, dependency injection, startup, and styles.
- `tests/CompanionRobot.Tests` – unit tests for non-UI services.

### Core runtime flow
1. A user types a message or a speech transcript is finalized.
2. `ChatService` validates and normalizes the input.
3. `UserMessageReceived` is published to the event bus.
4. `MemoryService` stores the user message in the current session.
5. `AIOrchestrator` builds a request from recent context and calls the configured AI provider.
6. The assistant response is stored, published as `AIResponseGenerated`, and surfaced in the UI.
7. The event bus makes it easy to add future hardware reactions or robot behaviors.

## Project Structure

```text
CompanionRobot.sln
src/
  CompanionRobot.App/
  CompanionRobot.Application/
  CompanionRobot.Core/
  CompanionRobot.Infrastructure/
tests/
  CompanionRobot.Tests/
```

## Build Instructions

### Prerequisites
- Windows 10 or Windows 11
- .NET 8 SDK
- Visual Studio 2022 or newer with WPF/.NET desktop workload, or the .NET CLI

### Build with .NET CLI
```powershell
dotnet restore CompanionRobot.sln
dotnet build CompanionRobot.sln
```

### Build in Visual Studio
1. Open `CompanionRobot.sln`.
2. Restore NuGet packages if prompted.
3. Build the solution.
4. Set `CompanionRobot.App` as the startup project if needed.

## Run Instructions

### CLI
```powershell
dotnet run --project .\src\CompanionRobot.App\CompanionRobot.App.csproj
```

### Visual Studio
Press `F5` or `Ctrl+F5` while `CompanionRobot.App` is the startup project.

## Configuration Notes

The app reads `appsettings.json` from the WPF app output folder. Key sections:
- `Application`: app name and debug mode.
- `AI`: provider selection, placeholder model path, endpoint, temperature, token count, and fallback behavior.
- `Speech`: provider selection, whisper.cpp placeholders, partial transcript settings, and auto-send behavior.
- `Hardware`: whether hardware mode is logically enabled and placeholder port settings.
- `Persistence`: file path for the last session JSON.
- `Logging`: standard `Microsoft.Extensions.Logging` levels.

Default settings use Mock providers so the app can run immediately.

## How Mock Mode Works

### Mock AI
`MockAIProvider` generates deterministic but varied assistant replies that reference the current request and recent context.

### Mock Speech
`MockSpeechRecognitionService` simulates partial transcript updates followed by a final recognized phrase. The final transcript either fills the input box or enters the same send pipeline immediately, depending on `Speech:AutoSendRecognizedSpeech`.

### Mock Hardware
Hardware actions from the debug panel write structured log entries and return predictable fake data instead of talking to any device.

## Switching to WhisperCpp or LocalLlm Later

### WhisperCpp speech mode
1. Update `Speech:Provider` to `WhisperCpp`.
2. Set `Speech:ExecutablePath` to your whisper.cpp-compatible executable.
3. Set `Speech:ModelPath` to a local Whisper model file.
4. Optionally enable partial results.

If the executable or model is missing, the adapter logs the problem, updates app status, and—when configured—falls back to Mock speech.

### Local LLM mode
1. Update `AI:Provider` to `LocalLlm`.
2. Provide either a reachable local endpoint or a valid model/runtime path.
3. Tune token, temperature, and context settings.

If the local runtime is unavailable, the provider logs the issue and can fall back to Mock mode for continued app usability.

## External Files Needed for Real Offline Integrations

For actual offline speech/LLM runtime integration you will typically need:
- A local whisper.cpp executable or compatible speech runtime.
- A local Whisper model file such as `ggml-base.en.bin`.
- A local LLM runtime or endpoint, e.g. llama.cpp server or equivalent local service.
- One or more local LLM model files such as `.gguf` assets.

These are intentionally not bundled in the repository so Mock mode stays lightweight and immediately usable.

## Current Limitations

- Mock speech simulates microphone recognition rather than capturing live audio.
- The whisper.cpp and local LLM adapters are production-shaped shells, not full runtime integrations.
- Hardware support is intentionally mock-only in this first version.
- Text-to-speech, wake word detection, camera/vision, and robot emotional state are not yet implemented.

## Next Recommended Steps

1. Integrate a real local audio capture path into `WhisperCppSpeechRecognitionService`.
2. Add a real llama.cpp or local HTTP adapter implementation inside `LocalLlmProvider`.
3. Introduce robot behavior modules that subscribe to application events.
4. Add text-to-speech and a queued output pipeline.
5. Expand persistence into profiles, long-term memory, and telemetry.
