namespace CompanionRobot.Infrastructure.Options;

public sealed class ApplicationOptions
{
    public const string SectionName = "Application";

    public string Name { get; set; } = "CompanionRobot";

    public bool DebugMode { get; set; } = true;
}
