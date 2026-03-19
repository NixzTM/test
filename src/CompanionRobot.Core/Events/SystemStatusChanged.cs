namespace CompanionRobot.Core.Events;

public sealed record SystemStatusChanged(string Status, string? WarningOrError = null);
