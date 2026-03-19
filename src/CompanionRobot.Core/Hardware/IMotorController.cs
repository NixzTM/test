namespace CompanionRobot.Core.Hardware;

public interface IMotorController
{
    Task MoveAsync(string direction, int speedPercent, CancellationToken cancellationToken = default);
}
