package net.subaru.replayer.panel;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.FlatTextField;
import net.subaru.replayer.ReplayPlugin;
import net.subaru.replayer.replay.RecordingReplayer;
import net.unethicalite.client.Static;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.File;

public class Panel extends PluginPanel {
    private ReplayPlugin plugin;
    private JToggleButton recordReplayToggle;
    private JButton selectFolderButton;
    private FlatTextField selectedFolderField;
    private JButton startStopButton;
    private JButton pauseResumeButton;
    private JButton forwardButton;
    private JComboBox<String> speedComboBox;
    private JLabel replayLengthLabel;
    private JLabel totalTicksLabel;
    private JLabel currentTickLabel;
    private JTextField goToTickField;
    private JButton goToTickButton;

    public Panel(ReplayPlugin plugin) {
        this.plugin = plugin;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(new EmptyBorder(10, 10, 10, 10));

        add(createModePanel());
        add(Box.createRigidArea(new Dimension(0, 10)));
        add(createFolderPanel());
        add(Box.createRigidArea(new Dimension(0, 10)));
        add(createControlPanel());
        add(Box.createRigidArea(new Dimension(0, 10)));
        add(createInfoPanel());
        add(Box.createRigidArea(new Dimension(0, 10)));
        add(createGoToTickPanel());

        setupListeners();
    }

    private JPanel createModePanel() {
        JPanel modePanel = new JPanel();
        modePanel.setLayout(new BorderLayout());
        modePanel.setBorder(createTitledBorder("Mode"));

        recordReplayToggle = new JToggleButton("Recording");
        recordReplayToggle.setFocusPainted(false);
        modePanel.add(recordReplayToggle, BorderLayout.CENTER);

        startStopButton = new JButton("Start");
        startStopButton.setFocusPainted(false);
        modePanel.add(startStopButton, BorderLayout.SOUTH);

        return modePanel;
    }

    private JPanel createFolderPanel() {
        JPanel folderPanel = new JPanel();
        folderPanel.setLayout(new BorderLayout(5, 0));
        folderPanel.setBorder(createTitledBorder("Replay Folder"));

        selectFolderButton = new JButton("Select");
        selectFolderButton.setFocusPainted(false);
        folderPanel.add(selectFolderButton, BorderLayout.WEST);

        selectedFolderField = new FlatTextField();
        selectedFolderField.setEditable(false);
        selectedFolderField.setText("No folder selected");
        folderPanel.add(selectedFolderField, BorderLayout.CENTER);

        return folderPanel;
    }

    private JPanel createControlPanel() {
        JPanel controlPanel = new JPanel(new GridBagLayout());
        controlPanel.setBorder(createTitledBorder("Playback Controls"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);

        forwardButton = new JButton(">>");
        pauseResumeButton = new JButton("Pause");
        speedComboBox = new JComboBox<>(new String[]{"0.5x", "1x", "2x", "4x", "8x"});

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 1;

        gbc.gridx = 1;
        gbc.gridwidth = 2;
        controlPanel.add(pauseResumeButton, gbc);

        gbc.gridx = 3;
        gbc.gridwidth = 1;
        controlPanel.add(forwardButton, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        controlPanel.add(new JLabel("Playback Speed:"), gbc);

        gbc.gridx = 2;
        gbc.gridwidth = 2;
        controlPanel.add(speedComboBox, gbc);

        return controlPanel;
    }

    private JPanel createInfoPanel() {
        JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new GridLayout(3, 1, 5, 5));
        infoPanel.setBorder(createTitledBorder("Replay Information"));

        replayLengthLabel = new JLabel("Replay Length: 0 ms");
        totalTicksLabel = new JLabel("Total Ticks: 0");
        currentTickLabel = new JLabel("Current Tick: 0");

        infoPanel.add(replayLengthLabel);
        infoPanel.add(totalTicksLabel);
        infoPanel.add(currentTickLabel);

        return infoPanel;
    }

    private JPanel createGoToTickPanel() {
        JPanel goToTickPanel = new JPanel(new BorderLayout(5, 0));
        goToTickPanel.setBorder(createTitledBorder("Go To Tick"));

        goToTickField = new JTextField();
        goToTickButton = new JButton("Go");

        goToTickPanel.add(goToTickField, BorderLayout.CENTER);
        goToTickPanel.add(goToTickButton, BorderLayout.EAST);

        return goToTickPanel;
    }

    private TitledBorder createTitledBorder(String title) {
        TitledBorder border = BorderFactory.createTitledBorder(title);
        border.setTitleColor(ColorScheme.LIGHT_GRAY_COLOR);
        return border;
    }

    private void setupListeners() {
        recordReplayToggle.addActionListener(e -> {
            boolean isRecording = recordReplayToggle.isSelected();
            recordReplayToggle.setText(isRecording ? "Recording" : "Playback");
            plugin.setRecordMode(isRecording);
        });

        selectFolderButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

            File defaultFolder = new File(plugin.getRecordingPath().toString());
            if (defaultFolder.exists() && defaultFolder.isDirectory()) {
                fileChooser.setCurrentDirectory(defaultFolder);
            }

            int result = fileChooser.showOpenDialog(this);
            if (result == JFileChooser.APPROVE_OPTION) {
                File selectedFile = fileChooser.getSelectedFile();
                plugin.setRecordingFolder(selectedFile.toPath());
                selectedFolderField.setText(selectedFile.getAbsolutePath());
            }
        });

        startStopButton.addActionListener(e -> {
            boolean isRunning = plugin.toggleProxyServer();
            startStopButton.setText(isRunning ? "Stop" : "Start");

            RecordingReplayer replayer = plugin.getRecordingReplayer();

            if (replayer == null || !replayer.isPaused()) {
                return;
            }

            String selectedSpeed = (String) speedComboBox.getSelectedItem();
            double speed = Double.parseDouble(selectedSpeed.replace("x", ""));

            replayer.setSpeedMultiplier(speed);
        });

        pauseResumeButton.addActionListener(e -> {
            boolean isPaused = plugin.togglePause();
            SwingUtilities.invokeLater(() -> {
                pauseResumeButton.setText(isPaused ? "Resume" : "Pause");
            });
        });

        forwardButton.addActionListener(e -> {
            plugin.stepForward();
            RecordingReplayer replayer = plugin.getRecordingReplayer();

            if (replayer == null || !replayer.isPaused()) {
                return;
            }

            updateReplayInfo();
        });

        speedComboBox.addActionListener(e -> {
            String selectedSpeed = (String) speedComboBox.getSelectedItem();
            double speed = Double.parseDouble(selectedSpeed.replace("x", ""));
            Static.getClient().getLogger().info("Selected speed: {}", speed);
            plugin.setReplaySpeed(speed);
        });

        goToTickButton.addActionListener(e -> {
            try {
                int targetTick = Integer.parseInt(goToTickField.getText());
                RecordingReplayer replayer = plugin.getRecordingReplayer();
                if (replayer != null) {
                    replayer.goToTick(targetTick);
                    updateReplayInfo();
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Invalid tick number", "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    public void updateReplayInfo() {
        SwingUtilities.invokeLater(() -> {
            RecordingReplayer replayer = plugin.getRecordingReplayer();
            if (replayer != null) {
                replayLengthLabel.setText(String.format("Replay Length: %d ms", replayer.getReplayLength()));
                totalTicksLabel.setText(String.format("Total Ticks: %d", replayer.getTotalTicks()));
                currentTickLabel.setText(String.format("Current Tick: %d", replayer.getCurrentTick()));
            } else {
                replayLengthLabel.setText("Replay Length: Not loaded");
                totalTicksLabel.setText("Total Ticks: Not loaded");
                currentTickLabel.setText("Current Tick: Not loaded");
            }
        });
    }
}