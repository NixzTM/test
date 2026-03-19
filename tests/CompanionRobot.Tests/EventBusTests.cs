using CompanionRobot.Application.Events;

namespace CompanionRobot.Tests;

public sealed class EventBusTests
{
    [Fact]
    public async Task PublishAsync_NotifiesSubscribers()
    {
        var eventBus = new EventBus();
        var received = new List<string>();
        using var subscription = eventBus.Subscribe<TestEvent>(evt =>
        {
            received.Add(evt.Value);
            return Task.CompletedTask;
        });

        await eventBus.PublishAsync(new TestEvent("hello"));

        Assert.Single(received);
        Assert.Equal("hello", received[0]);
    }

    private sealed record TestEvent(string Value);
}
