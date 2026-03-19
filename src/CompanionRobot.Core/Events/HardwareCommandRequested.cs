namespace CompanionRobot.Core.Events;

public sealed record HardwareCommandRequested(string Device, string Command, string Payload);
