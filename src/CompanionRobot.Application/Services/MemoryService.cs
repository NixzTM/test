using CompanionRobot.Core.Interfaces;
using CompanionRobot.Core.Models;

namespace CompanionRobot.Application.Services;

public sealed class MemoryService : IMemoryService
{
    private readonly object _syncRoot = new();
    private ChatSession _currentSession = new();

    public ChatSession CurrentSession
    {
        get
        {
            lock (_syncRoot)
            {
                return Clone(_currentSession);
            }
        }
    }

    public void AddMessage(ChatMessage message)
    {
        ArgumentNullException.ThrowIfNull(message);

        lock (_syncRoot)
        {
            _currentSession.Messages.Add(message);
            _currentSession.UpdatedAt = DateTimeOffset.UtcNow;
        }
    }

    public IReadOnlyList<ChatMessage> GetAllMessages()
    {
        lock (_syncRoot)
        {
            return _currentSession.Messages.ToList();
        }
    }

    public IReadOnlyList<ChatMessage> GetRecentContext(int maxMessages)
    {
        lock (_syncRoot)
        {
            return _currentSession.Messages.TakeLast(Math.Max(0, maxMessages)).ToList();
        }
    }

    public void ReplaceSession(ChatSession session)
    {
        ArgumentNullException.ThrowIfNull(session);

        lock (_syncRoot)
        {
            _currentSession = Clone(session);
        }
    }

    public void Clear()
    {
        lock (_syncRoot)
        {
            _currentSession = new ChatSession();
        }
    }

    private static ChatSession Clone(ChatSession session) => new()
    {
        Id = session.Id,
        CreatedAt = session.CreatedAt,
        UpdatedAt = session.UpdatedAt,
        Messages = session.Messages.ToList()
    };
}
