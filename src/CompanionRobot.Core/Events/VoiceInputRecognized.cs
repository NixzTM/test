namespace CompanionRobot.Core.Events;

public sealed record VoiceInputRecognized(string Transcript, bool AutoSent);
