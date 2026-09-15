package com.openttd.launcher;

import com.openttd.launcher.service.Settings;
import javax.swing.*;
import java.awt.*;

/** Applies preferences without rebuilding controls or adding duplicate listeners. */
final class Appearance {
    static void apply(Component component, Settings.Preferences preferences) {
        boolean dark = preferences.theme().equals("Dark");
        Color background = dark ? new Color(19,22,28) : new Color(243,246,250);
        Color surface = dark ? new Color(35,41,51) : Color.WHITE;
        Color text = dark ? new Color(232,237,243) : new Color(28,39,53);
        Color muted = dark ? new Color(170,181,195) : new Color(76,91,110);
        Color accent = Color.decode(preferences.accent());
        if (component instanceof JComponent c) {
            Font original = (Font)c.getClientProperty("originalFont");
            if (original == null && c.getFont() != null) { original = c.getFont(); c.putClientProperty("originalFont", original); }
            if (original != null) c.setFont(original.deriveFont(original.getSize2D() * preferences.scale() / 100f));
            Color originalBackground = (Color)c.getClientProperty("originalBackground");
            if (originalBackground == null) { originalBackground=c.getBackground(); c.putClientProperty("originalBackground",originalBackground); }
            c.setForeground(text);
            c.setBackground(c instanceof JPanel || c instanceof JTabbedPane ? background : surface);
            if (new Color(29,34,43).equals(originalBackground)) c.setBackground(dark ? new Color(29,34,43) : Color.WHITE);
            if (c instanceof JTextArea area) { area.setCaretColor(text); area.setSelectionColor(accent); }
            if (c instanceof JTextField field) { field.setCaretColor(text); field.setSelectionColor(accent); }
            if (c instanceof JLabel && c.getFont().getSize2D() < 14 * preferences.scale() / 100f) c.setForeground(muted);
            if (c instanceof JProgressBar) c.setForeground(accent);
            if (Boolean.TRUE.equals(c.getClientProperty("primary"))) {
                c.setBackground(accent);
                c.setForeground((accent.getRed()*299 + accent.getGreen()*587 + accent.getBlue()*114) / 1000 > 145 ? new Color(19,22,28) : Color.WHITE);
            }
        }
        if (component instanceof Container container) for (Component child : container.getComponents()) apply(child, preferences);
    }
}
