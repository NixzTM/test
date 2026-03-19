using System.Windows;
using CompanionRobot.App.ViewModels;

namespace CompanionRobot.App;

public partial class MainWindow : Window
{
    public MainWindow(MainWindowViewModel viewModel)
    {
        InitializeComponent();
        DataContext = viewModel;
    }
}
