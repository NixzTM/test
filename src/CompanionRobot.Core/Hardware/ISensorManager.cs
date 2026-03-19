namespace CompanionRobot.Core.Hardware;

public interface ISensorManager
{
    Task<IReadOnlyDictionary<string, string>> ReadSensorsAsync(CancellationToken cancellationToken = default);
}
