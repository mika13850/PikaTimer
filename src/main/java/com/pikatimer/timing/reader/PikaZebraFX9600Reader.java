/*
 * Copyright (C) 2024 John Garner <segfaultcoredump@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.pikatimer.timing.reader;

import com.mot.rfid.api3.InvalidUsageException;
import com.mot.rfid.api3.OperationFailureException;
import com.mot.rfid.api3.RFIDReader;
import com.mot.rfid.api3.RfidEventsListener;
import com.mot.rfid.api3.RfidReadEvents;
import com.mot.rfid.api3.RfidStatusEvents;
import com.mot.rfid.api3.TRACE_LEVEL;
import com.mot.rfid.api3.TagData;
import com.mot.rfid.api3.TagDataArray;
import com.mot.rfid.api3.TagStorageSettings;
import com.pikatimer.event.Event;
import com.pikatimer.timing.RawTimeData;
import com.pikatimer.timing.TimingListener;
import com.pikatimer.timing.TimingReader;
import com.pikatimer.util.DurationFormatter;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.controlsfx.control.ToggleSwitch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Zebra FX 9600 RFID Reader implementation using LLRP protocol Supports network
 * connectivity, power level configuration, and filtering
 * 
 * @author PikaTimer Team
 */
public class PikaZebraFX9600Reader implements TimingReader {
    private static final Logger logger = LoggerFactory.getLogger(PikaZebraFX9600Reader.class);

    protected TimingListener timingListener;
    protected String readerIP;
    protected static final String DEFAULT_READER_IP = "169.254.152.175";
    protected Integer readerPort = 5084; // Default LLRP port
    protected Integer timingWindow = 5000; // Default Timing Window

    protected static final Map<String, Long> lastReads = new ConcurrentHashMap<>();

    private Pane displayPane;
    private VBox displayVBox;
    private TextField readerIPTextField;
    private TextField readerPortTextField;
    private TextField timingWindowTextField;
    private Label statusLabel;
    private Label lastReadLabel;
    private ToggleSwitch connectToggleSwitch;
    private ToggleSwitch readToggleSwitch;

    // Configuration controls
    private Spinner<Integer> powerLevelSpinner;
    private CheckBox antenna1CheckBox;
    private CheckBox antenna2CheckBox;
    private CheckBox antenna3CheckBox;
    private CheckBox antenna4CheckBox;
    private TextField epcFilterTextField;
    private Button applySettingsButton;

    protected final BooleanProperty readingStatus = new SimpleBooleanProperty(false);
    protected final BooleanProperty connectedStatus = new SimpleBooleanProperty(false);

    private Boolean connectToReader = false;
    private Thread readerConnectionThread;

    private RFIDReader reader = null;

    public PikaZebraFX9600Reader() {
        logger.info("Zebra FX 9600 Reader initialized");
    }

    @Override
    public void setTimingListener(TimingListener t) {
        timingListener = t;

        // Load saved settings
        readerIP = timingListener.getAttribute("ZebraFX9600:reader_ip");
        if (readerIP == null || readerIP.isEmpty()) {
            readerIP = DEFAULT_READER_IP;
            timingListener.setAttribute("ZebraFX9600:reader_ip", readerIP);
        }

        String readerPortStr = timingListener.getAttribute("ZebraFX9600:reader_port");
        if (readerPortStr != null && !readerPortStr.isEmpty()) {
            try {
                readerPort = Integer.parseInt(readerPortStr);
            } catch (NumberFormatException e) {
                readerPort = 5084;
            }
        }

        String timingWindowStr = timingListener.getAttribute("ZebraFX9600:timing_window");
        if (timingWindowStr != null && !timingWindowStr.isEmpty()) {
            try {
                timingWindow = Integer.parseInt(timingWindowStr);
            } catch (NumberFormatException e) {
                timingWindow = 5000;
            }
        }

        logger.debug("ZebraFX9600: Loaded settings - IP: {}, Port: {}, Timing window: {}", readerIP, readerPort,
                timingWindow);
    }

    @Override
    public void showControls(Pane p) {
        if (displayPane == null) {
            initializeUI();
        }

        displayPane = p;
        displayPane.getChildren().clear();
        displayPane.getChildren().add(displayVBox);
    }

    private void initializeUI() {
        displayVBox = new VBox();
        displayVBox.setSpacing(10);
        displayVBox.setPadding(new Insets(10));

        // Connection section
        HBox connectionHBox = new HBox(10);
        connectionHBox.setAlignment(Pos.CENTER_LEFT);

        Label ipLabel = new Label("Reader IP:");
        readerIPTextField = new TextField(readerIP);
        readerIPTextField.setPrefWidth(120);

        Label portLabel = new Label("Port:");
        readerPortTextField = new TextField(readerPort.toString());
        readerPortTextField.setPrefWidth(60);

        Label timingWindowLabel = new Label("Timing Window:");
        timingWindowTextField = new TextField(timingWindow.toString());
        timingWindowTextField.setPrefWidth(60);

        connectToggleSwitch = new ToggleSwitch("Connect");
        connectToggleSwitch.setPadding(new Insets(3, 0, 0, 0));

        readToggleSwitch = new ToggleSwitch("Read");
        readToggleSwitch.setPadding(new Insets(3, 0, 0, 0));
        readToggleSwitch.disableProperty().bind(connectedStatus.not());

        Label spacer = new Label();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        connectionHBox.getChildren().addAll(ipLabel, readerIPTextField, portLabel, readerPortTextField,
                timingWindowLabel, timingWindowTextField, connectToggleSwitch, readToggleSwitch, spacer);

        // Status section
        statusLabel = new Label("Disconnected");
        statusLabel.setPrefWidth(200);
        lastReadLabel = new Label("");
        lastReadLabel.setPrefWidth(400);

        HBox statusHBox = new HBox(10);
        statusHBox.getChildren().addAll(statusLabel, lastReadLabel);

        // Advanced settings
        TitledPane settingsPane = createSettingsPane();
        settingsPane.setExpanded(false);

        displayVBox.getChildren().addAll(connectionHBox, statusHBox, settingsPane);

        // Setup listeners
        setupListeners();
    }

    private TitledPane createSettingsPane() {
        TitledPane settingsPane = new TitledPane();
        settingsPane.setText("Advanced Settings");

        VBox settingsVBox = new VBox(10);
        settingsVBox.setPadding(new Insets(10));

        // Power Level
        HBox powerHBox = new HBox(10);
        powerHBox.setAlignment(Pos.CENTER_LEFT);
        Label powerLabel = new Label("Transmit Power (dBm):");
        powerLabel.setPrefWidth(150);
        powerLevelSpinner = new Spinner<>(10, 30, 27); // 10-30 dBm range, default 27
        powerLevelSpinner.setEditable(true);
        powerLevelSpinner.setPrefWidth(80);
        powerHBox.getChildren().addAll(powerLabel, powerLevelSpinner);

        // Antenna Selection
        GridPane antennaGrid = new GridPane();
        antennaGrid.setHgap(15);
        antennaGrid.setVgap(5);
        Label antennaLabel = new Label("Active Antennas:");
        antennaLabel.setPrefWidth(150);

        antenna1CheckBox = new CheckBox("Antenna 1");
        antenna1CheckBox.setSelected(true);
        antenna2CheckBox = new CheckBox("Antenna 2");
        antenna2CheckBox.setSelected(true);
        antenna3CheckBox = new CheckBox("Antenna 3");
        antenna3CheckBox.setSelected(false);
        antenna4CheckBox = new CheckBox("Antenna 4");
        antenna4CheckBox.setSelected(false);

        antennaGrid.add(antennaLabel, 0, 0);
        antennaGrid.add(antenna1CheckBox, 1, 0);
        antennaGrid.add(antenna2CheckBox, 2, 0);
        antennaGrid.add(antenna3CheckBox, 1, 1);
        antennaGrid.add(antenna4CheckBox, 2, 1);

        // EPC Filter
        HBox filterHBox = new HBox(10);
        filterHBox.setAlignment(Pos.CENTER_LEFT);
        Label filterLabel = new Label("EPC Filter (hex):");
        filterLabel.setPrefWidth(150);
        epcFilterTextField = new TextField();
        epcFilterTextField.setPromptText("e.g., 3000 (optional)");
        epcFilterTextField.setPrefWidth(200);
        filterHBox.getChildren().addAll(filterLabel, epcFilterTextField);

        // Apply button
        applySettingsButton = new Button("Apply Settings");
        applySettingsButton.setOnAction(e -> applySettings());
        applySettingsButton.disableProperty().bind(connectedStatus.not());

        settingsVBox.getChildren().addAll(powerHBox, antennaGrid, filterHBox, applySettingsButton);

        settingsPane.setContent(settingsVBox);
        return settingsPane;
    }

    private void setupListeners() {
        // IP validation
        readerIPTextField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (validateIP(newVal)) {
                readerIP = newVal;
                timingListener.setAttribute("ZebraFX9600:reader_ip", readerIP);
            } else if (!newVal.isEmpty()) {
                Platform.runLater(() -> readerIPTextField.setText(oldVal));
            }
        });

        // Port validation
        readerPortTextField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.matches("\\d{0,5}")) {
                try {
                    if (!newVal.isEmpty()) {
                        int port = Integer.parseInt(newVal);
                        if (port > 0 && port <= 65535) {
                            readerPort = port;
                            timingListener.setAttribute("ZebraFX9600:reader_port", String.valueOf(readerPort));
                        }
                    }
                } catch (NumberFormatException e) {
                    Platform.runLater(() -> readerPortTextField.setText(oldVal));
                }
            } else {
                Platform.runLater(() -> readerPortTextField.setText(oldVal));
            }
        });

        // Timing window validation
        timingWindowTextField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.matches("\\d{0,6}")) {
                try {
                    if (!newVal.isEmpty()) {
                        int newTimingWindow = Integer.parseInt(newVal);
                        if (newTimingWindow > 0) {
                            timingWindow = newTimingWindow;
                            timingListener.setAttribute("ZebraFX9600:timing_window", String.valueOf(timingWindow));
                        }
                    }
                } catch (NumberFormatException e) {
                    Platform.runLater(() -> timingWindowTextField.setText(oldVal));
                }
            } else {
                Platform.runLater(() -> timingWindowTextField.setText(oldVal));
            }
        });

        // Connect toggle
        connectToggleSwitch.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                connect();
            } else {
                disconnect();
            }
        });

        // Read toggle
        readToggleSwitch.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                startReading();
            } else {
                stopReading();
            }
        });

        connectToggleSwitch.selectedProperty().bindBidirectional(connectedStatus);
        readToggleSwitch.selectedProperty().bindBidirectional(readingStatus);
    }

    private boolean validateIP(String ip) {
        if (ip == null || ip.isEmpty())
            return false;
        String[] parts = ip.split("\\.");
        if (parts.length != 4)
            return false;

        try {
            for (String part : parts) {
                int num = Integer.parseInt(part);
                if (num < 0 || num > 255)
                    return false;
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    public BooleanProperty getReadingStatus() {
        return readingStatus;
    }

    @Override
    public Boolean chipIsBib() {
        return Boolean.TRUE;
    }

    private void connect() {
        if (connectToReader)
            return;

        connectToReader = true;
        logger.info("Connecting to Zebra FX 9600 at {}:{}", readerIP, readerPort);
        Platform.runLater(() -> statusLabel.setText("Connecting..."));

        readerConnectionThread = new Thread(() -> {
            try {
                reader = new RFIDReader();
                reader.setHostName(readerIP);
                reader.setPort(readerPort);
                reader.connect();

                Platform.runLater(() -> {
                    connectedStatus.set(true);
                    statusLabel.setText("Connected");
                });
                logger.info("Successfully connected to Zebra FX 9600");

                // Add event handler
                reader.Events.setInventoryStartEvent(true);
                reader.Events.setInventoryStopEvent(true);
                reader.Events.setAccessStartEvent(true);
                reader.Events.setAccessStopEvent(true);
                reader.Events.setAntennaEvent(true);
                reader.Events.setGPIEvent(true);
                reader.Events.setBufferFullEvent(true);
                reader.Events.setBufferFullWarningEvent(true);
                reader.Events.setReaderDisconnectEvent(true);
                reader.Events.setReaderExceptionEvent(true);
                reader.Events.setTagReadEvent(true);
                reader.Events.setAttachTagDataWithReadEvent(false);
                reader.Events.addEventsListener(new ReadNotifyHandler());

                // Configure tag storage settings
                try {
                    TagStorageSettings tagStorageSettings = reader.Config.getTagStorageSettings();
                    tagStorageSettings.discardTagsOnInventoryStop(true);
                    reader.Config.setTagStorageSettings(tagStorageSettings);
                } catch (Exception e) {
                    logger.error("Failed to configure tag storage", e);
                    throw new RuntimeException("Failed to configure tag storage", e);
                }
                reader.Config.setTraceLevel(TRACE_LEVEL.TRACE_LEVEL_ERROR);

                // Get Reader capabilities
                logCapabilities();

                // Apply initial settings
                applySettings();

            } catch (Exception e) {
                connectToReader = false;
                logger.error("Connection failed", e);
                Platform.runLater(() -> {
                    connectedStatus.set(false);
                    statusLabel.setText("Connection failed");
                });
            }
        });
        readerConnectionThread.setDaemon(true);
        readerConnectionThread.start();
    }

    private void logCapabilities() {
        // Get Reader capabilities
        logger.debug("Reader ID: " + reader.ReaderCapabilities.ReaderID.getID());
        logger.debug("ModelName: " + reader.ReaderCapabilities.getModelName());
        logger.debug("Communication Standard: " + reader.ReaderCapabilities.getCommunicationStandard().toString());
        logger.debug("Country Code: " + reader.ReaderCapabilities.getCountryCode());
        logger.debug("FirwareVersion: " + reader.ReaderCapabilities.getFirwareVersion());
        logger.debug("RSSI Filter: " + reader.ReaderCapabilities.isRSSIFilterSupported());
        logger.debug("Tag Event Reporting: " + reader.ReaderCapabilities.isTagEventReportingSupported());
        logger.debug("Tag Locating Reporting: " + reader.ReaderCapabilities.isTagLocationingSupported());
        logger.debug("NXP Command Support: " + reader.ReaderCapabilities.isNXPCommandSupported());
        logger.debug("BlockEraseSupport: " + reader.ReaderCapabilities.isBlockEraseSupported());
        logger.debug("BlockWriteSupport: " + reader.ReaderCapabilities.isBlockWriteSupported());
        logger.debug("BlockPermalockSupport: " + reader.ReaderCapabilities.isBlockPermalockSupported());
        logger.debug("RecommisionSupport: " + reader.ReaderCapabilities.isRecommisionSupported());
        logger.debug("WriteWMISupport: " + reader.ReaderCapabilities.isWriteUMISupported());
        logger.debug("RadioPowerControlSupport: " + reader.ReaderCapabilities.isRadioPowerControlSupported());
        logger.debug("HoppingEnabled: " + reader.ReaderCapabilities.isHoppingEnabled());
        logger.debug("StateAwareSingulationCapable: "
                + reader.ReaderCapabilities.isTagInventoryStateAwareSingulationSupported());
        logger.debug("UTCClockCapable: " + reader.ReaderCapabilities.isUTCClockSupported());
        logger.debug(
                "NumOperationsInAccessSequence: " + reader.ReaderCapabilities.getMaxNumOperationsInAccessSequence());
        logger.debug("NumPreFilters: " + reader.ReaderCapabilities.getMaxNumPreFilters());
        logger.debug("NumAntennaSupported: " + reader.ReaderCapabilities.getNumAntennaSupported());
        logger.debug("NumGPIPorts: " + reader.ReaderCapabilities.getNumGPIPorts());
        logger.debug("NumGPIPorts: " + reader.ReaderCapabilities.getNumGPOPorts());
    }

    private void disconnect() {
        connectToReader = false;
        logger.info("Disconnecting from Zebra FX 9600");

        if (readingStatus.get()) {
            stopReading();
        }

        try {
            reader.disconnect();
            Platform.runLater(() -> {
                connectedStatus.set(false);
                readingStatus.set(false);
                statusLabel.setText("Disconnected");
            });
        } catch (Exception e) {
            logger.error("Disconnection interrupted", e);
        }
    }

    private void applySettings() {
        if (!connectedStatus.get())
            return;

        logger.info("Applying settings to Zebra FX 9600");
        logger.info("Power Level: {} dBm", powerLevelSpinner.getValue());
        logger.info("Antennas: 1={}, 2={}, 3={}, 4={}", antenna1CheckBox.isSelected(), antenna2CheckBox.isSelected(),
                antenna3CheckBox.isSelected(), antenna4CheckBox.isSelected());

        if (!epcFilterTextField.getText().isEmpty()) {
            logger.info("EPC Filter: {}", epcFilterTextField.getText());
        }

        // TODO: Implement actual LLRP configuration commands
        // This would send SET_READER_CONFIG messages with:
        // - Antenna configurations
        // - Power levels
        // - Access specs for filtering

        Platform.runLater(() -> statusLabel.setText("Settings applied"));
    }

    @Override
    public void readOnce() {
        // Not applicable for continuous RFID reading
    }

    @Override
    public void startReading() {
        if (!connectedStatus.get()) {
            logger.warn("Cannot start reading - not connected to reader");
            return;
        }

        if (reader != null && reader.isConnected()) {
            try {
                reader.Actions.Inventory.perform();
                logger.info("Starting RFID reading on Zebra FX 9600 at {}:{}", readerIP, readerPort);
                Platform.runLater(() -> statusLabel.setText("Reading..."));
            } catch (Exception e) {
                logger.error("Error reading with Zebra FX 9600", e);
                throw new RuntimeException("Failed to start reading", e);
            }
        }
    }

    // Read Notify Event handler
    class ReadNotifyHandler implements RfidEventsListener {

        // Read Event Notification
        public void eventReadNotify(RfidReadEvents e) {
            try {
                TagDataArray tags = reader.Actions.getReadTagsEx(50);
                if (tags.getTags() != null) {
                    for (TagData tag : tags.getTags()) {
                        if (tag.getTagID() == null) {
                            continue;
                        }
                        processTagRead(tag);
                    }
                }
            } catch (RuntimeException exception) {
                logger.error("Error reading tags", exception);
            }
        }

        // Status Event Notification
        public void eventStatusNotify(RfidStatusEvents e) {
            System.out.println("Status Notification " + e.StatusEventData.getStatusEventType());
        }
    }

    private void processTagRead(TagData tag) {
        Long lastRead = lastReads.get(tag.getTagID());
        long currentTime = System.currentTimeMillis();
        if (lastRead != null) {
            long timeDiff = currentTime - lastRead;
            if (timeDiff < timingWindow) {
                // Skip this tag read as it was seen recently
                logger.debug("Tag read skip: Chip={}, TimeDiff={}, TimingWindow={}, Timestamp={}", tag.getTagID(),
                        timeDiff, timingWindow, currentTime);
                return;
            }
        }
        lastReads.put(tag.getTagID(), currentTime);
        logger.debug("Tag read: Chip={}, Timestamp={}", tag.getTagID(), currentTime);

        // Calculate time relative to event start
        LocalDateTime eventStart = LocalDateTime.of(Event.getInstance().getLocalEventDate(), LocalTime.MIN);
        LocalDateTime readTime = LocalDateTime.now();
        Duration timeSinceStart = Duration.between(eventStart, readTime);

        if (timeSinceStart.isNegative()) {
            logger.warn("Tag read before event start, ignoring");
            return;
        }

        // Create raw time data
        RawTimeData rawTime = new RawTimeData();
        rawTime.setChip(tag.getTagID());
        rawTime.setTimestampLong(timeSinceStart.toNanos());

        String status = "Read Chip: " + tag.getTagID() + " at " + DurationFormatter.durationToString(timeSinceStart, 3);

        Platform.runLater(() -> lastReadLabel.setText(status));

        // Send to timing system
        timingListener.processRead(rawTime);
    }

    @Override
    public void stopReading() {
        logger.info("Stopping RFID reading");
        if (reader != null && reader.isConnected()) {
            try {
                reader.Actions.Inventory.stop();
                Platform.runLater(() -> statusLabel.setText("Connected - Stopped"));
            } catch (Exception e) {
                logger.error("Error stopping reading with Zebra FX 9600", e);
                throw new RuntimeException("Failed to stop reading", e);
            }
        }
    }

}
