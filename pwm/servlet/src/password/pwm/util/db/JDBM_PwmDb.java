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

package password.pwm.util.db;

import jdbm.PrimaryTreeMap;
import jdbm.RecordManager;
import jdbm.RecordManagerFactory;
import password.pwm.util.Helper;
import password.pwm.util.PwmLogger;
import password.pwm.util.TimeDuration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static password.pwm.util.db.PwmDB.DB;

/**
 * @author Jason D. Rivard
 */
public class JDBM_PwmDb implements PwmDBProvider {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(JDBM_PwmDb.class, true);
    private static final String FILE_NAME = "jdbm";

    private RecordManager recman;
    private final Map<String, Map<String, String>> treeMap = new HashMap<String, Map<String, String>>();
    private File dbDirectory;

    // cache of dbIterators
    private final Map<DB, DbIterator<String>> dbIterators = new ConcurrentHashMap<DB, DbIterator<String>>();

    // operation lock
    private final ReadWriteLock LOCK = new ReentrantReadWriteLock();

    private PwmDB.Status status = PwmDB.Status.NEW;

// --------------------------- CONSTRUCTORS ---------------------------

    JDBM_PwmDb() {
    }

// ------------------------ INTERFACE METHODS ------------------------


// --------------------- Interface PwmDB ---------------------

    public void close()
            throws PwmDBException {
        status = PwmDB.Status.CLOSED;

        if (recman == null) {
            return;
        }

        try {
            LOCK.writeLock().lock();
            final long startTime = System.currentTimeMillis();
            LOGGER.debug("closing pwmDB");
            recman.commit();
            recman.close();
            recman = null;
            LOGGER.info("pwmDB closed in " + TimeDuration.fromCurrent(startTime).asCompactString());
        } catch (Exception e) {
            LOGGER.error("error while closing pwmDB: " + e.getMessage(), e);
            throw new PwmDBException(e);
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    public PwmDB.Status getStatus() {
        return status;
    }

    public boolean contains(final PwmDB.DB db, final String key)
            throws PwmDBException {
        try {
            LOCK.readLock().lock();
            return get(db, key) != null;
        } finally {
            LOCK.readLock().unlock();
        }
    }

    public String get(final PwmDB.DB db, final String key)
            throws PwmDBException {
        try {
            LOCK.readLock().lock();
            final Map<String, String> tree = getHTree(db);
            final Object value = tree.get(key);
            return value == null ? null : value.toString();
        } catch (IOException e) {
            throw new PwmDBException(e);
        } finally {
            LOCK.readLock().unlock();
        }
    }

    public void init(final File dbDirectory, final Map<String, String> initParameters, final boolean readOnly)
            throws PwmDBException {
        if (readOnly) {
            throw new UnsupportedOperationException("readOnly not supported");
        }

        if (status != PwmDB.Status.NEW) {
            throw new IllegalStateException("already initialized");
        }

        final long startTime = System.currentTimeMillis();
        try {
            LOCK.writeLock().lock();
            this.dbDirectory = dbDirectory;
            final String dbFileName = dbDirectory.getAbsolutePath() + File.separator + FILE_NAME;
            recman = RecordManagerFactory.createRecordManager(dbFileName, new Properties());

            LOGGER.info("pwmDB opened in " + TimeDuration.fromCurrent(startTime).asCompactString());
            status = PwmDB.Status.OPEN;
        } catch (Exception e) {
            LOGGER.error("error while opening pwmDB: " + e.getMessage(), e);
            throw new PwmDBException(e);
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    public Iterator<String> iterator(final DB db)
            throws PwmDBException {
        try {
            if (dbIterators.containsKey(db)) {
                throw new IllegalArgumentException("multiple iterators per DB are not permitted");
            }

            final DbIterator<String> iterator = new DbIterator<String>(db);
            dbIterators.put(db, iterator);
            return iterator;
        } catch (Exception e) {
            throw new PwmDBException(e);
        }
    }

    public void putAll(final DB db, final Map<String, String> keyValueMap)
            throws PwmDBException {
        try {
            LOCK.writeLock().lock();
            final Map<String, String> tree = getHTree(db);
            tree.putAll(keyValueMap);
            recman.commit();
        } catch (IOException e) {
            try {
                recman.rollback();
            } catch (IOException e2) {
                throw new PwmDBException(e2);
            }
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    public boolean put(final PwmDB.DB db, final String key, final String value)
            throws PwmDBException {
        final boolean preExists;
        try {
            LOCK.writeLock().lock();
            preExists = remove(db, key);
            final Map<String, String> tree = getHTree(db);
            tree.put(key, value);
            recman.commit();
        } catch (IOException e) {
            throw new PwmDBException(e);
        } finally {
            LOCK.writeLock().unlock();
        }

        return preExists;
    }


    public boolean remove(final PwmDB.DB db, final String key)
            throws PwmDBException {
        try {
            LOCK.writeLock().lock();
            final Map<String, String> tree = getHTree(db);
            final String removedValue = tree.remove(key);
            recman.commit();
            return removedValue != null;
        } catch (IOException e) {
            throw new PwmDBException(e);
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    public void returnIterator(final DB db) throws PwmDBException {

    }

    public int size(final PwmDB.DB db)
            throws PwmDBException {
        try {
            LOCK.readLock().lock();
            return getHTree(db).size();
        } catch (IOException e) {
            throw new PwmDBException(e);
        } finally {
            LOCK.readLock().unlock();
        }
    }

    public void truncate(final PwmDB.DB db)
            throws PwmDBException {
        final int startSize = size(db);

        LOGGER.info("beginning truncate of " + startSize + " records in " + db.toString() + " database, this may take a while...");

        final long startTime = System.currentTimeMillis();

        try {
            LOCK.writeLock().lock();
            final Map<String, String> tree = getHTree(db);
            tree.keySet().clear();
            recman.commit();
        } catch (IOException e) {
            throw new PwmDBException(e);
        } finally {
            LOCK.writeLock().unlock();
        }

        LOGGER.debug("truncate complete of " + db.toString() + ", " + startSize + " records in " + new TimeDuration(System.currentTimeMillis(), startTime).asCompactString() + ", " + size(db) + " records in database");
    }

    public long diskSpaceUsed() {
        try {
            return Helper.getFileDirectorySize(dbDirectory);
        } catch (Exception e) {
            LOGGER.error("error trying to compute db directory size: " + e.getMessage());
        }
        return 0;
    }

    public void removeAll(final DB db, final Collection<String> keys)
            throws PwmDBException {
        try {
            LOCK.writeLock().lock();
            final Map<String, String> tree = getHTree(db);
            tree.keySet().removeAll(keys);
            recman.commit();
        } catch (IOException e) {
            throw new PwmDBException(e);
        } finally {
            LOCK.writeLock().unlock();
        }
    }

// -------------------------- OTHER METHODS --------------------------

    private Map<String, String> getHTree(final DB keyName)
            throws IOException {
        Map<String, String> tree = treeMap.get(keyName.toString());
        if (tree == null) {
            tree = openHTree(keyName.toString(), recman);
            treeMap.put(keyName.toString(), tree);
        }
        return tree;
    }

    private static Map<String, String> openHTree(
            final String name,
            final RecordManager recman
    )
            throws IOException {
        final PrimaryTreeMap storeMap = recman.treeMap(name);
        return storeMap;
    }

// -------------------------- INNER CLASSES --------------------------

    private class DbIterator<K> implements Iterator<String> {
        private final PwmDB.DB db;
        private Iterator<String> theIterator;

        private DbIterator(final DB db) throws IOException, PwmDBException {
            this.db = db;
            this.theIterator = getHTree(db).keySet().iterator();
        }

        public boolean hasNext() {
            return theIterator.hasNext();
        }

        public void close() {
            theIterator = null;
            dbIterators.remove(db);
        }

        public String next() {
            return theIterator.next();
        }

        public void remove() {
            theIterator.remove();
        }

        protected void finalize() throws Throwable {
            super.finalize();
            close();
        }
    }

}
