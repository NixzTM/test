namespace CompanionRobot.Core.Interfaces;

public interface IEventBus
{
    IDisposable Subscribe<TEvent>(Func<TEvent, Task> handler) where TEvent : class;

    Task PublishAsync<TEvent>(TEvent eventItem, CancellationToken cancellationToken = default) where TEvent : class;
}
