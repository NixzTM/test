namespace CompanionRobot.Core.Interfaces;

public interface IChatService
{
    Task<bool> ProcessUserInputAsync(string input, string source, CancellationToken cancellationToken = default);
}
