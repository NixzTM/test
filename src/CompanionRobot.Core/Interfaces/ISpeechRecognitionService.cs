using CompanionRobot.Core.Events;

namespace CompanionRobot.Core.Interfaces;

public interface ISpeechRecognitionService
{
    event EventHandler<SpeechRecognizedEventArgs>? SpeechRecognized;
    event EventHandler<SpeechRecognitionStateChangedEventArgs>? StateChanged;
    event EventHandler<SpeechRecognitionErrorEventArgs>? ErrorOccurred;

    string ProviderName { get; }

    Task StartListeningAsync(CancellationToken cancellationToken = default);
    Task StopListeningAsync(CancellationToken cancellationToken = default);
}
