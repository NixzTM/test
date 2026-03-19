using System.Collections.ObjectModel;
using System.Windows;
using CompanionRobot.App.Commands;
using CompanionRobot.Core.Events;
using CompanionRobot.Core.Interfaces;
using CompanionRobot.Core.Models;
using Microsoft.Extensions.Logging;

namespace CompanionRobot.App.ViewModels;

public sealed class MainWindowViewModel : ObservableObject, IDisposable
{
    private readonly IChatService _chatService;
    private readonly ISpeechInputCoordinator _speechInputCoordinator;
    private readonly IHardwareService _hardwareService;
    private readonly IAppStateService _appStateService;
    private readonly IMemoryService _memoryService;
    private readonly ILogger<MainWindowViewModel> _logger;
    private readonly IDisposable _userMessageSubscription;
    private readonly IDisposable _assistantMessageSubscription;
    private readonly IDisposable _voiceInputSubscription;
    private readonly IDisposable _hardwareSubscription;
    private string _inputText = string.Empty;
    private string _transcriptPreview = string.Empty;
    private string _statusMessage = "Ready.";
    private string _warningMessage = string.Empty;
    private string _errorMessage = string.Empty;
    private string _aiProviderName = "Unknown";
    private string _speechProviderName = "Unknown";
    private bool _isBusy;
    private bool _isListening;
    private bool _hardwareEnabled;
    private string _hardwareStatus = "No mock hardware activity yet.";
    private string _hardwareDisplayText = "CompanionRobot online";

    public MainWindowViewModel(
        IChatService chatService,
        ISpeechInputCoordinator speechInputCoordinator,
        IHardwareService hardwareService,
        IAppStateService appStateService,
        IMemoryService memoryService,
        IEventBus eventBus,
        ILogger<MainWindowViewModel> logger)
    {
        _chatService = chatService;
        _speechInputCoordinator = speechInputCoordinator;
        _hardwareService = hardwareService;
        _appStateService = appStateService;
        _memoryService = memoryService;
        _logger = logger;

        Messages = new ObservableCollection<ChatMessage>(_memoryService.GetAllMessages());

        SendCommand = new AsyncRelayCommand(SendAsync, CanSend);
        StartListeningCommand = new AsyncRelayCommand(StartListeningAsync, () => !IsListening);
        StopListeningCommand = new AsyncRelayCommand(StopListeningAsync, () => IsListening);
        ClearConversationCommand = new RelayCommand(ClearConversation, () => !IsBusy);
        WaveCommand = new AsyncRelayCommand(WaveAsync, () => !IsBusy);
        DriveForwardCommand = new AsyncRelayCommand(DriveForwardAsync, () => !IsBusy);
        ReadSensorsCommand = new AsyncRelayCommand(ReadSensorsAsync, () => !IsBusy);
        DisplayStatusCommand = new AsyncRelayCommand(DisplayStatusAsync, () => !IsBusy && !string.IsNullOrWhiteSpace(HardwareDisplayText));

        _userMessageSubscription = eventBus.Subscribe<UserMessageReceived>(OnUserMessageReceivedAsync);
        _assistantMessageSubscription = eventBus.Subscribe<AIResponseGenerated>(OnAiResponseGeneratedAsync);
        _voiceInputSubscription = eventBus.Subscribe<VoiceInputRecognized>(OnVoiceInputRecognizedAsync);
        _hardwareSubscription = eventBus.Subscribe<HardwareCommandRequested>(OnHardwareCommandRequestedAsync);

        _appStateService.StateChanged += OnAppStateChanged;
        ApplyState(_appStateService.Current);
    }

    public ObservableCollection<ChatMessage> Messages { get; }

    public string InputText
    {
        get => _inputText;
        set
        {
            if (SetProperty(ref _inputText, value))
            {
                NotifyCommandStates();
            }
        }
    }

    public string TranscriptPreview
    {
        get => _transcriptPreview;
        private set => SetProperty(ref _transcriptPreview, value);
    }

    public string StatusMessage
    {
        get => _statusMessage;
        private set => SetProperty(ref _statusMessage, value);
    }

    public string WarningMessage
    {
        get => _warningMessage;
        private set => SetProperty(ref _warningMessage, value);
    }

    public string ErrorMessage
    {
        get => _errorMessage;
        private set => SetProperty(ref _errorMessage, value);
    }

    public string AIProviderName
    {
        get => _aiProviderName;
        private set
        {
            if (SetProperty(ref _aiProviderName, value))
            {
                RaisePropertyChanged(nameof(ProviderSummary));
            }
        }
    }

    public string SpeechProviderName
    {
        get => _speechProviderName;
        private set
        {
            if (SetProperty(ref _speechProviderName, value))
            {
                RaisePropertyChanged(nameof(ProviderSummary));
            }
        }
    }

    public bool IsBusy
    {
        get => _isBusy;
        private set
        {
            if (SetProperty(ref _isBusy, value))
            {
                RaisePropertyChanged(nameof(BusySummary));
                NotifyCommandStates();
            }
        }
    }

    public bool IsListening
    {
        get => _isListening;
        private set
        {
            if (SetProperty(ref _isListening, value))
            {
                RaisePropertyChanged(nameof(ListeningSummary));
                NotifyCommandStates();
            }
        }
    }

    public bool HardwareEnabled
    {
        get => _hardwareEnabled;
        private set
        {
            if (SetProperty(ref _hardwareEnabled, value))
            {
                RaisePropertyChanged(nameof(HardwareEnabledSummary));
            }
        }
    }

    public string HardwareStatus
    {
        get => _hardwareStatus;
        private set => SetProperty(ref _hardwareStatus, value);
    }

    public string HardwareDisplayText
    {
        get => _hardwareDisplayText;
        set
        {
            if (SetProperty(ref _hardwareDisplayText, value))
            {
                NotifyCommandStates();
            }
        }
    }

    public string ProviderSummary => $"AI: {AIProviderName} • Speech: {SpeechProviderName}";
    public string BusySummary => IsBusy ? "Robot brain busy..." : "Idle";
    public string ListeningSummary => IsListening ? "Listening is active." : "Listening is stopped.";
    public string HardwareEnabledSummary => HardwareEnabled ? "Enabled" : "Mock-only / disabled";

    public AsyncRelayCommand SendCommand { get; }
    public AsyncRelayCommand StartListeningCommand { get; }
    public AsyncRelayCommand StopListeningCommand { get; }
    public RelayCommand ClearConversationCommand { get; }
    public AsyncRelayCommand WaveCommand { get; }
    public AsyncRelayCommand DriveForwardCommand { get; }
    public AsyncRelayCommand ReadSensorsCommand { get; }
    public AsyncRelayCommand DisplayStatusCommand { get; }

    private bool CanSend() => !IsBusy && !string.IsNullOrWhiteSpace(InputText);

    private async Task SendAsync()
    {
        var content = InputText;
        InputText = string.Empty;

        try
        {
            var sent = await _chatService.ProcessUserInputAsync(content, "Typed");
            if (!sent)
            {
                InputText = content;
            }
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to send chat message.");
            ErrorMessage = ex.Message;
            InputText = content;
        }
    }

    private async Task StartListeningAsync()
    {
        try
        {
            await _speechInputCoordinator.StartListeningAsync();
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to start listening.");
            ErrorMessage = ex.Message;
        }
    }

    private async Task StopListeningAsync()
    {
        try
        {
            await _speechInputCoordinator.StopListeningAsync();
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to stop listening.");
            ErrorMessage = ex.Message;
        }
    }

    private void ClearConversation()
    {
        _memoryService.Clear();
        Dispatch(Messages.Clear);
        StatusMessage = "Conversation cleared. Ready.";
        WarningMessage = string.Empty;
        ErrorMessage = string.Empty;
    }

    private async Task WaveAsync()
    {
        try
        {
            await _hardwareService.WaveAsync();
            HardwareStatus = "Mock wave sequence triggered.";
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to trigger mock wave.");
            ErrorMessage = ex.Message;
        }
    }

    private async Task DriveForwardAsync()
    {
        try
        {
            await _hardwareService.DriveForwardAsync();
            HardwareStatus = "Mock drive forward command triggered.";
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to trigger mock drive forward.");
            ErrorMessage = ex.Message;
        }
    }

    private async Task ReadSensorsAsync()
    {
        try
        {
            HardwareStatus = await _hardwareService.ReadSensorSnapshotAsync();
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to read mock sensors.");
            ErrorMessage = ex.Message;
        }
    }

    private async Task DisplayStatusAsync()
    {
        try
        {
            await _hardwareService.DisplayStatusAsync(HardwareDisplayText);
            HardwareStatus = $"Display updated with: {HardwareDisplayText}";
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to update mock display.");
            ErrorMessage = ex.Message;
        }
    }

    private Task OnUserMessageReceivedAsync(UserMessageReceived evt)
    {
        Dispatch(() => Messages.Add(evt.Message));
        return Task.CompletedTask;
    }

    private Task OnAiResponseGeneratedAsync(AIResponseGenerated evt)
    {
        Dispatch(() => Messages.Add(evt.Message));
        return Task.CompletedTask;
    }

    private Task OnVoiceInputRecognizedAsync(VoiceInputRecognized evt)
    {
        if (!evt.AutoSent)
        {
            Dispatch(() => InputText = evt.Transcript);
        }

        return Task.CompletedTask;
    }

    private Task OnHardwareCommandRequestedAsync(HardwareCommandRequested evt)
    {
        Dispatch(() => HardwareStatus = $"{evt.Device} -> {evt.Command} ({evt.Payload})");
        return Task.CompletedTask;
    }

    private void OnAppStateChanged(object? sender, AppStateSnapshot state) => Dispatch(() => ApplyState(state));

    private void ApplyState(AppStateSnapshot state)
    {
        StatusMessage = state.StatusMessage;
        WarningMessage = state.LastWarning ?? string.Empty;
        ErrorMessage = state.LastError ?? string.Empty;
        TranscriptPreview = state.TranscriptPreview;
        AIProviderName = state.AIProvider;
        SpeechProviderName = state.SpeechProvider;
        IsBusy = state.IsBusy;
        IsListening = state.IsListening;
        HardwareEnabled = state.HardwareEnabled;
    }

    private void NotifyCommandStates()
    {
        SendCommand.NotifyCanExecuteChanged();
        StartListeningCommand.NotifyCanExecuteChanged();
        StopListeningCommand.NotifyCanExecuteChanged();
        ClearConversationCommand.NotifyCanExecuteChanged();
        WaveCommand.NotifyCanExecuteChanged();
        DriveForwardCommand.NotifyCanExecuteChanged();
        ReadSensorsCommand.NotifyCanExecuteChanged();
        DisplayStatusCommand.NotifyCanExecuteChanged();
    }

    private static void Dispatch(Action action)
    {
        var dispatcher = Application.Current?.Dispatcher;
        if (dispatcher is null || dispatcher.CheckAccess())
        {
            action();
            return;
        }

        dispatcher.Invoke(action);
    }

    public void Dispose()
    {
        _logger.LogInformation("Disposing main window view model subscriptions.");
        _appStateService.StateChanged -= OnAppStateChanged;
        _userMessageSubscription.Dispose();
        _assistantMessageSubscription.Dispose();
        _voiceInputSubscription.Dispose();
        _hardwareSubscription.Dispose();
    }
}
