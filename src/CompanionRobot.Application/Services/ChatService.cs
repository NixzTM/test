using System.Threading;
using CompanionRobot.Core.Enums;
using CompanionRobot.Core.Events;
using CompanionRobot.Core.Interfaces;
using CompanionRobot.Core.Models;
using Microsoft.Extensions.Logging;

namespace CompanionRobot.Application.Services;

public sealed class ChatService(
    IMemoryService memoryService,
    IAIOrchestrator aiOrchestrator,
    IEventBus eventBus,
    IAppStateService appStateService,
    ILogger<ChatService> logger) : IChatService
{
    private readonly SemaphoreSlim _sendLock = new(1, 1);

    public async Task<bool> ProcessUserInputAsync(string input, string source, CancellationToken cancellationToken = default)
    {
        var normalized = input?.Trim();
        if (string.IsNullOrWhiteSpace(normalized))
        {
            return false;
        }

        await _sendLock.WaitAsync(cancellationToken).ConfigureAwait(false);
        try
        {
            logger.LogInformation("Accepted user input from {Source}.", source);
            var userMessage = ChatMessage.Create(MessageRole.User, normalized, source);
            await eventBus.PublishAsync(new UserMessageReceived(userMessage), cancellationToken).ConfigureAwait(false);
            memoryService.AddMessage(userMessage);
            appStateService.SetStatus($"Processing {source.ToLowerInvariant()} input...");
            await aiOrchestrator.GenerateAssistantMessageAsync(userMessage, cancellationToken).ConfigureAwait(false);
            return true;
        }
        finally
        {
            _sendLock.Release();
        }
    }
}
