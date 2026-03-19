namespace CompanionRobot.Infrastructure.Options;

public sealed class AIOptions
{
    public const string SectionName = "AI";

    public string Provider { get; set; } = "Mock";
    public string ModelPath { get; set; } = string.Empty;
    public string Endpoint { get; set; } = string.Empty;
    public int ContextWindow { get; set; } = 8192;
    public int MaxTokens { get; set; } = 256;
    public double Temperature { get; set; } = 0.7;
    public bool AllowMockFallback { get; set; } = true;
}
