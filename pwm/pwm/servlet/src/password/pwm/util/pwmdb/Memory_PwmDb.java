/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2012 The PWM Project
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

package password.pwm.util.pwmdb;

import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.util.Helper;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static password.pwm.util.pwmdb.PwmDB.DB;


/**
 * @author Jason D. Rivard
 */
public class Memory_PwmDb implements PwmDBProvider {
// ------------------------------ FIELDS ------------------------------

    private static final long MIN_FREE_MEMORY = 1024 * 1024;  // 1mb
    private PwmDB.Status state = PwmDB.Status.NEW;
    private Map<DB, Map<String, String>> maps = new HashMap<DB, Map<String, String>>();

// -------------------------- STATIC METHODS --------------------------

    private static void checkFreeMem() throws PwmDBException {
        final long currentFreeMem = Runtime.getRuntime().freeMemory();
        if (currentFreeMem < MIN_FREE_MEMORY) {
            System.gc();
            Helper.pause(100);
            System.gc();
            if (currentFreeMem < MIN_FREE_MEMORY) {
                throw new PwmDBException(new ErrorInformation(PwmError.ERROR_PWMDB_UNAVAILABLE,"out of memory, unable to add new records"));
            }
        }
    }

    private void opertationPreCheck() throws PwmDBException {
        if (state != PwmDB.Status.OPEN) {
            throw new IllegalStateException("db is not open");
        }
        checkFreeMem();
    }

// --------------------------- CONSTRUCTORS ---------------------------

    public Memory_PwmDb() {
        for (final DB db : PwmDB.DB.values()) {
            final Map<String, String> newMap = new ConcurrentHashMap<String, String>();
            maps.put(db, newMap);
        }
    }

// ------------------------ INTERFACE METHODS ------------------------


// --------------------- Interface PwmDB ---------------------

    @PwmDB.WriteOperation
    public void close()
            throws PwmDBException {
        state = PwmDB.Status.CLOSED;
        for (final DB db : PwmDB.DB.values()) {
            maps.get(db).clear();
        }
    }

    public boolean contains(final DB db, final String key)
            throws PwmDBException {
        opertationPreCheck();
        final Map<String, String> map = maps.get(db);
        return map.containsKey(key);
    }

    public String get(final DB db, final String key)
            throws PwmDBException {
        opertationPreCheck();
        final Map<String, String> map = maps.get(db);
        return map.get(key);
    }

    @PwmDB.WriteOperation
    public void init(final File dbDirectory, final Map<String, String> initParameters, final boolean readOnly)
            throws PwmDBException {
        if (readOnly) {
            maps = Collections.unmodifiableMap(maps);
        }
        if (state == PwmDB.Status.OPEN) {
            throw new IllegalStateException("cannot init db more than one time");
        }
        if (state == PwmDB.Status.CLOSED) {
            throw new IllegalStateException("db is closed");
        }
        state = PwmDB.Status.OPEN;
    }

    public Iterator<String> iterator(final DB db) throws PwmDBException {
        return new DbIterator(db);
    }

    @PwmDB.WriteOperation
    public void putAll(final DB db, final Map<String, String> keyValueMap)
            throws PwmDBException {
        opertationPreCheck();

        if (keyValueMap != null) {
            final Map<String, String> map = maps.get(db);
            map.putAll(keyValueMap);
        }
    }

    @PwmDB.WriteOperation
    public boolean put(final DB db, final String key, final String value)
            throws PwmDBException {
        opertationPreCheck();

        final Map<String, String> map = maps.get(db);
        return null != map.put(key, value);
    }

    @PwmDB.WriteOperation
    public boolean remove(final DB db, final String key)
            throws PwmDBException {
        opertationPreCheck();

        final Map<String, String> map = maps.get(db);
        return null != map.remove(key);
    }

    public void returnIterator(final DB db) throws PwmDBException {
    }

    public int size(final DB db)
            throws PwmDBException {
        opertationPreCheck();

        final Map<String, String> map = maps.get(db);
        return map.size();
    }

    @PwmDB.WriteOperation
    public void truncate(final DB db)
            throws PwmDBException {
        opertationPreCheck();

        final Map<String, String> map = maps.get(db);
        map.clear();
    }

    public void removeAll(final DB db, final Collection<String> keys) throws PwmDBException {
        opertationPreCheck();

        maps.get(db).keySet().removeAll(keys);
    }

    public PwmDB.Status getStatus() {
        return state;
    }

    // -------------------------- ENUMERATIONS --------------------------


// -------------------------- INNER CLASSES --------------------------

    private class DbIterator<K> implements Iterator<String> {
        private final Iterator<String> iterator;

        private DbIterator(final DB db) {
            iterator = maps.get(db).keySet().iterator();
        }

        public boolean hasNext() {
            return iterator.hasNext();
        }

        public String next() {
            return iterator.next();
        }

        public void remove() {
            iterator.remove();
        }
    }

    public File getFileLocation() {
        return null;
    }
}
