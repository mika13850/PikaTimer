package com.pikatimer.timing.reader;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mot.rfid.api3.RFIDReader;
import com.mot.rfid.api3.RfidEventsListener;
import com.mot.rfid.api3.RfidReadEvents;
import com.mot.rfid.api3.RfidStatusEvents;
import com.mot.rfid.api3.TAG_EVENT_REPORT_TRIGGER;
import com.mot.rfid.api3.TAG_MOVING_EVENT_REPORT;
import com.mot.rfid.api3.TRACE_LEVEL;
import com.mot.rfid.api3.TagData;
import com.mot.rfid.api3.TagDataArray;
import com.mot.rfid.api3.TagStorageSettings;
import com.mot.rfid.api3.TriggerInfo;

/**
 * Integration test for Zebra FX9600 RFID Reader. This test requires: 1. Native
 * libraries to be present in the lib/ directory 2. A Zebra FX9600 reader to be
 * available on the network 3. Environment variable ZEBRA_READER_IP to be set
 * (optional, defaults to 169.254.152.175)
 * 
 * The test will be skipped if native libraries are not found.
 */
public class ZebraFX9600ReaderTest {

    private static final Logger logger = LoggerFactory.getLogger(ZebraFX9600ReaderTest.class);
    private static final String DEFAULT_READER_IP = "169.254.152.175";
    private static final int DEFAULT_READER_PORT = 5084;
    private static final Map<String, Long> lastReads = new HashMap<>();

    private RFIDReader reader = null;

    public void connect() {
        String readerIP = System.getenv("ZEBRA_READER_IP");
        if (readerIP == null || readerIP.isEmpty()) {
            readerIP = DEFAULT_READER_IP;
        }
        // Intanciate the reader
        reader = new RFIDReader();
        reader.setHostName(readerIP);
        reader.setPort(DEFAULT_READER_PORT);

        // Connect to the reader
        try {
            logger.info("Connecting to Zebra FX 9600 at {}:{}", readerIP, DEFAULT_READER_PORT);
            reader.connect();
            logger.info("Successfully connected to Zebra FX 9600");
        } catch (Exception e) {
            logger.error("Failed to connect to Zebra FX 9600", e);
            throw new RuntimeException("Failed to connect to reader", e);
        }

        // Configure the reader
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

        // Add event handler
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

        logCapabilities();
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

    public void disconnect() {
        if (reader != null) {
            try {
                if (reader.isConnected()) {
                    reader.disconnect();
                    logger.info("Disconnected from Zebra FX 9600");
                }
            } catch (Exception e) {
                logger.error("Error disconnecting from Zebra FX 9600", e);
            } finally {
                reader = null;
            }
        }
    }

    public void startReading() {
        if (reader != null && reader.isConnected()) {
            try {
                reader.Actions.Inventory.perform();
                logger.info("Start reading with Zebra FX 9600");
            } catch (Exception e) {
                logger.error("Error reading with Zebra FX 9600", e);
                throw new RuntimeException("Failed to start reading", e);
            }
        }
    }

    public void stopReading() {
        if (reader != null && reader.isConnected()) {
            try {
                reader.Actions.Inventory.stop();
                logger.info("Stop reading with Zebra FX 9600");
            } catch (Exception e) {
                logger.error("Error stopping reading with Zebra FX 9600", e);
                throw new RuntimeException("Failed to stop reading", e);
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
                        Long lastRead = lastReads.get(tag.getTagID());
                        long currentTime = System.currentTimeMillis();
                        if (lastRead != null) {
                            long timeDiff = currentTime - lastRead;
                            if (timeDiff < 5000) {
                                // Skip this tag read as it was seen recently
                                continue;
                            }
                        }
                        lastReads.put(tag.getTagID(), currentTime);
                        logger.info("Tag: " + tag.getTagID() + " " + tag.getPeakRSSI() + " " + tag.getTagSeenCount());
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

    @Test
    @EnabledIfEnvironmentVariable(named = "LD_LIBRARY_PATH", matches = ".*lib", disabledReason = "Requires native Zebra libraries in LD_LIBRARY_PATH")
    void testReaderConnection() throws InterruptedException {
        logger.info("Starting Zebra FX9600 reader connection test");

        // Test connection
        assertDoesNotThrow(() -> connect(), "Should connect to reader without throwing exception");
        assertNotNull(reader, "Reader should be initialized");
        assertTrue(reader.isConnected(), "Reader should be connected");

        // Test reader capabilities
        assertNotNull(reader.ReaderCapabilities, "Reader capabilities should be available");
        assertNotNull(reader.ReaderCapabilities.getModelName(), "Model name should be available");
        logger.info("Connected to reader model: {}", reader.ReaderCapabilities.getModelName());

        // Test starting reading
        assertDoesNotThrow(() -> startReading(), "Should start reading without throwing exception");

        // Wait for some reads
        Thread.sleep(10000);

        // Test stopping reading
        assertDoesNotThrow(() -> stopReading(), "Should stop reading without throwing exception");

        // Test disconnection
        assertDoesNotThrow(() -> disconnect(), "Should disconnect without throwing exception");

        logger.info("Zebra FX9600 reader connection test completed successfully");
    }

}
