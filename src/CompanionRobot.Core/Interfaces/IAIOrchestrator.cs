using CompanionRobot.Core.Models;

namespace CompanionRobot.Core.Interfaces;

public interface IAIOrchestrator
{
    Task<ChatMessage> GenerateAssistantMessageAsync(ChatMessage userMessage, CancellationToken cancellationToken = default);
}
