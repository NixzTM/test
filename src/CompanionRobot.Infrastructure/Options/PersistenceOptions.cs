namespace CompanionRobot.Infrastructure.Options;

public sealed class PersistenceOptions
{
    public const string SectionName = "Persistence";

    public string SessionFilePath { get; set; } = "Data/session.json";
}
