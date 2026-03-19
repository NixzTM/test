using CompanionRobot.Application.Services;
using CompanionRobot.Core.Enums;
using CompanionRobot.Core.Models;

namespace CompanionRobot.Tests;

public sealed class MemoryServiceTests
{
    [Fact]
    public void AddMessage_StoresAndReturnsRecentContext()
    {
        var service = new MemoryService();
        service.AddMessage(ChatMessage.Create(MessageRole.System, "system"));
        service.AddMessage(ChatMessage.Create(MessageRole.User, "hello"));
        service.AddMessage(ChatMessage.Create(MessageRole.Assistant, "hi"));

        var context = service.GetRecentContext(2);

        Assert.Equal(2, context.Count);
        Assert.Equal(MessageRole.User, context[0].Role);
        Assert.Equal(MessageRole.Assistant, context[1].Role);
    }
}
