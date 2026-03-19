namespace CompanionRobot.Infrastructure.Options;

public sealed class HardwareOptions
{
    public const string SectionName = "Hardware";

    public bool Enabled { get; set; }
    public string PortName { get; set; } = "COM3";
}
