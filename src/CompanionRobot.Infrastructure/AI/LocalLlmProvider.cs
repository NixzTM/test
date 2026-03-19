using CompanionRobot.Core.Interfaces;
using CompanionRobot.Core.Models;
using CompanionRobot.Infrastructure.Options;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;

namespace CompanionRobot.Infrastructure.AI;

public sealed class LocalLlmProvider(
    IOptions<AIOptions> options,
    MockAIProvider mockAIProvider,
    ILogger<LocalLlmProvider> logger) : IAIProvider
{
    private readonly AIOptions _options = options.Value;

    public string ProviderName => "LocalLlm";

    public async Task<AIResponse> GetResponseAsync(ChatRequest request, CancellationToken cancellationToken = default)
    {
        cancellationToken.ThrowIfCancellationRequested();

        if (await IsRuntimeAvailableAsync(cancellationToken).ConfigureAwait(false))
        {
            logger.LogInformation("Local LLM runtime appears available. Placeholder integration path executing.");
            return new AIResponse
            {
                Content = $"LocalLlm adapter shell received your request: '{request.UserInput}'. Replace this placeholder with llama.cpp or local HTTP runtime integration.",
                ProviderName = ProviderName,
                GeneratedAt = DateTimeOffset.UtcNow,
                IsFallbackResponse = false
            };
        }

        var reason = "Configured LocalLlm runtime is unavailable. Missing endpoint or model/runtime path.";
        logger.LogWarning(reason);

        if (!_options.AllowMockFallback)
        {
            throw new InvalidOperationException(reason);
        }

        var fallbackResponse = await mockAIProvider.GetResponseAsync(request, cancellationToken).ConfigureAwait(false);
        return new AIResponse
        {
            Content = fallbackResponse.Content,
            GeneratedAt = fallbackResponse.GeneratedAt,
            IsFallbackResponse = true,
            ProviderName = ProviderName
        };
    }

    private Task<bool> IsRuntimeAvailableAsync(CancellationToken cancellationToken)
    {
        cancellationToken.ThrowIfCancellationRequested();
        var endpointAvailable = !string.IsNullOrWhiteSpace(_options.Endpoint);
        var modelAvailable = !string.IsNullOrWhiteSpace(_options.ModelPath) && File.Exists(_options.ModelPath);
        return Task.FromResult(endpointAvailable || modelAvailable);
    }
}
