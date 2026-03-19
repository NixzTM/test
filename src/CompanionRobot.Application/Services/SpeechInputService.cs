using CompanionRobot.Core.Events;
using CompanionRobot.Core.Interfaces;
using Microsoft.Extensions.Logging;

namespace CompanionRobot.Application.Services;

public sealed class SpeechInputService : ISpeechInputCoordinator, IDisposable
{
    private readonly ISpeechRecognitionService _speechRecognitionService;
    private readonly IChatService _chatService;
    private readonly IEventBus _eventBus;
    private readonly IAppStateService _appStateService;
    private readonly ILogger<SpeechInputService> _logger;
    private readonly bool _autoSendRecognizedSpeech;
    private bool _disposed;

    public SpeechInputService(
        ISpeechRecognitionService speechRecognitionService,
        IChatService chatService,
        IEventBus eventBus,
        IAppStateService appStateService,
        ILogger<SpeechInputService> logger,
        bool autoSendRecognizedSpeech)
    {
        _speechRecognitionService = speechRecognitionService;
        _chatService = chatService;
        _eventBus = eventBus;
        _appStateService = appStateService;
        _logger = logger;
        _autoSendRecognizedSpeech = autoSendRecognizedSpeech;

        _speechRecognitionService.SpeechRecognized += OnSpeechRecognized;
        _speechRecognitionService.StateChanged += OnStateChanged;
        _speechRecognitionService.ErrorOccurred += OnErrorOccurred;
    }

    public Task StartListeningAsync(CancellationToken cancellationToken = default)
    {
        _logger.LogInformation("Starting speech recognition using {ProviderName}.", _speechRecognitionService.ProviderName);
        return _speechRecognitionService.StartListeningAsync(cancellationToken);
    }

    public Task StopListeningAsync(CancellationToken cancellationToken = default)
    {
        _logger.LogInformation("Stopping speech recognition using {ProviderName}.", _speechRecognitionService.ProviderName);
        return _speechRecognitionService.StopListeningAsync(cancellationToken);
    }

    private async void OnSpeechRecognized(object? sender, SpeechRecognizedEventArgs e)
    {
        try
        {
            _appStateService.SetTranscriptPreview(e.Transcript);
            if (!e.IsFinal)
            {
                return;
            }

            _logger.LogInformation("Final speech transcript received: {Transcript}", e.Transcript);
            await _eventBus.PublishAsync(new VoiceInputRecognized(e.Transcript, _autoSendRecognizedSpeech)).ConfigureAwait(false);

            if (_autoSendRecognizedSpeech)
            {
                await _chatService.ProcessUserInputAsync(e.Transcript, "Speech").ConfigureAwait(false);
                _appStateService.SetTranscriptPreview(string.Empty);
            }
            else
            {
                _appStateService.SetStatus("Speech recognized. Review or send the text.");
            }
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Speech transcript handling failed.");
            _appStateService.SetError(ex.Message);
        }
    }

    private async void OnStateChanged(object? sender, SpeechRecognitionStateChangedEventArgs e)
    {
        try
        {
            _appStateService.SetListening(e.IsListening, e.StatusMessage);
            if (e.IsListening)
            {
                await _eventBus.PublishAsync(new ListeningStarted(DateTimeOffset.UtcNow)).ConfigureAwait(false);
            }
            else
            {
                await _eventBus.PublishAsync(new ListeningStopped(DateTimeOffset.UtcNow)).ConfigureAwait(false);
            }
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Speech state change handling failed.");
            _appStateService.SetError(ex.Message);
        }
    }

    private async void OnErrorOccurred(object? sender, SpeechRecognitionErrorEventArgs e)
    {
        try
        {
            _logger.LogError(e.Exception, "Speech recognition error: {Message}", e.ErrorMessage);
            _appStateService.SetError(e.ErrorMessage);
            _appStateService.SetListening(false, "Speech recognition error.");
            await _eventBus.PublishAsync(new ListeningErrorOccurred(e.ErrorMessage)).ConfigureAwait(false);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Speech error propagation failed.");
            _appStateService.SetError(ex.Message);
        }
    }

    public void Dispose()
    {
        if (_disposed)
        {
            return;
        }

        _speechRecognitionService.SpeechRecognized -= OnSpeechRecognized;
        _speechRecognitionService.StateChanged -= OnStateChanged;
        _speechRecognitionService.ErrorOccurred -= OnErrorOccurred;
        _disposed = true;
    }
}
