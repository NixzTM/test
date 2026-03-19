using CompanionRobot.Core.Hardware;
using Microsoft.Extensions.Logging;

namespace CompanionRobot.Infrastructure.Hardware;

public sealed class MockSensorManager(ILogger<MockSensorManager> logger) : ISensorManager
{
    public Task<IReadOnlyDictionary<string, string>> ReadSensorsAsync(CancellationToken cancellationToken = default)
    {
        logger.LogInformation("Mock sensors queried.");
        IReadOnlyDictionary<string, string> snapshot = new Dictionary<string, string>
        {
            ["Battery"] = "97%",
            ["Temperature"] = "36C",
            ["Proximity"] = "Clear"
        };

        return Task.FromResult(snapshot);
    }
}
