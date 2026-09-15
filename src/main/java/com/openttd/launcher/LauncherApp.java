package com.openttd.launcher;

import com.openttd.launcher.model.InstalledVersion;
import com.openttd.launcher.model.ReleaseChannel;
import com.openttd.launcher.model.ReleaseInfo;
import com.openttd.launcher.service.InstallService;
import com.openttd.launcher.service.ReleaseService;
import com.openttd.launcher.service.Settings;
import com.openttd.launcher.service.TtdDataService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class LauncherApp {
    private static final Color BACKGROUND = new Color(19, 22, 28);
    private static final Color PANEL = new Color(29, 34, 43);
    private static final Color PANEL_ALT = new Color(35, 41, 51);
    private static final Color TEXT = new Color(232, 237, 243);
    private static final Color MUTED = new Color(155, 166, 180);
    private static final Color ACCENT = new Color(91, 177, 255);
    private static final Color SUCCESS = new Color(106, 210, 147);
    private static final Color BORDER = new Color(57, 67, 81);

    private final Settings settings = new Settings();
    private final ReleaseService releaseService = new ReleaseService();
    private final InstallService installService = new InstallService(releaseService);
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "openttd-launcher-worker"); thread.setDaemon(true); return thread;
    });
    private final JComboBox<ReleaseChannel> channelBox = new JComboBox<>(ReleaseChannel.values());
    private final JComboBox<ReleaseInfo> versionBox = new JComboBox<>();
    private final JButton historyButton = button("Older versions");
    private int historyPage;
    private boolean moreHistory = true;
    private boolean changingVersions;
    private final JLabel installedValue = valueLabel("Not installed");
    private final JLabel latestValue = valueLabel("Not checked");
    private final JLabel directoryValue = valueLabel("");
    private final JLabel statusValue = valueLabel("Ready");
    private final JProgressBar progress = new JProgressBar();
    private final JTextArea log = new JTextArea();
    private final JButton installButton = button("Install");
    private final JButton updateButton = button("Update");
    private final JButton repairButton = button("Repair");
    private final JButton launchButton = button("Launch OpenTTD");
    private final JButton checkButton = button("Check for updates");
    private final JButton folderButton = button("Choose folder");
    private final JButton ttdButton = button("Set up TTD files");
    private final TtdDataService ttdDataService = new TtdDataService();
    private boolean busy;
    private ReleaseInfo latest;
    private InstalledVersion installed;
    private JFrame frame;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LauncherApp().show());
    }

    private void show() {
        frame = new JFrame("OpenTTD Launcher");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setMinimumSize(new Dimension(760, 600));
        frame.setSize(860, 680);
        frame.setLocationByPlatform(true);
        frame.setContentPane(buildContent());
        frame.addWindowListener(new WindowAdapter() { @Override public void windowClosed(WindowEvent e) { worker.shutdownNow(); } });
        frame.setVisible(true);
        refreshDirectory();
        resetVersions();
        checkLatest();
    }

    private JPanel buildContent() {
        JPanel root = new JPanel(new BorderLayout(0, 18)); root.setBackground(BACKGROUND); root.setBorder(BorderFactory.createEmptyBorder(28, 32, 24, 32));
        root.add(header(), BorderLayout.NORTH);
        JPanel center = new JPanel(new BorderLayout(0, 16)); center.setOpaque(false); center.add(summary(), BorderLayout.NORTH); center.add(activity(), BorderLayout.CENTER);
        root.add(center, BorderLayout.CENTER); root.add(actions(), BorderLayout.SOUTH);
        return root;
    }

    private JPanel header() {
        JPanel panel = new JPanel(new BorderLayout()); panel.setOpaque(false);
        JLabel title = new JLabel("OpenTTD Launcher"); title.setForeground(TEXT); title.setFont(new Font("Segoe UI", Font.BOLD, 30));
        JLabel subtitle = new JLabel("Keep your railway empire moving"); subtitle.setForeground(MUTED); subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        JPanel copy = new JPanel(new GridLayout(2, 1, 0, 3)); copy.setOpaque(false); copy.add(title); copy.add(subtitle); panel.add(copy, BorderLayout.WEST);
        channelBox.setSelectedItem(settings.channel()); channelBox.setBackground(PANEL_ALT); channelBox.setForeground(TEXT); channelBox.setFont(new Font("Segoe UI", Font.PLAIN, 14)); channelBox.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
        channelBox.addActionListener(e -> { settings.channel((ReleaseChannel) channelBox.getSelectedItem()); resetVersions(); checkLatest(); });
        JPanel selector = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0)); selector.setOpaque(false); selector.add(label("CHANNEL")); selector.add(channelBox);
        versionBox.setPreferredSize(new Dimension(235, 34));
        versionBox.setMaximumRowCount(15);
        versionBox.setBackground(PANEL_ALT); versionBox.setForeground(TEXT);
        versionBox.addActionListener(e -> { if (!changingVersions) refreshInstalled(); });
        historyButton.addActionListener(e -> loadHistory());
        JPanel versions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8)); versions.setOpaque(false);
        versions.add(label("VERSION")); versions.add(versionBox); versions.add(historyButton);
        JPanel selectors = new JPanel(new GridLayout(2, 1)); selectors.setOpaque(false);
        selectors.add(selector); selectors.add(versions); panel.add(selectors, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel summary() {
        JPanel panel = new JPanel(new GridLayout(1, 3, 12, 0)); panel.setOpaque(false);
        panel.add(infoCard("INSTALLED", installedValue, "Detected in managed versions")); panel.add(infoCard("LATEST AVAILABLE", latestValue, "From the selected release source")); panel.add(infoCard("INSTALL LOCATION", directoryValue, "User data stays separate"));
        return panel;
    }

    private JPanel infoCard(String heading, JLabel value, String hint) {
        JPanel card = new JPanel(new BorderLayout(0, 7)); card.setBackground(PANEL); card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER), BorderFactory.createEmptyBorder(16, 17, 15, 17)));
        card.add(label(heading), BorderLayout.NORTH); value.setFont(new Font("Segoe UI", Font.BOLD, 19)); card.add(value, BorderLayout.CENTER); JLabel small = label(hint); small.setFont(new Font("Segoe UI", Font.PLAIN, 12)); card.add(small, BorderLayout.SOUTH); return card;
    }

    private JPanel activity() {
        JPanel panel = new JPanel(new BorderLayout(0, 10)); panel.setBackground(PANEL); panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER), BorderFactory.createEmptyBorder(18, 18, 18, 18)));
        JPanel top = new JPanel(new BorderLayout()); top.setOpaque(false); JLabel heading = new JLabel("Activity"); heading.setForeground(TEXT); heading.setFont(new Font("Segoe UI", Font.BOLD, 16)); top.add(heading, BorderLayout.WEST); top.add(statusValue, BorderLayout.EAST); panel.add(top, BorderLayout.NORTH);
        log.setEditable(false); log.setLineWrap(true); log.setWrapStyleWord(true); log.setBackground(PANEL_ALT); log.setForeground(MUTED); log.setFont(new Font("Consolas", Font.PLAIN, 13)); log.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12)); panel.add(new JScrollPane(log), BorderLayout.CENTER);
        progress.setStringPainted(true); progress.setForeground(ACCENT); progress.setBackground(PANEL_ALT); progress.setBorderPainted(false); progress.setString("Idle"); panel.add(progress, BorderLayout.SOUTH); return panel;
    }

    private JPanel actions() {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setOpaque(false);
        JPanel utilities = new JPanel(new BorderLayout());
        utilities.setOpaque(false);
        folderButton.addActionListener(e -> chooseFolder());
        checkButton.addActionListener(e -> checkLatest());
        utilities.add(folderButton, BorderLayout.WEST);
        utilities.add(checkButton, BorderLayout.EAST);
        ttdButton.setToolTipText("Download original TTD graphics and sound from tt-ms.de for the selected installation");
        ttdButton.addActionListener(e -> setupTtdFiles());
        JPanel dataAction = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0)); dataAction.setOpaque(false);
        dataAction.add(ttdButton); utilities.add(dataAction, BorderLayout.CENTER);
        panel.add(utilities, BorderLayout.NORTH);
        JPanel primary = new JPanel(new GridLayout(1, 4, 10, 0));
        primary.setOpaque(false);
        installButton.addActionListener(e -> install(false));
        updateButton.addActionListener(e -> { if (latest != null) { mergeVersions(java.util.List.of(latest)); versionBox.setSelectedItem(latest); install(false); } });
        repairButton.addActionListener(e -> install(true));
        launchButton.addActionListener(e -> launch());
        launchButton.setBackground(ACCENT);
        launchButton.setForeground(BACKGROUND);
        launchButton.setOpaque(true);
        launchButton.setToolTipText("Play the installed version, even when offline");
        repairButton.setToolTipText("Download and reinstall the selected version");
        primary.add(installButton);
        primary.add(updateButton);
        primary.add(repairButton);
        primary.add(launchButton);
        panel.add(primary, BorderLayout.SOUTH);
        return panel;
    }

    private void resetVersions() {
        latest = null;
        historyPage = 0;
        moreHistory = true;
        historyButton.setText("Older versions");
        changingVersions = true;
        versionBox.removeAllItems();
        changingVersions = false;
        try {
            var channel = (ReleaseChannel) channelBox.getSelectedItem();
            mergeVersions(installService.listInstalled(settings.installRoot(), channel).stream()
                    .map(v -> new ReleaseInfo(channel, v.version(), null, null)).toList());
        } catch (IOException ex) { log("Could not list installed versions: " + ex.getMessage()); }
        refreshInstalled();
    }

    private void mergeVersions(java.util.List<ReleaseInfo> releases) {
        ReleaseInfo previous = (ReleaseInfo) versionBox.getSelectedItem();
        var all = new java.util.LinkedHashMap<String, ReleaseInfo>();
        for (int i = 0; i < versionBox.getItemCount(); i++) {
            var item = versionBox.getItemAt(i); all.put(item.version(), item);
        }
        for (var release : releases) all.put(release.version(), release);
        changingVersions = true;
        versionBox.removeAllItems();
        for (var release : all.values()) versionBox.addItem(release);
        if (previous != null) versionBox.setSelectedItem(all.get(previous.version()));
        changingVersions = false;
    }

    private void loadHistory() {
        if (busy || !moreHistory) return;
        var channel = (ReleaseChannel) channelBox.getSelectedItem();
        int page = historyPage;
        setBusy(true, "Loading older versions...");
        worker.submit(() -> {
            try {
                var result = releaseService.fetchHistory(channel, page);
                SwingUtilities.invokeLater(() -> {
                    mergeVersions(result.releases());
                    historyPage++;
                    moreHistory = result.hasMore();
                    historyButton.setText(moreHistory ? "Load more" : "History loaded");
                    refreshInstalled();
                    setBusy(false, "Ready");
                    log("Loaded " + result.releases().size() + " historical versions for " + channel.displayName());
                });
            } catch (Exception ex) { SwingUtilities.invokeLater(() -> failure("Could not load older versions", ex)); }
        });
    }

    private void checkLatest() {
        if (busy) return;
        latest = null;
        latestValue.setText("Checking...");
        setBusy(true, "Checking releases...");
        ReleaseChannel channel = (ReleaseChannel) channelBox.getSelectedItem();
        log("Checking " + channel.displayName() + " on " + channel.sourceName());
        worker.submit(() -> { try { ReleaseInfo result = releaseService.fetchLatest(channel); SwingUtilities.invokeLater(() -> { latest = result; latestValue.setText(result.version()); mergeVersions(java.util.List.of(result)); refreshInstalled(); log("Latest release: " + result.label()); setBusy(false, "Ready"); status(installed != null && installed.version().equals(result.version()) ? "Up to date" : "Release available", SUCCESS); }); } catch (Exception ex) { SwingUtilities.invokeLater(() -> { latestValue.setText("Unavailable"); failure("Could not check releases", ex); }); } });
    }

    private void install(boolean repair) {
        if (busy) return;
        ReleaseInfo release = (ReleaseInfo) versionBox.getSelectedItem();
        if (release == null) return;
        if (!repair && installed != null) { log("Selected version is already installed."); return; }
        Path root = settings.installRoot(); setBusy(true, repair ? "Repairing..." : "Installing..."); progress.setValue(0);
        worker.submit(() -> { try { Path result = installService.install(releaseService.resolveDownload(release), root, (message, complete, total) -> SwingUtilities.invokeLater(() -> { status(message, TEXT); if (total > 0) { progress.setIndeterminate(false); progress.setMaximum((int) Math.min(Integer.MAX_VALUE, total)); progress.setValue((int) Math.min(Integer.MAX_VALUE, complete)); progress.setString(total == 1 ? message : formatBytes(complete) + " / " + formatBytes(total)); } else { progress.setIndeterminate(true); progress.setString(message); } })); SwingUtilities.invokeLater(() -> { log("Installed to " + result); refreshInstalled(); setBusy(false, "Ready"); status("Install complete", SUCCESS); }); } catch (Exception ex) { SwingUtilities.invokeLater(() -> failure("Installation failed", ex)); } });
    }

    private void launch() {
        if (installed == null) { failure("Nothing to launch", new IOException("Install a release first")); return; }
        try { ttdDataService.applyCached(settings.installRoot(), installed.executable()); new ProcessBuilder(installed.executable().toString()).directory(installed.directory().toFile()).start(); log("Launched " + installed.label()); status("Game running", SUCCESS); } catch (IOException ex) { failure("Could not launch OpenTTD", ex); }
    }

    private void setupTtdFiles() {
        if (busy || installed == null) return;
        InstalledVersion target = installed;
        Path root = settings.installRoot();
        setBusy(true, "Preparing TTD files...");
        log("Setting up original graphics and sound from " + TtdDataService.SOURCE);
        worker.submit(() -> {
            try {
                ttdDataService.prepare(root, (message, completed, total) -> SwingUtilities.invokeLater(() -> {
                    status(message, TEXT); progress.setString(message);
                }));
                ttdDataService.applyCached(root, target.executable());
                SwingUtilities.invokeLater(() -> {
                    setBusy(false, "TTD files ready");
                    status("Graphics and sound ready", SUCCESS);
                    log("Original TTD files installed for " + target.label() + ". Cached files will be reused for other versions.");
                });
            } catch (Exception ex) { SwingUtilities.invokeLater(() -> failure("Could not set up TTD files", ex)); }
        });
    }

    private void chooseFolder() { JFileChooser chooser = new JFileChooser(settings.installRoot().toFile()); chooser.setDialogTitle("Choose OpenTTD install directory"); chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY); if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) { settings.installRoot(chooser.getSelectedFile().toPath()); refreshDirectory(); resetVersions(); checkLatest(); log("Install directory changed."); } }
    private void refreshDirectory() { directoryValue.setText(shorten(settings.installRoot().toString(), 24)); directoryValue.setToolTipText(settings.installRoot().toString()); }
    private void refreshInstalled() {
        try {
            ReleaseInfo selected = (ReleaseInfo) versionBox.getSelectedItem();
            installed = selected == null ? null : installService.listInstalled(settings.installRoot(), (ReleaseChannel) channelBox.getSelectedItem())
                    .stream().filter(v -> v.version().equals(selected.version())).findFirst().orElse(null);
            installedValue.setText(installed == null ? "Not installed" : installed.version());
        } catch (IOException e) {
            installed = null;
            installedValue.setText("Unavailable");
            log("Could not inspect installed versions: " + e.getMessage());
        }
        refreshActions();
    }

    private void refreshActions() {
        ReleaseInfo selected = (ReleaseInfo) versionBox.getSelectedItem();
        boolean available = selected != null && selected.channel() == channelBox.getSelectedItem() && selected.pageUri() != null;
        boolean current = latest != null && installed != null && installed.version().equals(latest.version());
        versionBox.setEnabled(!busy && versionBox.getItemCount() > 0);
        historyButton.setEnabled(!busy && moreHistory);
        checkButton.setEnabled(!busy);
        channelBox.setEnabled(!busy);
        folderButton.setEnabled(!busy);
        ttdButton.setEnabled(!busy && installed != null);
        installButton.setEnabled(!busy && available && installed == null);
        updateButton.setEnabled(!busy && latest != null && installed != null && !current);
        repairButton.setEnabled(!busy && available && installed != null);
        launchButton.setEnabled(!busy && installed != null);
    }

    private void setBusy(boolean busy, String message) {
        this.busy = busy;
        refreshActions();
        status(message, MUTED);
        progress.setIndeterminate(busy);
        progress.setString(message);
        if (!busy) progress.setValue(0);
    }
    private void failure(String title, Exception ex) { setBusy(false, "Action failed"); status(title, new Color(255, 126, 126)); log(title + ": " + ex.getMessage()); JOptionPane.showMessageDialog(frame, ex.getMessage(), title, JOptionPane.ERROR_MESSAGE); }
    private void log(String message) { log.append(message + "\n"); log.setCaretPosition(log.getDocument().getLength()); }
    private void status(String message, Color color) { statusValue.setText(message); statusValue.setForeground(color); }
    private static JButton button(String text) { JButton button = new JButton(text); button.setFont(new Font("Segoe UI", Font.BOLD, 13)); button.setForeground(TEXT); button.setBackground(PANEL_ALT); button.setFocusPainted(false); button.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER), BorderFactory.createEmptyBorder(9, 13, 9, 13))); button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); return button; }
    private static JLabel label(String text) { JLabel label = new JLabel(text); label.setForeground(MUTED); label.setFont(new Font("Segoe UI", Font.BOLD, 11)); return label; }
    private static JLabel valueLabel(String text) { JLabel label = new JLabel(text); label.setForeground(TEXT); return label; }
    private static String shorten(String text, int max) { return text.length() <= max ? text : "..." + text.substring(text.length() - max + 3); }
    private static String formatBytes(long bytes) { if (bytes < 1024 * 1024) return (bytes / 1024) + " KB"; return String.format("%.1f MB", bytes / 1024d / 1024d); }
}
