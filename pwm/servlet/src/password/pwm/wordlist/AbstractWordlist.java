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

package password.pwm.wordlist;

import password.pwm.PwmService;
import password.pwm.PwmSession;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthRecord;
import password.pwm.health.HealthStatus;
import password.pwm.util.Helper;
import password.pwm.util.PwmLogger;
import password.pwm.util.Sleeper;
import password.pwm.util.TimeDuration;
import password.pwm.util.db.PwmDB;
import password.pwm.util.db.PwmDBException;
import password.pwm.util.stats.Statistic;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

abstract class AbstractWordlist implements Wordlist, PwmService {
    protected PwmDB.DB META_DB = null;
    protected PwmDB.DB WORD_DB = null;

    protected WordlistConfiguration wordlistConfiguration;

    protected volatile STATUS wlStatus = STATUS.NEW;
    protected PwmDB pwmDB;
    protected Populator populator;

    protected PwmLogger LOGGER = PwmLogger.getLogger(AbstractWordlist.class);
    protected String DEBUG_LABEL = "Generic Wordlist";

    protected int storedSize = 0;
    private ErrorInformation lastError;



// --------------------------- CONSTRUCTORS ---------------------------

    protected AbstractWordlist(
            final WordlistConfiguration wordlistConfiguration,
            final PwmDB pwmDB
    ) {
        this.wordlistConfiguration = wordlistConfiguration;
        this.pwmDB = pwmDB;
    }

    protected void init() {
        final long startTime = System.currentTimeMillis();
        wlStatus = STATUS.OPENING;

        if (pwmDB == null) {
            final String errorMsg = "pwmDB is not available, " + DEBUG_LABEL + " will remain closed";
            LOGGER.warn(errorMsg);
            lastError = new ErrorInformation(PwmError.ERROR_SERVICE_NOT_AVAILABLE,errorMsg);
            close();
            return;
        }

        if (wordlistConfiguration.getWordlistFile() == null) {
            LOGGER.warn("wordlist file is not specified, " + DEBUG_LABEL + " will remain closed");
            try {
                resetDB("-1");
            } catch (Exception e) {
                final String errorMsg = "error while clearing " + DEBUG_LABEL + " DB: " + e.getMessage();
                LOGGER.warn(errorMsg);
                lastError = new ErrorInformation(PwmError.ERROR_SERVICE_NOT_AVAILABLE,errorMsg);
            }
            close();
            return;
        }

        if (!wordlistConfiguration.getWordlistFile().exists()) {
            final String errorMsg = "wordlist file \"" + wordlistConfiguration.getWordlistFile().getAbsolutePath() + "\" does not exist, " + DEBUG_LABEL + "; will remain closed";
            LOGGER.warn(errorMsg);
            lastError = new ErrorInformation(PwmError.ERROR_SERVICE_NOT_AVAILABLE,errorMsg);
            close();
            return;
        }

        try {
            checkPopulation();
        } catch (Exception e) {
            final String errorMsg = "unexpected error while examining wordlist db: " + e.getMessage();
            if ((e instanceof PwmUnrecoverableException) || (e instanceof NullPointerException) || (e instanceof PwmDBException)) {
                LOGGER.warn(errorMsg);
            } else {
                LOGGER.warn(errorMsg,e);
            }
            lastError = new ErrorInformation(PwmError.ERROR_SERVICE_NOT_AVAILABLE,errorMsg);
            populator = null;
            close();
            return;
        }

        //read stored size
        try {
            final String storedSizeStr = pwmDB.get(META_DB, KEY_SIZE);
            storedSize = Integer.valueOf(storedSizeStr);
        } catch (PwmDBException e) {
            final String errorMsg = DEBUG_LABEL + " error reading stored size, closing, " + e.getMessage();
            LOGGER.warn(errorMsg);
            lastError = new ErrorInformation(PwmError.ERROR_SERVICE_NOT_AVAILABLE,errorMsg);
            close();
            return;
        }

        if (wlStatus == STATUS.OPENING) {
            wlStatus = STATUS.OPEN;
            final int wordlistSize = size();
            final TimeDuration totalTime = TimeDuration.fromCurrent(startTime);
            LOGGER.debug(DEBUG_LABEL + " open with " + wordlistSize + " words in " + totalTime.asCompactString());
        } else {
            final String errorMsg = DEBUG_LABEL + " status changed unexpectedly during startup, closing";
            LOGGER.warn(errorMsg);
            lastError = new ErrorInformation(PwmError.ERROR_SERVICE_NOT_AVAILABLE,errorMsg);
            close();
        }
    }

    private String normalizeWord(final String input) {
        if (input == null) {
            return null;
        }

        String word = input.trim();

        if (!wordlistConfiguration.isCaseSensitive()) {
            word = word.toLowerCase();
        }

        return word.length() > 0 ? word : null;
    }

    protected String makeChecksumString(final File wordlistFile)
            throws IOException {
        final StringBuilder checksumString = new StringBuilder();
        checksumString.append("checksum=").append(Helper.md5sum(wordlistFile));
        checksumString.append(",length=").append(wordlistFile.length());
        checksumString.append(",caseSensitive=").append(wordlistConfiguration.isCaseSensitive());
        return checksumString.toString();
    }

    protected void checkPopulation()
            throws Exception {
        LOGGER.trace("calculating checksum of " + wordlistConfiguration.getWordlistFile().getAbsolutePath());
        final String checksumString = makeChecksumString(wordlistConfiguration.getWordlistFile());
        LOGGER.trace("checksum of " + wordlistConfiguration.getWordlistFile().getAbsolutePath() + " complete, result: " + checksumString);

        final boolean clearRequired = !checkDbStatus() || !checkDbVersion() || !checkChecksum(checksumString);
        final boolean isComplete = !clearRequired && VALUE_STATUS.COMPLETE.equals(VALUE_STATUS.forString(pwmDB.get(META_DB, KEY_STATUS)));

        if (!clearRequired && isComplete) {
            return;
        }

        LOGGER.debug(DEBUG_LABEL + " previous population incomplete, resuming");

        if (clearRequired) {
            resetDB(checksumString);
        }

        if (wlStatus != STATUS.OPENING) {
            LOGGER.warn(DEBUG_LABEL + " changed unexpectedly during startup, closing");
            close();
            return;
        }

        populator = new Populator(
                new ZipReader(wordlistConfiguration.getWordlistFile()),
                new Sleeper(wordlistConfiguration.getLoadFactor()),
                this
        );
        populator.populate();
        populator = null;
    }

    private boolean checkChecksum(final String checksum)
            throws Exception {
        LOGGER.trace("checking wordlist file checksum stored in pwmDB");

        final Object checksumInDb = pwmDB.get(META_DB, KEY_CHECKSUM);
        final boolean result = checksum.equals(checksumInDb);

        if (!result) {
            LOGGER.info("existing ZIP checksum does not match current wordlist file, db=(" + checksumInDb + "), file=(" + checksum + "), clearing db");
        } else {
            LOGGER.trace("existing ZIP checksum matches current wordlist file, db=(" + checksumInDb + "), file=(" + checksum + ")");
        }

        return result;
    }

    private boolean checkDbStatus()
            throws Exception {
        LOGGER.trace("checking " + DEBUG_LABEL + " db status");

        final VALUE_STATUS statusInDb = VALUE_STATUS.forString(pwmDB.get(META_DB, KEY_STATUS));

        if (statusInDb == null || statusInDb.equals(VALUE_STATUS.DIRTY)) {
            LOGGER.info("existing db was not shut down cleanly during population, clearing db");
            return false;
        } else {
            LOGGER.trace("existing db is clean");
            return true;
        }
    }

    private boolean checkDbVersion()
            throws Exception {
        LOGGER.trace("checking version number stored in pwmDB");

        final Object versionInDB = pwmDB.get(META_DB, KEY_VERSION);
        final boolean result = VALUE_VERSION.equals(versionInDB);

        if (!result) {
            LOGGER.info("existing db version does not match current db version db=(" + versionInDB + ")  pwm=(" + VALUE_VERSION + "), clearing db");
        } else {
            LOGGER.trace("existing db version matches current db version db=(" + versionInDB + ")  pwm=(" + VALUE_VERSION + ")");
        }

        return result;
    }

    private void resetDB(final String checksum)
            throws Exception {
        pwmDB.put(META_DB, KEY_VERSION, VALUE_VERSION + "_ClearInProgress");

        for (final PwmDB.DB db : new PwmDB.DB[]{META_DB, WORD_DB}) {
            LOGGER.debug("clearing " + db);
            pwmDB.truncate(db);
        }

        pwmDB.put(META_DB, KEY_VERSION, VALUE_VERSION);
        pwmDB.put(META_DB, KEY_CHECKSUM, checksum);
    }

    public boolean containsWord(final PwmSession pwmSession, final String word) {
        if (wlStatus != STATUS.OPEN) {
            return false;
        }

        final String testWord = normalizeWord(word);

        if (testWord == null) {
            return false;
        }

        try {
            final long startTime = System.currentTimeMillis();
            final boolean result = pwmDB.contains(WORD_DB, testWord);
            final long totalTime = (System.currentTimeMillis() - startTime);

            if (pwmSession != null) {
                LOGGER.trace(pwmSession, "successfully checked word, result=" + result + ", duration=" + TimeDuration.asCompactString(totalTime));
                pwmSession.getContextManager().getStatisticsManager().updateAverageValue(Statistic.AVG_WORDLIST_CHECK_TIME, totalTime);
            }

            return result;
        } catch (Exception e) {
            LOGGER.error("database error checking for word: " + e.getMessage());
        }

        return false;
    }

    public int size() {
        if (populator != null) {
            return 0;
        }

        return storedSize;
    }

    public synchronized void close() {
        if (populator != null) {
            populator.pause();
            final long beginWaitTime = System.currentTimeMillis();
            if (populator != null) {
                LOGGER.info("waiting 10 seconds for populator to exit");
            }
            while (populator != null && (System.currentTimeMillis() - beginWaitTime > 10 * 1000)) {
                Helper.pause(100);
            }
            populator = null;
        }

        if (wlStatus != STATUS.CLOSED) {
            LOGGER.debug("closed");
        }

        wlStatus = STATUS.CLOSED;
        pwmDB = null;
    }

// --------------------- GETTER / SETTER METHODS ---------------------

    public File getWordlistFile() {
        return wordlistConfiguration.getWordlistFile();
    }

// -------------------------- OTHER METHODS --------------------------

    public STATUS status() {
        return wlStatus;
    }

    public String getDebugStatus() {
        if (wlStatus == STATUS.OPENING && populator != null) {
            return "populating " + populator.percentComplete();
        } else {
            return wlStatus.toString();
        }
    }

    protected abstract Map<String, String> getWriteTxnForValue(String value);

// -------------------------- ENUMERATIONS --------------------------

    static enum VALUE_STATUS {
        COMPLETE,
        DIRTY,
        IN_PROGRESS;

        static VALUE_STATUS forString(final String status) {
            for (final VALUE_STATUS s : VALUE_STATUS.values()) {
                if (s.toString().equals(status)) {
                    return s;
                }
            }

            return null;
        }
    }

    public List<HealthRecord> healthCheck() {
        if (wlStatus == STATUS.OPENING) {
            final HealthRecord healthRecord = new HealthRecord(HealthStatus.CAUTION, this.DEBUG_LABEL, this.DEBUG_LABEL + " is not yet open: " + this.getDebugStatus());
            return Collections.singletonList(healthRecord);
        }

        if (lastError != null) {
            final HealthRecord healthRecord = new HealthRecord(HealthStatus.WARN, this.DEBUG_LABEL, this.DEBUG_LABEL + " error: " + lastError.toDebugStr());
            return Collections.singletonList(healthRecord);
        }
        return null;
    }
}
