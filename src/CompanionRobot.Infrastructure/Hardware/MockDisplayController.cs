using CompanionRobot.Core.Hardware;
using Microsoft.Extensions.Logging;

namespace CompanionRobot.Infrastructure.Hardware;

public sealed class MockDisplayController(ILogger<MockDisplayController> logger) : IDisplayController
{
    public Task ShowMessageAsync(string message, CancellationToken cancellationToken = default)
    {
        logger.LogInformation("Mock display message: {Message}", message);
        return Task.CompletedTask;
    }
}
