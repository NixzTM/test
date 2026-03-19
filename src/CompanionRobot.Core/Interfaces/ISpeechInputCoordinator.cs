namespace CompanionRobot.Core.Interfaces;

public interface ISpeechInputCoordinator
{
    Task StartListeningAsync(CancellationToken cancellationToken = default);
    Task StopListeningAsync(CancellationToken cancellationToken = default);
}
