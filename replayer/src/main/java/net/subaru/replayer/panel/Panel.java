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
    private JButton backwardButton;
    private JButton forwardButton;
    private JComboBox<String> speedComboBox;

    public Panel(ReplayPlugin plugin) {
        this.plugin = plugin;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(new EmptyBorder(10, 10, 10, 10));

        add(createModePanel());
        add(Box.createRigidArea(new Dimension(0, 10)));
        add(createFolderPanel());
        add(Box.createRigidArea(new Dimension(0, 10)));
        add(createControlPanel());

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

        backwardButton = new JButton("<<");
        forwardButton = new JButton(">>");
        pauseResumeButton = new JButton("Pause");
        speedComboBox = new JComboBox<>(new String[]{"0.5x", "1x", "2x", "4x", "8x"});

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 1;
        controlPanel.add(backwardButton, gbc);

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

            if (replayer == null || !replayer.isPaused())
            {
                return;
            }

            String selectedSpeed = (String) speedComboBox.getSelectedItem();
            double speed = Double.parseDouble(selectedSpeed.replace("x", ""));

            replayer.setSpeedMultiplier(speed);
        });

        pauseResumeButton.addActionListener(e -> {
            boolean isPaused = plugin.togglePause();
            pauseResumeButton.setText(isPaused ? "Resume" : "Pause");
        });

        backwardButton.addActionListener(e -> {
            plugin.stepBackward();
            RecordingReplayer replayer = plugin.getRecordingReplayer();

            if (replayer == null || !replayer.isPaused())
            {
                return;
            }

            pauseResumeButton.setText("Resume");
        });

        forwardButton.addActionListener(e -> {
            plugin.stepForward();
            RecordingReplayer replayer = plugin.getRecordingReplayer();

            if (replayer == null || !replayer.isPaused())
            {
                return;
            }
            pauseResumeButton.setText("Resume");
        });

        speedComboBox.addActionListener(e -> {
            String selectedSpeed = (String) speedComboBox.getSelectedItem();
            double speed = Double.parseDouble(selectedSpeed.replace("x", ""));
            Static.getClient().getLogger().info("Selected speed: {}", speed);
            plugin.setReplaySpeed(speed);
        });
    }
}