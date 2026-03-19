namespace CompanionRobot.Core.Models;

public sealed class ChatRequest
{
    public string UserInput { get; init; } = string.Empty;

    public IReadOnlyList<ChatMessage> ContextMessages { get; init; } = Array.Empty<ChatMessage>();

    public DateTimeOffset RequestedAt { get; init; } = DateTimeOffset.UtcNow;

    public string SessionId { get; init; } = string.Empty;
}
