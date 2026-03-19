using CompanionRobot.Core.Models;

namespace CompanionRobot.Core.Interfaces;

public interface IAIProvider
{
    string ProviderName { get; }

    Task<AIResponse> GetResponseAsync(ChatRequest request, CancellationToken cancellationToken = default);
}
