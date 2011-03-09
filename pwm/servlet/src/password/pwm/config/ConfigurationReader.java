/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2010 The PWM Project
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package password.pwm.config;

import password.pwm.util.Helper;
import password.pwm.util.PwmLogger;

import java.io.*;
import java.math.BigInteger;
import java.util.Date;

/**
 * Read the PWM configuration.
 *
 * @author Jason D. Rivard
 */
public class ConfigurationReader {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(ConfigurationReader.class.getName());
    private static final int MAX_FILE_CHARS = 100 * 1024;

    private final File configFile;
    private final String configFileChecksum;
    private final StoredConfiguration storedConfiguration;
    private final Configuration configuration;

    private Date configurationReadTime;

    private final MODE configMode;

    public enum MODE {
        NEW,
        CONFIGURATION,
        RUNNING
    }

    public ConfigurationReader(final File configFile) {
        this.configFile = configFile;
        this.configFileChecksum = readFileChecksum(configFile);
        this.storedConfiguration = readStoredConfig();
        this.configMode = determineConfigMode(storedConfiguration);
        LOGGER.debug("configuration mode: " + configMode);

        if (modifiedSincePWMSave()) {
            LOGGER.warn("configuration settings have been modified since the file was saved by pwm");
        }

        configuration = configMode == MODE.NEW ? null : new Configuration(this.storedConfiguration);
    }

    public MODE getConfigMode() {
        return configMode;
    }

    private static MODE determineConfigMode(final StoredConfiguration storedConfig) {
        if (storedConfig == null) {
            return MODE.NEW;
        }

        final String configIsEditable = storedConfig.readProperty(StoredConfiguration.PROPERTY_KEY_CONFIG_IS_EDITABLE);
        if (configIsEditable != null && configIsEditable.equalsIgnoreCase("true")) {
            return MODE.CONFIGURATION;
        }

        return MODE.RUNNING;
    }

    public StoredConfiguration getStoredConfiguration() {
        return storedConfiguration;
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    private StoredConfiguration readStoredConfig() {
        LOGGER.debug("loading configuration file: " + configFile);

        final String theFileData;
        try {
            theFileData = Helper.readFileAsString(configFile, MAX_FILE_CHARS);
        } catch (Exception e) {
            LOGGER.warn("unable to read configuration file: " + e.getMessage());
            return null;
        }

        final StoredConfiguration storedConfiguration;
        try {
            storedConfiguration = StoredConfiguration.fromXml(theFileData);
        } catch (Exception e) {
            LOGGER.warn("unable to parse configuration file: " + e.getMessage());
            return null;
        }

        for (final String errorString : storedConfiguration.validateValues()) {
            LOGGER.error("error in config file, please investigate: " + errorString);
        }

        configurationReadTime = new Date();
        storedConfiguration.lock();
        return storedConfiguration;
    }

    public void saveConfiguration(final StoredConfiguration storedConfiguration)
            throws IOException {
        if (getConfigMode() == MODE.RUNNING) {
            throw new IllegalStateException("running config mode does now allow saving of configuration");
        }

        { // increment the config epoch
            String epochStrValue = storedConfiguration.readProperty(StoredConfiguration.PROPERTY_KEY_CONFIG_EPOCH);
            try {
                final BigInteger epochValue = epochStrValue == null || epochStrValue.length() < 0 ? BigInteger.ZERO : new BigInteger(epochStrValue);
                epochStrValue = epochValue.add(BigInteger.ONE).toString();
            } catch (Exception e) {
                LOGGER.error("error trying to parse previous config epoch property: " + e.getMessage());
                epochStrValue = "0";
            }
            storedConfiguration.writeProperty(StoredConfiguration.PROPERTY_KEY_CONFIG_EPOCH, epochStrValue);
        }

        final String xmlBlob = storedConfiguration.toXml();
        final FileWriter fileWriter = new FileWriter(configFile, false);
        fileWriter.write(xmlBlob);
        fileWriter.close();
        LOGGER.info("saved configuration " + storedConfiguration.toString());
    }

    public boolean modifiedSinceLoad() {
        final String currentChecksum = readFileChecksum(configFile);
        return !currentChecksum.equals(configFileChecksum);
    }

    public boolean modifiedSincePWMSave() {
        if (this.getConfigMode() == MODE.NEW) {
            return false;
        }

        try {
            final String storedChecksum = storedConfiguration.readProperty(StoredConfiguration.PROPERTY_KEY_SETTING_CHECKSUM);
            final String actualChecksum = storedConfiguration.settingChecksum();
            return !actualChecksum.equals(storedChecksum);
        } catch (Exception e) {
            LOGGER.warn("unable to evaluate checksum file: " + e.getMessage());
        }
        return true;
    }

    private static String readFileChecksum(final File file) {
        if (!file.exists()) {
            return "";
        }

        return String.valueOf(file.lastModified());
    }

    public Date getConfigurationReadTime() {
        return configurationReadTime;
    }

    public int getConfigurationEpoch() {
        try {
            return Integer.parseInt(storedConfiguration.readProperty(StoredConfiguration.PROPERTY_KEY_CONFIG_EPOCH));
        } catch (Exception e) {
            return 0;
        }
    }
}


