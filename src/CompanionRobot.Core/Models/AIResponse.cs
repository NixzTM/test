namespace CompanionRobot.Core.Models;

public sealed class AIResponse
{
    public string Content { get; init; } = string.Empty;

    public bool IsFallbackResponse { get; init; }

    public string ProviderName { get; init; } = string.Empty;

    public DateTimeOffset GeneratedAt { get; init; } = DateTimeOffset.UtcNow;
}
