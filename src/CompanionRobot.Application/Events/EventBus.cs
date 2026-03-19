using System.Collections.Concurrent;
using CompanionRobot.Core.Interfaces;

namespace CompanionRobot.Application.Events;

public sealed class EventBus : IEventBus
{
    private readonly ConcurrentDictionary<Type, ConcurrentDictionary<Guid, Func<object, Task>>> _subscriptions = new();

    public IDisposable Subscribe<TEvent>(Func<TEvent, Task> handler) where TEvent : class
    {
        ArgumentNullException.ThrowIfNull(handler);

        var eventType = typeof(TEvent);
        var subscriptionId = Guid.NewGuid();
        var eventHandlers = _subscriptions.GetOrAdd(eventType, static _ => new ConcurrentDictionary<Guid, Func<object, Task>>());
        eventHandlers[subscriptionId] = evt => handler((TEvent)evt);

        return new Subscription(() =>
        {
            if (_subscriptions.TryGetValue(eventType, out var handlers))
            {
                handlers.TryRemove(subscriptionId, out _);
            }
        });
    }

    public async Task PublishAsync<TEvent>(TEvent eventItem, CancellationToken cancellationToken = default) where TEvent : class
    {
        ArgumentNullException.ThrowIfNull(eventItem);

        if (!_subscriptions.TryGetValue(typeof(TEvent), out var handlers))
        {
            return;
        }

        foreach (var handler in handlers.Values)
        {
            cancellationToken.ThrowIfCancellationRequested();
            await handler(eventItem).ConfigureAwait(false);
        }
    }

    private sealed class Subscription(Action disposeAction) : IDisposable
    {
        private readonly Action _disposeAction = disposeAction;
        private int _disposed;

        public void Dispose()
        {
            if (Interlocked.Exchange(ref _disposed, 1) == 0)
            {
                _disposeAction();
            }
        }
    }
}
