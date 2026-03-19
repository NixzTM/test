using CompanionRobot.Core.Models;

namespace CompanionRobot.Core.Events;

public sealed record UserMessageReceived(ChatMessage Message);
