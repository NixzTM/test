using CompanionRobot.Core.Hardware;
using Microsoft.Extensions.Logging;

namespace CompanionRobot.Infrastructure.Hardware;

public sealed class MockMotorController(ILogger<MockMotorController> logger) : IMotorController
{
    public Task MoveAsync(string direction, int speedPercent, CancellationToken cancellationToken = default)
    {
        logger.LogInformation("Mock motor moving {Direction} at {SpeedPercent}% speed.", direction, speedPercent);
        return Task.CompletedTask;
    }
}
