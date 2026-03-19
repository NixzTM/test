using CompanionRobot.Core.Hardware;
using Microsoft.Extensions.Logging;

namespace CompanionRobot.Infrastructure.Hardware;

public sealed class MockHardwareCoordinator(
    IServoController servoController,
    IMotorController motorController,
    ISensorManager sensorManager,
    IDisplayController displayController,
    ILogger<MockHardwareCoordinator> logger) : IHardwareCoordinator
{
    public async Task PerformWaveAsync(CancellationToken cancellationToken = default)
    {
        logger.LogInformation("Starting mock wave sequence.");
        await servoController.SetAngleAsync("RightArm", 30, cancellationToken).ConfigureAwait(false);
        await servoController.SetAngleAsync("RightArm", 75, cancellationToken).ConfigureAwait(false);
        await servoController.SetAngleAsync("RightArm", 45, cancellationToken).ConfigureAwait(false);
    }

    public Task DriveForwardAsync(CancellationToken cancellationToken = default)
    {
        logger.LogInformation("Starting mock drive forward sequence.");
        return motorController.MoveAsync("Forward", 35, cancellationToken);
    }

    public Task<IReadOnlyDictionary<string, string>> ReadStatusAsync(CancellationToken cancellationToken = default) =>
        sensorManager.ReadSensorsAsync(cancellationToken);

    public Task DisplayAsync(string message, CancellationToken cancellationToken = default) =>
        displayController.ShowMessageAsync(message, cancellationToken);
}
