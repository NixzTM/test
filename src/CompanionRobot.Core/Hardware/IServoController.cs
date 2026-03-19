namespace CompanionRobot.Core.Hardware;

public interface IServoController
{
    Task SetAngleAsync(string servoName, int angle, CancellationToken cancellationToken = default);
}
