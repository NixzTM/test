using CompanionRobot.Core.Hardware;
using Microsoft.Extensions.Logging;

namespace CompanionRobot.Infrastructure.Hardware;

public sealed class MockServoController(ILogger<MockServoController> logger) : IServoController
{
    public Task SetAngleAsync(string servoName, int angle, CancellationToken cancellationToken = default)
    {
        logger.LogInformation("Mock servo {ServoName} moved to angle {Angle}.", servoName, angle);
        return Task.CompletedTask;
    }
}
