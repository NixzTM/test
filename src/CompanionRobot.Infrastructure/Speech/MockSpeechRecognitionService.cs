using CompanionRobot.Core.Events;
using CompanionRobot.Core.Interfaces;
using Microsoft.Extensions.Logging;

namespace CompanionRobot.Infrastructure.Speech;

public sealed class MockSpeechRecognitionService(ILogger<MockSpeechRecognitionService> logger) : ISpeechRecognitionService
{
    private readonly object _syncRoot = new();
    private CancellationTokenSource? _listeningCts;
    private Task? _simulationTask;

    public event EventHandler<SpeechRecognizedEventArgs>? SpeechRecognized;
    public event EventHandler<SpeechRecognitionStateChangedEventArgs>? StateChanged;
    public event EventHandler<SpeechRecognitionErrorEventArgs>? ErrorOccurred;

    public string ProviderName => "MockSpeech";

    public Task StartListeningAsync(CancellationToken cancellationToken = default)
    {
        lock (_syncRoot)
        {
            if (_listeningCts is not null)
            {
                return Task.CompletedTask;
            }

            logger.LogInformation("Mock speech recognition started.");
            _listeningCts = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
            _simulationTask = SimulateRecognitionAsync(_listeningCts.Token);
        }

        StateChanged?.Invoke(this, new SpeechRecognitionStateChangedEventArgs { IsListening = true, StatusMessage = "Listening in mock mode..." });
        return Task.CompletedTask;
    }

    public async Task StopListeningAsync(CancellationToken cancellationToken = default)
    {
        CancellationTokenSource? cts;
        Task? simulationTask;

        lock (_syncRoot)
        {
            cts = _listeningCts;
            simulationTask = _simulationTask;
            _listeningCts = null;
            _simulationTask = null;
        }

        if (cts is null)
        {
            return;
        }

        cts.Cancel();
        try
        {
            if (simulationTask is not null)
            {
                await simulationTask.ConfigureAwait(false);
            }
        }
        catch (OperationCanceledException)
        {
        }
        finally
        {
            cts.Dispose();
            StateChanged?.Invoke(this, new SpeechRecognitionStateChangedEventArgs { IsListening = false, StatusMessage = "Mock listening stopped." });
            logger.LogInformation("Mock speech recognition stopped.");
        }
    }

    private async Task SimulateRecognitionAsync(CancellationToken cancellationToken)
    {
        try
        {
            var partials = new[]
            {
                "hello companion",
                "hello companion robot",
                "hello companion robot can you"
            };

            foreach (var partial in partials)
            {
                cancellationToken.ThrowIfCancellationRequested();
                await Task.Delay(700, cancellationToken).ConfigureAwait(false);
                SpeechRecognized?.Invoke(this, new SpeechRecognizedEventArgs { Transcript = partial, IsFinal = false });
            }

            await Task.Delay(900, cancellationToken).ConfigureAwait(false);
            SpeechRecognized?.Invoke(this, new SpeechRecognizedEventArgs
            {
                Transcript = "Hello Companion Robot, can you summarize our current status?",
                IsFinal = true
            });

            CompleteNaturally();
        }
        catch (OperationCanceledException)
        {
        }
        catch (Exception ex)
        {
            logger.LogError(ex, "Mock speech recognition failed.");
            ErrorOccurred?.Invoke(this, new SpeechRecognitionErrorEventArgs { ErrorMessage = ex.Message, Exception = ex });
            CompleteNaturally();
        }
    }

    private void CompleteNaturally()
    {
        CancellationTokenSource? cts;

        lock (_syncRoot)
        {
            cts = _listeningCts;
            _listeningCts = null;
            _simulationTask = null;
        }

        cts?.Dispose();
        StateChanged?.Invoke(this, new SpeechRecognitionStateChangedEventArgs { IsListening = false, StatusMessage = "Mock listening completed." });
        logger.LogInformation("Mock speech recognition completed.");
    }
}
