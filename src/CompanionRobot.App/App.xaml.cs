using System.IO;
using System.Windows;
using CompanionRobot.App.ViewModels;
using CompanionRobot.Application.Events;
using CompanionRobot.Application.Services;
using CompanionRobot.Core.Hardware;
using CompanionRobot.Core.Interfaces;
using CompanionRobot.Infrastructure.AI;
using CompanionRobot.Infrastructure.Hardware;
using CompanionRobot.Infrastructure.Options;
using CompanionRobot.Infrastructure.Speech;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;

namespace CompanionRobot.App;

public partial class App : System.Windows.Application
{
    private IHost? _host;

    protected override async void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);

        _host = Host.CreateDefaultBuilder()
            .ConfigureAppConfiguration((context, configBuilder) =>
            {
                configBuilder.Sources.Clear();
                configBuilder.SetBasePath(AppContext.BaseDirectory);
                configBuilder.AddJsonFile("appsettings.json", optional: false, reloadOnChange: true);
            })
            .ConfigureLogging((context, logging) =>
            {
                logging.ClearProviders();
                logging.AddConfiguration(context.Configuration.GetSection("Logging"));
                logging.AddConsole();
                logging.AddDebug();
            })
            .ConfigureServices((context, services) => ConfigureServices(context.Configuration, services))
            .Build();

        await _host.StartAsync();

        var logger = _host.Services.GetRequiredService<ILogger<App>>();
        logger.LogInformation("CompanionRobot application starting up.");

        var persistenceService = _host.Services.GetRequiredService<ISessionPersistenceService>();
        var memoryService = _host.Services.GetRequiredService<IMemoryService>();
        var appStateService = _host.Services.GetRequiredService<IAppStateService>();
        var hardwareOptions = _host.Services.GetRequiredService<IOptions<HardwareOptions>>().Value;
        var aiProvider = _host.Services.GetRequiredService<IAIProvider>();
        var speechProvider = _host.Services.GetRequiredService<ISpeechRecognitionService>();

        appStateService.SetProviders(aiProvider.ProviderName, speechProvider.ProviderName);
        appStateService.SetHardwareEnabled(hardwareOptions.Enabled);
        appStateService.SetStatus("Loading previous session...");

        var loadedSession = await persistenceService.LoadAsync();
        if (loadedSession is not null)
        {
            memoryService.ReplaceSession(loadedSession);
            appStateService.SetStatus($"Loaded session with {loadedSession.Messages.Count} messages.");
        }
        else
        {
            appStateService.SetStatus("Ready.");
        }

        var mainWindow = _host.Services.GetRequiredService<MainWindow>();
        MainWindow = mainWindow;
        mainWindow.Show();
    }

    protected override async void OnExit(ExitEventArgs e)
    {
        if (_host is not null)
        {
            var logger = _host.Services.GetRequiredService<ILogger<App>>();
            logger.LogInformation("CompanionRobot application shutting down.");
            var memoryService = _host.Services.GetRequiredService<IMemoryService>();
            var persistenceService = _host.Services.GetRequiredService<ISessionPersistenceService>();

            try
            {
                await persistenceService.SaveAsync(memoryService.CurrentSession);
            }
            catch (Exception ex)
            {
                logger.LogError(ex, "Failed to persist the current chat session during shutdown.");
            }

            await _host.StopAsync(TimeSpan.FromSeconds(3));
            _host.Dispose();
        }

        base.OnExit(e);
    }

    private static void ConfigureServices(IConfiguration configuration, IServiceCollection services)
    {
        services.Configure<ApplicationOptions>(configuration.GetSection(ApplicationOptions.SectionName));
        services.Configure<AIOptions>(configuration.GetSection(AIOptions.SectionName));
        services.Configure<SpeechOptions>(configuration.GetSection(SpeechOptions.SectionName));
        services.Configure<HardwareOptions>(configuration.GetSection(HardwareOptions.SectionName));
        services.Configure<PersistenceOptions>(configuration.GetSection(PersistenceOptions.SectionName));

        services.AddSingleton<IEventBus, EventBus>();
        services.AddSingleton<IMemoryService, MemoryService>();
        services.AddSingleton<IAppStateService, AppStateService>();
        services.AddSingleton<IAIOrchestrator, AIOrchestrator>();
        services.AddSingleton<IChatService, ChatService>();
        services.AddSingleton<IHardwareService, HardwareService>();

        services.AddSingleton<MockAIProvider>();
        services.AddSingleton<LocalLlmProvider>();
        services.AddSingleton<MockSpeechRecognitionService>();
        services.AddSingleton<WhisperCppSpeechRecognitionService>();

        services.AddSingleton<IAIProvider>(serviceProvider =>
        {
            var options = serviceProvider.GetRequiredService<IOptions<AIOptions>>().Value;
            return options.Provider.Equals("LocalLlm", StringComparison.OrdinalIgnoreCase)
                ? serviceProvider.GetRequiredService<LocalLlmProvider>()
                : serviceProvider.GetRequiredService<MockAIProvider>();
        });

        services.AddSingleton<ISpeechRecognitionService>(serviceProvider =>
        {
            var options = serviceProvider.GetRequiredService<IOptions<SpeechOptions>>().Value;
            return options.Provider.Equals("WhisperCpp", StringComparison.OrdinalIgnoreCase)
                ? serviceProvider.GetRequiredService<WhisperCppSpeechRecognitionService>()
                : serviceProvider.GetRequiredService<MockSpeechRecognitionService>();
        });

        services.AddSingleton<ISpeechInputCoordinator>(serviceProvider =>
        {
            var speech = serviceProvider.GetRequiredService<ISpeechRecognitionService>();
            var chat = serviceProvider.GetRequiredService<IChatService>();
            var bus = serviceProvider.GetRequiredService<IEventBus>();
            var state = serviceProvider.GetRequiredService<IAppStateService>();
            var logger = serviceProvider.GetRequiredService<ILogger<SpeechInputService>>();
            var speechOptions = serviceProvider.GetRequiredService<IOptions<SpeechOptions>>().Value;
            return new SpeechInputService(speech, chat, bus, state, logger, speechOptions.AutoSendRecognizedSpeech);
        });

        services.AddSingleton<ISessionPersistenceService>(serviceProvider =>
        {
            var options = serviceProvider.GetRequiredService<IOptions<PersistenceOptions>>().Value;
            var resolvedPath = Path.IsPathRooted(options.SessionFilePath)
                ? options.SessionFilePath
                : Path.Combine(AppContext.BaseDirectory, options.SessionFilePath);
            var logger = serviceProvider.GetRequiredService<ILogger<SessionPersistenceService>>();
            return new SessionPersistenceService(logger, resolvedPath);
        });

        services.AddSingleton<IServoController, MockServoController>();
        services.AddSingleton<IMotorController, MockMotorController>();
        services.AddSingleton<ISensorManager, MockSensorManager>();
        services.AddSingleton<IDisplayController, MockDisplayController>();
        services.AddSingleton<IHardwareCoordinator, MockHardwareCoordinator>();

        services.AddSingleton<MainWindowViewModel>();
        services.AddSingleton<MainWindow>();
    }
}
