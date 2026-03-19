namespace CompanionRobot.Core.Events;

public sealed class SpeechRecognizedEventArgs : EventArgs
{
    public required string Transcript { get; init; }

    public bool IsFinal { get; init; }
}
