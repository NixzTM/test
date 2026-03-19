using CompanionRobot.Core.Enums;

namespace CompanionRobot.Core.Models;

public sealed record ChatMessage(
    Guid Id,
    MessageRole Role,
    string Content,
    DateTimeOffset Timestamp,
    string Source)
{
    public static ChatMessage Create(MessageRole role, string content, string source = "Unknown") =>
        new(Guid.NewGuid(), role, content, DateTimeOffset.UtcNow, source);
}
