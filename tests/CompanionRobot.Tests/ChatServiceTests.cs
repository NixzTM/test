using CompanionRobot.Application.Events;
using CompanionRobot.Application.Services;
using CompanionRobot.Core.Enums;
using CompanionRobot.Core.Interfaces;
using CompanionRobot.Core.Models;
using Microsoft.Extensions.Logging.Abstractions;

namespace CompanionRobot.Tests;

public sealed class ChatServiceTests
{
    [Fact]
    public async Task ProcessUserInputAsync_AddsUserAndAssistantMessages()
    {
        var memoryService = new MemoryService();
        var eventBus = new EventBus();
        var appState = new AppStateService();
        var provider = new TestAIProvider();
        var orchestrator = new AIOrchestrator(memoryService, provider, eventBus, appState, new NullLogger<AIOrchestrator>());
        var chatService = new ChatService(memoryService, orchestrator, eventBus, appState, new NullLogger<ChatService>());

        var result = await chatService.ProcessUserInputAsync("Hello robot", "Typed");

        Assert.True(result);
        var messages = memoryService.GetAllMessages();
        Assert.Equal(2, messages.Count);
        Assert.Equal(MessageRole.User, messages[0].Role);
        Assert.Equal(MessageRole.Assistant, messages[1].Role);
    }

    private sealed class TestAIProvider : IAIProvider
    {
        public string ProviderName => "TestAI";

        public Task<AIResponse> GetResponseAsync(ChatRequest request, CancellationToken cancellationToken = default) =>
            Task.FromResult(new AIResponse
            {
                Content = $"Echo: {request.UserInput}",
                ProviderName = ProviderName,
                GeneratedAt = DateTimeOffset.UtcNow
            });
    }
}
