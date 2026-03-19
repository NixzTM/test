using CompanionRobot.Core.Events;
using CompanionRobot.Core.Interfaces;
using CompanionRobot.Infrastructure.Options;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;

namespace CompanionRobot.Infrastructure.Speech;

public sealed class WhisperCppSpeechRecognitionService(
    IOptions<SpeechOptions> options,
    MockSpeechRecognitionService mockSpeechRecognitionService,
    ILogger<WhisperCppSpeechRecognitionService> logger) : ISpeechRecognitionService
{
    private readonly SpeechOptions _options = options.Value;
    private bool _mockEventsWired;

    public event EventHandler<SpeechRecognizedEventArgs>? SpeechRecognized;
    public event EventHandler<SpeechRecognitionStateChangedEventArgs>? StateChanged;
    public event EventHandler<SpeechRecognitionErrorEventArgs>? ErrorOccurred;

    public string ProviderName => "WhisperCpp";

    public async Task StartListeningAsync(CancellationToken cancellationToken = default)
    {
        if (ValidateRuntime())
        {
            logger.LogInformation("WhisperCpp adapter shell started with executable {ExecutablePath} and model {ModelPath}.", _options.ExecutablePath, _options.ModelPath);
            StateChanged?.Invoke(this, new SpeechRecognitionStateChangedEventArgs { IsListening = true, StatusMessage = "WhisperCpp adapter shell ready. Real microphone integration pending." });
            await Task.Delay(300, cancellationToken).ConfigureAwait(false);
            SpeechRecognized?.Invoke(this, new SpeechRecognizedEventArgs
            {
                Transcript = "WhisperCpp adapter shell is configured. Replace process invocation with real audio capture and output parsing.",
                IsFinal = true
            });
            StateChanged?.Invoke(this, new SpeechRecognitionStateChangedEventArgs { IsListening = false, StatusMessage = "WhisperCpp adapter shell completed placeholder recognition." });
            return;
        }

        var error = "WhisperCpp executable or model file is missing.";
        logger.LogWarning(error);
        ErrorOccurred?.Invoke(this, new SpeechRecognitionErrorEventArgs { ErrorMessage = error });

        if (!_options.AllowMockFallback)
        {
            return;
        }

        logger.LogWarning("Falling back to mock speech recognition because WhisperCpp is unavailable.");
        WireMockEvents();
        await mockSpeechRecognitionService.StartListeningAsync(cancellationToken).ConfigureAwait(false);
    }

    public async Task StopListeningAsync(CancellationToken cancellationToken = default)
    {
        if (_options.AllowMockFallback)
        {
            await mockSpeechRecognitionService.StopListeningAsync(cancellationToken).ConfigureAwait(false);
            return;
        }

        StateChanged?.Invoke(this, new SpeechRecognitionStateChangedEventArgs { IsListening = false, StatusMessage = "WhisperCpp listening stopped." });
    }

    private bool ValidateRuntime() =>
        !string.IsNullOrWhiteSpace(_options.ExecutablePath) &&
        !string.IsNullOrWhiteSpace(_options.ModelPath) &&
        File.Exists(_options.ExecutablePath) &&
        File.Exists(_options.ModelPath);

    private void WireMockEvents()
    {
        if (_mockEventsWired)
        {
            return;
        }

        mockSpeechRecognitionService.SpeechRecognized += ForwardSpeechRecognized;
        mockSpeechRecognitionService.StateChanged += ForwardStateChanged;
        mockSpeechRecognitionService.ErrorOccurred += ForwardError;
        _mockEventsWired = true;
    }

    private void ForwardSpeechRecognized(object? sender, SpeechRecognizedEventArgs e) => SpeechRecognized?.Invoke(this, e);
    private void ForwardStateChanged(object? sender, SpeechRecognitionStateChangedEventArgs e) => StateChanged?.Invoke(this, e);
    private void ForwardError(object? sender, SpeechRecognitionErrorEventArgs e) => ErrorOccurred?.Invoke(this, e);
}
