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

package password.pwm.util;

import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.*;
import password.pwm.bean.SessionStateBean;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.stats.Statistic;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IntruderManager watches for login errors by users and from IP addresses.  When to many bad attempts
 * occur, IntruderManager denies further login attempts.
 * <p/>
 * An singleton instance of IntruderManager is held by the ContextManager singlenton.
 * <p/>
 * IntruderManager itself does not restrict/test logins, it relies on servlets or servlet filters
 * to call it appropriately.
 *
 * @author Jason D. Rivard
 */
public class IntruderManager implements Serializable {
// ------------------------------ FIELDS ------------------------------

    public static final int INTRUDER_RETENTION_TIME = 60 * 60 * 1000; //1 hr

    private static final PwmLogger LOGGER = PwmLogger.getLogger(IntruderManager.class);
    private final Map<String, IntruderRecord> addressLockTable = new ConcurrentHashMap<String, IntruderRecord>();
    private final Map<String, IntruderRecord> userLockTable = new ConcurrentHashMap<String, IntruderRecord>();

    private final ContextManager theManager;

// -------------------------- STATIC METHODS --------------------------

    private static void cleanup(final Map<String, IntruderRecord> table) {
        final int cleanTime = INTRUDER_RETENTION_TIME;
        final Map<String, IntruderRecord> copiedMap = new HashMap<String, IntruderRecord>(table);
        for (final String key : copiedMap.keySet()) {
            final IntruderRecord record = copiedMap.get(key);

            if (record != null && record.timeRemaining() < 0) {
                if (Math.abs(record.timeRemaining()) > cleanTime) {
                    table.remove(key);
                }
            }
        }
    }

// --------------------------- CONSTRUCTORS ---------------------------

    public IntruderManager(final ContextManager contextManager) {
        this.theManager = contextManager;
    }

// -------------------------- OTHER METHODS --------------------------

    /**
     * Mark an IP address as having a bad attempt.
     *
     * @param pwmSession Session state
     */
    public void addBadAddressAttempt(final PwmSession pwmSession) {
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        ssBean.incrementIncorrectLogins();

        final Configuration config = pwmSession.getConfig();
        final String addressString = ssBean.getSrcAddress();
        final long resetTime = config.readSettingAsLong(PwmSetting.INTRUDER_ADDRESS_RESET_TIME) * 1000;

        if (resetTime <= 0) {
            return;
        }

        markBadAttempt(addressLockTable, addressString, resetTime, (int) config.readSettingAsLong(PwmSetting.INTRUDER_ADDRESS_MAX_ATTEMPTS));
        final IntruderRecord record = addressLockTable.get(addressString);

        try {
            this.checkAddress(pwmSession);
        } catch (PwmUnrecoverableException e) {
            lockAddress(pwmSession, record, addressString);
        }

        {
            final StringBuilder sb = new StringBuilder();
            sb.append("incrementing count");
            sb.append(" address=").append(addressString);
            sb.append(", attemptCount=").append(record.getAttemptCount());
            if (record.isLocked()) {
                sb.append(", locked=").append(record.isLocked());
                sb.append(", alerted=").append(record.isAlerted());
                sb.append(", timeRemaining=").append(TimeDuration.asCompactString(record.timeRemaining()));
            }
            LOGGER.debug(pwmSession, sb.toString());
        }
    }

    private static void markBadAttempt(final Map<String, IntruderRecord> table, final String key, final long maxTime, final int maxAttempts) {
        IntruderRecord record = table.get(key);
        if (record == null) {
            record = new IntruderRecord(maxTime, maxAttempts);
        }
        record.incrementAttemptCount();
        table.put(key, record);
    }

    /**
     * Checks to see if an ip address has been locked out.  If the ip address is locked out, a PWMException is thrown, and
     * the ssBean's error status is set appropriately
     *
     * @param pwmSession the session bean of the logged in user
     * @throws password.pwm.error.PwmUnrecoverableException
     *          if the user is locked out
     */
    public void checkAddress(final PwmSession pwmSession)
            throws PwmUnrecoverableException {
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        final String addressString = ssBean.getSrcAddress();
        final Configuration config = pwmSession.getConfig();


        final int sessionMaxAttempts = (int) config.readSettingAsLong(PwmSetting.INTRUDER_SESSION_MAX_ATTEMPTS);
        if (sessionMaxAttempts > 0 && ssBean.getIncorrectLogins() > sessionMaxAttempts) {
            LOGGER.warn(pwmSession, "session intruder limit exceeded for " + addressString);
            final ErrorInformation error = new ErrorInformation(PwmError.ERROR_INTRUDER_SESSION);
            ssBean.setSessionError(error);
            throw new PwmUnrecoverableException(error);
        }


        final IntruderRecord record = addressLockTable.get(addressString);
        if (record != null && record.isLocked()) {
            LOGGER.warn(pwmSession, "address intruder limit exceeded for " + addressString + " " + TimeDuration.asCompactString(record.timeRemaining()) + " remaining in lockout");
            final ErrorInformation error = new ErrorInformation(PwmError.ERROR_INTRUDER_ADDRESS);
            ssBean.setSessionError(error);
            throw new PwmUnrecoverableException(error);
        }
    }

    public void addBadUserAttempt(final String username, final PwmSession pwmSession) {
        final Configuration config = pwmSession.getConfig();
        final long resetTime = config.readSettingAsLong(PwmSetting.INTRUDER_USER_RESET_TIME) * 1000;

        if (resetTime <= 0) {
            return;
        }

        markBadAttempt(userLockTable, username, resetTime, (int) config.readSettingAsLong(PwmSetting.INTRUDER_USER_MAX_ATTEMPTS));
        final IntruderRecord record = userLockTable.get(username);

        try {
            this.checkUser(username, pwmSession);
        } catch (PwmUnrecoverableException e) {
            lockUser(pwmSession, record, username);
        }

        {
            final StringBuilder sb = new StringBuilder();
            sb.append("incrementing count");
            sb.append(" user=").append(username);
            sb.append(", attemptCount=").append(record.getAttemptCount());
            if (record.isLocked()) {
                sb.append(", locked=").append(record.isLocked());
                sb.append(", alerted=").append(record.isAlerted());
                sb.append(", timeRemaining=").append(TimeDuration.asCompactString(record.timeRemaining()));
            }
            LOGGER.debug(pwmSession, sb.toString());
        }
    }

    private void lockUser(final PwmSession pwmSession, final IntruderRecord record, final String username) {
        if (record.isAlerted()) {
            return;
        }
        record.setAlerted(true);

        final Map<String, String> values = new LinkedHashMap<String, String>();
        values.put("type", "user");
        values.put("username", username);
        values.put("attempts", String.valueOf(record.getAttemptCount()));
        values.put("duration", TimeDuration.asCompactString(record.timeRemaining()));
        AlertHandler.alertIntruder(theManager, values);

        theManager.getStatisticsManager().incrementValue(Statistic.LOCKED_USERS);

        try {
            final String userDN = UserStatusHelper.convertUsernameFieldtoDN(username, pwmSession, null);
            final ChaiUser user = ChaiFactory.createChaiUser(userDN, pwmSession.getContextManager().getProxyChaiProvider());
            UserHistory.updateUserHistory(pwmSession, user, UserHistory.Record.Event.INTRUDER_LOCK, "");
            LOGGER.debug(pwmSession, "updated user history for " + userDN + " with intruder lock event");
        } catch (ChaiUnavailableException e) {
            LOGGER.debug(pwmSession, "error updating user history for " + username + " " + e.getMessage());

        } catch (PwmException e) {
            LOGGER.debug(pwmSession, "error updating user history for " + username + " " + e.getMessage());
        }
    }

    private void lockAddress(final PwmSession pwmSession, final IntruderRecord record, final String addressString) {
        if (record.isAlerted()) {
            return;
        }
        record.setAlerted(true);

        final Map<String, String> values = new LinkedHashMap<String, String>();
        values.put("type", "address");
        values.put("address", addressString);
        values.put("attempts", String.valueOf(record.getAttemptCount()));
        values.put("duration", TimeDuration.asCompactString(record.timeRemaining()));
        AlertHandler.alertIntruder(theManager, values);

        theManager.getStatisticsManager().incrementValue(Statistic.LOCKED_ADDRESSES);
    }

    /**
     * Checks to see if a userDN has been locked out.  If the userDN is locked out, a PWMException is thrown, and
     * the ssBean's error status is set appropriately
     *
     * @param username   the userDN to test
     * @param pwmSession the session bean of the logged in user
     * @throws password.pwm.error.PwmUnrecoverableException
     *          if the user is locked out
     */
    public void checkUser(final String username, final PwmSession pwmSession)
            throws PwmUnrecoverableException {
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();

        final IntruderRecord record = userLockTable.get(username);
        if (record != null && record.isLocked()) {
            LOGGER.info(pwmSession, "user intruder limit exceeded for " + username + " " + TimeDuration.asCompactString(record.timeRemaining()) + " remaining in lockout");
            final ErrorInformation error = new ErrorInformation(PwmError.ERROR_INTRUDER_USER);
            ssBean.setSessionError(error);
            throw new PwmUnrecoverableException(error);
        }
    }

    public void addGoodAddressAttempt(final PwmSession pwmSession) {
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        ssBean.resetIncorrectLogins();
        doGoodAttempt(addressLockTable, ssBean.getSrcAddress());
        LOGGER.debug(pwmSession, "address intruder count reset for " + ssBean.getSrcAddress());
    }

    private static void doGoodAttempt(final Map table, final String key) {
        final IntruderRecord record = (IntruderRecord) table.get(key);
        if (record != null) {
            record.clearAttemptCount();
        }
    }

    public void addGoodUserAttempt(final String username, final PwmSession pwmSession) {
        doGoodAttempt(userLockTable, username);
        LOGGER.debug(pwmSession, "user intruder count reset for " + username);
    }

    public int currentLockedAddresses() {
        return lockCount(getAddressLockTable());
    }

    public static int lockCount(final Map<String, IntruderRecord> map) {
        int counter = 0;
        for (final String key : map.keySet()) {
            if (map.get(key).isLocked()) {
                counter++;
            }
        }
        return counter;
    }

    public Map<String, IntruderRecord> getAddressLockTable() {
        return Collections.unmodifiableMap(new HashMap<String, IntruderRecord>(addressLockTable));
    }

    public int currentLockedUsers() {
        return lockCount(getUserLockTable());
    }

    public Map<String, IntruderRecord> getUserLockTable() {
        return Collections.unmodifiableMap(new HashMap<String, IntruderRecord>(userLockTable));
    }

// -------------------------- INNER CLASSES --------------------------

    static public class IntruderRecord implements Serializable {
        private long timeStamp;
        private int attemptCount;
        private final long maxTimeout;
        private final int maxAttempts;
        private boolean alerted;

        public IntruderRecord(final long maxTimeout, final int maxAttempts) {
            this.timeStamp = System.currentTimeMillis();
            this.attemptCount = 0;
            this.alerted = false;
            this.maxTimeout = maxTimeout;
            this.maxAttempts = maxAttempts;
        }

        public long getTimeStamp() {
            return timeStamp;
        }

        public int getAttemptCount() {
            return attemptCount;
        }

        public void incrementAttemptCount() {
            if (timeRemaining() < 0) {
                attemptCount = 0;
                alerted = false;
            }
            this.timeStamp = System.currentTimeMillis();
            attemptCount++;
        }

        public void clearAttemptCount() {
            attemptCount = 0;
        }

        public long timeRemaining() {
            return (timeStamp + maxTimeout) - System.currentTimeMillis();
        }

        public boolean isLocked() {
            if (timeRemaining() > 0) {
                if (attemptCount >= maxAttempts) {
                    return true;
                }
            }
            return false;
        }

        public long getMaxTimeout() {
            return maxTimeout;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public boolean isAlerted() {
            return alerted;
        }

        public void setAlerted(final boolean alerted) {
            this.alerted = alerted;
        }
    }

    public static class CleanerTask extends TimerTask {
        private final IntruderManager intruderManager;

        public CleanerTask(final IntruderManager intruderManager) {
            this.intruderManager = intruderManager;
        }

        public void run() {
            cleanup(intruderManager.addressLockTable);
            cleanup(intruderManager.userLockTable);
        }
    }
}

