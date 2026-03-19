using System.Text;
using CompanionRobot.Core.Events;
using CompanionRobot.Core.Hardware;
using CompanionRobot.Core.Interfaces;
using Microsoft.Extensions.Logging;

namespace CompanionRobot.Application.Services;

public sealed class HardwareService(
    IHardwareCoordinator hardwareCoordinator,
    IEventBus eventBus,
    ILogger<HardwareService> logger) : IHardwareService
{
    public async Task WaveAsync(CancellationToken cancellationToken = default)
    {
        logger.LogInformation("Mock wave requested.");
        await eventBus.PublishAsync(new HardwareCommandRequested("Servo", "Wave", "Arm wave"), cancellationToken).ConfigureAwait(false);
        await hardwareCoordinator.PerformWaveAsync(cancellationToken).ConfigureAwait(false);
    }

    public async Task DriveForwardAsync(CancellationToken cancellationToken = default)
    {
        logger.LogInformation("Mock drive forward requested.");
        await eventBus.PublishAsync(new HardwareCommandRequested("Motor", "DriveForward", "Speed 35"), cancellationToken).ConfigureAwait(false);
        await hardwareCoordinator.DriveForwardAsync(cancellationToken).ConfigureAwait(false);
    }

    public async Task<string> ReadSensorSnapshotAsync(CancellationToken cancellationToken = default)
    {
        logger.LogInformation("Mock sensor read requested.");
        var snapshot = await hardwareCoordinator.ReadStatusAsync(cancellationToken).ConfigureAwait(false);
        var builder = new StringBuilder();

        foreach (var pair in snapshot)
        {
            if (builder.Length > 0)
            {
                builder.Append(" | ");
            }

            builder.Append(pair.Key).Append(':').Append(' ').Append(pair.Value);
        }

        return builder.ToString();
    }

    public Task DisplayStatusAsync(string message, CancellationToken cancellationToken = default)
    {
        logger.LogInformation("Mock display requested with message {Message}.", message);
        return hardwareCoordinator.DisplayAsync(message, cancellationToken);
    }
}
