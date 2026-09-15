package com.openttd.launcher;

import com.openttd.launcher.service.Settings;
import javax.swing.*;
import java.awt.*;

final class SettingsDialog {
    private final JDialog dialog;
    private final JComboBox<String> theme = new JComboBox<>(new String[]{"Dark", "Light"});
    private final JButton accent = new JButton("Choose color...");
    private Color accentColor;
    private final JSpinner scale = new JSpinner(new SpinnerNumberModel(100,85,150,5));
    private final JCheckBox startup = new JCheckBox("Check for game updates when the launcher opens");
    private final JCheckBox minimize = new JCheckBox("Minimize the launcher after starting a game");
    private final JCheckBox restore = new JCheckBox("Restore the launcher when the game closes");
    private final JCheckBox timestamps = new JCheckBox("Show timestamps in new activity messages");
    private final JSpinner lines = new JSpinner(new SpinnerNumberModel(1000,100,10000,100));
    private final JTextArea arguments = new JTextArea(7,32);

    SettingsDialog(JFrame owner, Settings settings, Runnable saved) {
        dialog = new JDialog(owner, "Launcher settings", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        JPanel root = new JPanel(new BorderLayout(12,12));
        root.setBorder(BorderFactory.createEmptyBorder(18,18,18,18));
        JTabbedPane tabs = new JTabbedPane();
        JPanel appearance = form();
        row(appearance,"Theme",theme); row(appearance,"Accent color",accent); row(appearance,"Text size (%)",scale);
        appearance.add(new JLabel("Appearance changes apply when you save."));
        JPanel behavior = form();
        behavior.add(startup); behavior.add(minimize); behavior.add(restore); behavior.add(timestamps);
        row(behavior,"Activity history (lines)",lines);
        behavior.add(new JLabel("Change the game folder using Choose folder in the main window."));
        JPanel launch = form();
        launch.add(new JLabel("Extra game arguments (one argument per line):"));
        launch.add(new JScrollPane(arguments));
        launch.add(new JLabel("Example: put -r on one line, then 1280x720 on the next."));
        launch.add(new JLabel("Paths with spaces stay on one line; do not add quotes."));
        launch.add(new JLabel("Supported options depend on the selected game version."));
        tabs.addTab("Appearance",new JScrollPane(appearance));
        tabs.addTab("Behavior",new JScrollPane(behavior));
        tabs.addTab("Game launch",new JScrollPane(launch));
        root.add(tabs,BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton reset = new JButton("Reset defaults"), cancel = new JButton("Cancel"), save = new JButton("Save settings");
        save.putClientProperty("primary",true);
        buttons.add(reset); buttons.add(cancel); buttons.add(save); root.add(buttons,BorderLayout.SOUTH);
        accent.addActionListener(e -> { Color c = JColorChooser.showDialog(dialog,"Accent color",accentColor); if (c != null) { accentColor=c; updateAccent(); } });
        reset.addActionListener(e -> populate(Settings.defaults()));
        cancel.addActionListener(e -> dialog.dispose());
        save.addActionListener(e -> {
            try {
                scale.commitEdit(); lines.commitEdit();
                settings.preferences(new Settings.Preferences((String)theme.getSelectedItem(),String.format("#%06x",accentColor.getRGB() & 0xffffff),
                    (Integer)scale.getValue(),startup.isSelected(),minimize.isSelected(),restore.isSelected(),timestamps.isSelected(),(Integer)lines.getValue(),arguments.getText()));
                saved.run(); dialog.dispose();
            } catch (Exception ex) { JOptionPane.showMessageDialog(dialog,ex.getMessage(),"Could not save settings",JOptionPane.ERROR_MESSAGE); }
        });
        populate(settings.preferences());
        dialog.setContentPane(root); Appearance.apply(root,settings.preferences());
        dialog.getRootPane().setDefaultButton(save);
        dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),KeyStroke.getKeyStroke("ESCAPE"),JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.setSize(Math.max(640,settings.preferences().scale()*6),Math.max(460,settings.preferences().scale()*4));
        dialog.setMinimumSize(new Dimension(600,440)); dialog.setLocationRelativeTo(owner);
    }
    void show() { dialog.setVisible(true); }
    private void populate(Settings.Preferences p) {
        theme.setSelectedItem(p.theme()); accentColor=Color.decode(p.accent()); updateAccent(); scale.setValue(p.scale());
        startup.setSelected(p.checkOnStartup()); minimize.setSelected(p.minimizeOnLaunch()); restore.setSelected(p.restoreOnExit());
        timestamps.setSelected(p.timestamps()); lines.setValue(p.logLines()); arguments.setText(p.arguments());
    }
    private void updateAccent() { accent.setText(String.format("Choose color...  #%06X",accentColor.getRGB() & 0xffffff)); }
    private static JPanel form() {
        JPanel panel=new JPanel(); panel.setLayout(new BoxLayout(panel,BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(18,18,18,18)); return panel;
    }
    private static void row(JPanel panel,String label,JComponent control) {
        JPanel row=new JPanel(new FlowLayout(FlowLayout.LEFT,12,8)); row.add(new JLabel(label)); row.add(control);
        row.setAlignmentX(Component.LEFT_ALIGNMENT); row.setMaximumSize(new Dimension(Integer.MAX_VALUE,65)); panel.add(row);
    }
}
