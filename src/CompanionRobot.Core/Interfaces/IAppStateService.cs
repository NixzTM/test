namespace CompanionRobot.Core.Interfaces;

public interface IAppStateService
{
    event EventHandler<AppStateSnapshot>? StateChanged;

    AppStateSnapshot Current { get; }

    void SetListening(bool isListening, string? status = null);
    void SetBusy(bool isBusy, string? status = null);
    void SetProviders(string aiProvider, string speechProvider);
    void SetHardwareEnabled(bool enabled);
    void SetTranscriptPreview(string transcript);
    void SetWarning(string? warning);
    void SetError(string? error);
    void SetStatus(string status);
}

public sealed record AppStateSnapshot(
    bool IsListening,
    bool IsBusy,
    string TranscriptPreview,
    string StatusMessage,
    string? LastWarning,
    string? LastError,
    string AIProvider,
    string SpeechProvider,
    bool HardwareEnabled);
