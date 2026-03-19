namespace CompanionRobot.Core.Interfaces;

public interface IHardwareService
{
    Task WaveAsync(CancellationToken cancellationToken = default);
    Task DriveForwardAsync(CancellationToken cancellationToken = default);
    Task<string> ReadSensorSnapshotAsync(CancellationToken cancellationToken = default);
    Task DisplayStatusAsync(string message, CancellationToken cancellationToken = default);
}
