using CompanionRobot.Core.Interfaces;

namespace CompanionRobot.Application.Services;

public sealed class AppStateService : IAppStateService
{
    private readonly object _syncRoot = new();
    private AppStateSnapshot _current = new(
        false,
        false,
        string.Empty,
        "Ready.",
        null,
        null,
        "Unknown",
        "Unknown",
        false);

    public event EventHandler<AppStateSnapshot>? StateChanged;

    public AppStateSnapshot Current
    {
        get
        {
            lock (_syncRoot)
            {
                return _current;
            }
        }
    }

    public void SetListening(bool isListening, string? status = null)
    {
        var current = Current;
        Update(current with { IsListening = isListening, StatusMessage = status ?? current.StatusMessage });
    }

    public void SetBusy(bool isBusy, string? status = null)
    {
        var current = Current;
        Update(current with { IsBusy = isBusy, StatusMessage = status ?? current.StatusMessage });
    }

    public void SetProviders(string aiProvider, string speechProvider)
    {
        var current = Current;
        Update(current with { AIProvider = aiProvider, SpeechProvider = speechProvider });
    }

    public void SetHardwareEnabled(bool enabled)
    {
        var current = Current;
        Update(current with { HardwareEnabled = enabled });
    }

    public void SetTranscriptPreview(string transcript)
    {
        var current = Current;
        Update(current with { TranscriptPreview = transcript ?? string.Empty });
    }

    public void SetWarning(string? warning)
    {
        var current = Current;
        Update(current with { LastWarning = warning });
    }

    public void SetError(string? error)
    {
        var current = Current;
        Update(current with { LastError = error });
    }

    public void SetStatus(string status)
    {
        var current = Current;
        Update(current with { StatusMessage = status });
    }

    private void Update(AppStateSnapshot next)
    {
        lock (_syncRoot)
        {
            _current = next;
        }

        StateChanged?.Invoke(this, next);
    }
}
