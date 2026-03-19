using CompanionRobot.Application.Services;
using CompanionRobot.Core.Enums;
using CompanionRobot.Core.Models;
using Microsoft.Extensions.Logging.Abstractions;

namespace CompanionRobot.Tests;

public sealed class SessionPersistenceServiceTests
{
    [Fact]
    public async Task SaveAsync_ThenLoadAsync_RoundTripsSession()
    {
        var path = Path.Combine(Path.GetTempPath(), $"companionrobot-{Guid.NewGuid():N}.json");

        try
        {
            var service = new SessionPersistenceService(new NullLogger<SessionPersistenceService>(), path);
            var session = new ChatSession();
            session.Messages.Add(ChatMessage.Create(MessageRole.User, "persist me"));

            await service.SaveAsync(session);
            var loaded = await service.LoadAsync();

            Assert.NotNull(loaded);
            Assert.Single(loaded!.Messages);
            Assert.Equal("persist me", loaded.Messages[0].Content);
        }
        finally
        {
            if (File.Exists(path))
            {
                File.Delete(path);
            }
        }
    }
}
