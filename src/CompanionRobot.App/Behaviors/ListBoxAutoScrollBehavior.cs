using System.Collections.Specialized;
using System.Windows;
using System.Windows.Controls;

namespace CompanionRobot.App.Behaviors;

public static class ListBoxAutoScrollBehavior
{
    public static readonly DependencyProperty AutoScrollToEndProperty =
        DependencyProperty.RegisterAttached(
            "AutoScrollToEnd",
            typeof(bool),
            typeof(ListBoxAutoScrollBehavior),
            new PropertyMetadata(false, OnAutoScrollToEndChanged));

    public static bool GetAutoScrollToEnd(DependencyObject obj) => (bool)obj.GetValue(AutoScrollToEndProperty);

    public static void SetAutoScrollToEnd(DependencyObject obj, bool value) => obj.SetValue(AutoScrollToEndProperty, value);

    private static void OnAutoScrollToEndChanged(DependencyObject d, DependencyPropertyChangedEventArgs e)
    {
        if (d is not ListBox listBox || !(bool)e.NewValue)
        {
            return;
        }

        if (listBox.Items is INotifyCollectionChanged collection)
        {
            collection.CollectionChanged += (_, _) => ScrollToEnd(listBox);
        }
    }

    private static void ScrollToEnd(ListBox listBox)
    {
        if (listBox.Items.Count > 0)
        {
            listBox.ScrollIntoView(listBox.Items[listBox.Items.Count - 1]);
        }
    }
}
