namespace CompanionRobot.Core.Hardware;

public interface IHardwareCoordinator
{
    Task PerformWaveAsync(CancellationToken cancellationToken = default);
    Task DriveForwardAsync(CancellationToken cancellationToken = default);
    Task<IReadOnlyDictionary<string, string>> ReadStatusAsync(CancellationToken cancellationToken = default);
    Task DisplayAsync(string message, CancellationToken cancellationToken = default);
}
