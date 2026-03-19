using CompanionRobot.Application.Services;

namespace CompanionRobot.Tests;

public sealed class AppStateServiceTests
{
    [Fact]
    public void SetProviders_UpdatesCurrentSnapshot()
    {
        var service = new AppStateService();
        service.SetProviders("MockAI", "MockSpeech");
        service.SetListening(true, "Listening...");

        Assert.Equal("MockAI", service.Current.AIProvider);
        Assert.Equal("MockSpeech", service.Current.SpeechProvider);
        Assert.True(service.Current.IsListening);
    }
}
