namespace CompanionRobot.Core.Events;

public sealed class SpeechRecognitionErrorEventArgs : EventArgs
{
    public required string ErrorMessage { get; init; }

    public Exception? Exception { get; init; }
}
