using System.Text.Json;
using CompanionRobot.Core.Interfaces;
using CompanionRobot.Core.Models;
using Microsoft.Extensions.Logging;

namespace CompanionRobot.Application.Services;

public sealed class SessionPersistenceService(
    ILogger<SessionPersistenceService> logger,
    string sessionFilePath) : ISessionPersistenceService
{
    private static readonly JsonSerializerOptions SerializerOptions = new()
    {
        WriteIndented = true,
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase
    };

    public async Task<ChatSession?> LoadAsync(CancellationToken cancellationToken = default)
    {
        if (!File.Exists(sessionFilePath))
        {
            logger.LogInformation("No persisted chat session found at {Path}.", sessionFilePath);
            return null;
        }

        try
        {
            await using var stream = File.OpenRead(sessionFilePath);
            var session = await JsonSerializer.DeserializeAsync<ChatSession>(stream, SerializerOptions, cancellationToken).ConfigureAwait(false);
            logger.LogInformation("Loaded persisted chat session from {Path}.", sessionFilePath);
            return session;
        }
        catch (Exception ex)
        {
            logger.LogWarning(ex, "Failed to load chat session from {Path}. Starting with a new session.", sessionFilePath);
            return null;
        }
    }

    public async Task SaveAsync(ChatSession session, CancellationToken cancellationToken = default)
    {
        ArgumentNullException.ThrowIfNull(session);

        try
        {
            var directory = Path.GetDirectoryName(sessionFilePath);
            if (!string.IsNullOrWhiteSpace(directory))
            {
                Directory.CreateDirectory(directory);
            }

            await using var stream = File.Create(sessionFilePath);
            await JsonSerializer.SerializeAsync(stream, session, SerializerOptions, cancellationToken).ConfigureAwait(false);
            logger.LogInformation("Saved chat session to {Path}.", sessionFilePath);
        }
        catch (Exception ex)
        {
            logger.LogError(ex, "Failed to save chat session to {Path}.", sessionFilePath);
            throw;
        }
    }
}
