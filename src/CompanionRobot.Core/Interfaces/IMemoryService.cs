using CompanionRobot.Core.Models;

namespace CompanionRobot.Core.Interfaces;

public interface IMemoryService
{
    ChatSession CurrentSession { get; }

    void AddMessage(ChatMessage message);
    IReadOnlyList<ChatMessage> GetAllMessages();
    IReadOnlyList<ChatMessage> GetRecentContext(int maxMessages);
    void ReplaceSession(ChatSession session);
    void Clear();
}
