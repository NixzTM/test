using CompanionRobot.Core.Models;

namespace CompanionRobot.Core.Events;

public sealed record AIResponseGenerated(ChatMessage Message, AIResponse Response);
