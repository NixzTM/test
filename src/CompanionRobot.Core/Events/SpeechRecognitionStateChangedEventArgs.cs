namespace CompanionRobot.Core.Events;

public sealed class SpeechRecognitionStateChangedEventArgs : EventArgs
{
    public bool IsListening { get; init; }

    public string StatusMessage { get; init; } = string.Empty;
}
