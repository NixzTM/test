namespace CompanionRobot.Infrastructure.Options;

public sealed class SpeechOptions
{
    public const string SectionName = "Speech";

    public string Provider { get; set; } = "Mock";
    public string ExecutablePath { get; set; } = string.Empty;
    public string ModelPath { get; set; } = string.Empty;
    public string Language { get; set; } = "en";
    public bool EnablePartialResults { get; set; } = true;
    public bool AutoSendRecognizedSpeech { get; set; }
    public bool AllowMockFallback { get; set; } = true;
}
