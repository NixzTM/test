namespace CompanionRobot.Core.Hardware;

public interface IDisplayController
{
    Task ShowMessageAsync(string message, CancellationToken cancellationToken = default);
}
