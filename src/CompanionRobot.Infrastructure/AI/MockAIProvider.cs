using CompanionRobot.Core.Interfaces;
using CompanionRobot.Core.Models;
using Microsoft.Extensions.Logging;

namespace CompanionRobot.Infrastructure.AI;

public sealed class MockAIProvider(ILogger<MockAIProvider> logger) : IAIProvider
{
    public string ProviderName => "MockAI";

    public Task<AIResponse> GetResponseAsync(ChatRequest request, CancellationToken cancellationToken = default)
    {
        cancellationToken.ThrowIfCancellationRequested();

        var recentContext = request.ContextMessages
            .TakeLast(3)
            .Select(message => $"{message.Role}:{message.Content}")
            .ToArray();

        var responseText = recentContext.Length == 0
            ? $"Mock mode is active. I heard: \"{request.UserInput}\". I'm ready to help you prototype CompanionRobot safely offline."
            : $"Mock mode is active. Based on recent context ({string.Join(" | ", recentContext)}), my next step is to respond to: \"{request.UserInput}\".";

        logger.LogInformation("Mock AI generated a response for session {SessionId}.", request.SessionId);

        return Task.FromResult(new AIResponse
        {
            Content = responseText,
            IsFallbackResponse = false,
            ProviderName = ProviderName,
            GeneratedAt = DateTimeOffset.UtcNow
        });
    }
}
