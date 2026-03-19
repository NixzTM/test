using CompanionRobot.Core.Models;

namespace CompanionRobot.Core.Interfaces;

public interface ISessionPersistenceService
{
    Task<ChatSession?> LoadAsync(CancellationToken cancellationToken = default);
    Task SaveAsync(ChatSession session, CancellationToken cancellationToken = default);
}
