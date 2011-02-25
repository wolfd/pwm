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

import password.pwm.util.PwmLogger;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A LIFO {@link Queue} implementation backed by a pwmDB instance.  {@code this} instances are internally
 * synchronized.  This class actually implements all the {@code Deque} methods, but implements {@code Queue} instead
 * to retain compatability with JDK 1.5.
 */
public class PwmDBStoredQueue implements Queue<String> //, Deque<String>
{
// ------------------------------ FIELDS ------------------------------

    private final static PwmLogger LOGGER = PwmLogger.getLogger(PwmDBStoredQueue.class, true);
    private final static int MAX_SIZE = Integer.MAX_VALUE - 3;

    private final static String KEY_HEAD_POSITION = "_HEAD_POSITION";
    private final static String KEY_TAIL_POSITION = "_TAIL_POSITION";
    private final static String KEY_VERSION = "_KEY_VERSION";
    private final static String VALUE_VERSION = "7";

    private final InternalQueue internalQueue;

    private final ReadWriteLock LOCK = new ReentrantReadWriteLock();

    private static final Map<PwmDB.DB, PwmDBStoredQueue> singletonMap = Collections.synchronizedMap(new HashMap<PwmDB.DB, PwmDBStoredQueue>());

    private static final boolean developerDebug = false;

// --------------------------- CONSTRUCTORS ---------------------------

    private PwmDBStoredQueue(final PwmDB pwmDB, final PwmDB.DB DB)
            throws PwmDBException {
        internalQueue = new InternalQueue(pwmDB, DB);
    }

    public static synchronized PwmDBStoredQueue createPwmDBStoredQueue(final PwmDB pwmDB, final PwmDB.DB DB)
            throws PwmDBException {
        PwmDBStoredQueue queue = singletonMap.get(DB);
        if (queue == null) {
            queue = new PwmDBStoredQueue(pwmDB, DB);
            singletonMap.put(DB, queue);
        }
        return queue;
    }

    public void removeLast(final int removalCount) {
        try {
            LOCK.writeLock().lock();
            internalQueue.removeLast(removalCount);
        } catch (PwmDBException e) {
            throw new IllegalStateException("unexpected pwmDB error while modifying queue: " + e.getMessage(), e);
        } finally {
            LOCK.writeLock().unlock();
        }
    }

// ------------------------ INTERFACE METHODS ------------------------


// --------------------- Interface Collection ---------------------


    public boolean isEmpty() {
        try {
            LOCK.readLock().lock();
            return internalQueue.size() == 0;
        } finally {
            LOCK.readLock().unlock();
        }
    }

    public Object[] toArray() {
        try {
            LOCK.readLock().lock();
            final List<Object> returnList = new ArrayList<Object>();
            for (final Iterator<String> innerIter = this.iterator(); innerIter.hasNext();) {
                returnList.add(innerIter.next());
            }
            return returnList.toArray();
        } finally {
            LOCK.readLock().unlock();
        }
    }

    public <T> T[] toArray(final T[] a) {
        try {
            LOCK.readLock().lock();
            int i = 0;
            for (final Iterator<String> innerIter = this.iterator(); innerIter.hasNext();) {
                a[i] = (T) innerIter.next();
                i++;
            }
            return a;
        } finally {
            LOCK.readLock().unlock();
        }
    }

    public boolean containsAll(final Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    public boolean addAll(final Collection<? extends String> c) {
        try {
            LOCK.writeLock().lock();
            final Collection<String> stringCollection = new ArrayList<String>();
            for (final Object loopObj : c) {
                if (loopObj != null) {
                    stringCollection.add(loopObj.toString());
                }
            }
            internalQueue.addFirst(stringCollection);
            return true;
        } catch (PwmDBException e) {
            throw new IllegalStateException("unexpected pwmDB error while modifying queue: " + e.getMessage(), e);
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    public boolean removeAll(final Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    public boolean add(final String s) {
        try {
            LOCK.writeLock().lock();
            internalQueue.addFirst(Collections.singletonList(s));
            return true;
        } catch (PwmDBException e) {
            throw new IllegalStateException("unexpected pwmDB error while modifying queue: " + e.getMessage(), e);
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    public boolean retainAll(final Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    public void clear() {
        try {
            LOCK.writeLock().lock();
            internalQueue.clear();
        } catch (PwmDBException e) {
            throw new IllegalStateException("unexpected pwmDB error while modifying queue: " + e.getMessage(), e);
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    public boolean remove(final Object o) {
        throw new UnsupportedOperationException();
    }

    public boolean contains(final Object o) {
        throw new UnsupportedOperationException();
    }

    public int size() {
        try {
            LOCK.readLock().lock();
            return internalQueue.size();
        } finally {
            LOCK.readLock().unlock();
        }
    }

// --------------------- Interface Deque ---------------------


    public void addFirst(final String s) {
        try {
            LOCK.writeLock().lock();
            internalQueue.addFirst(Collections.singletonList(s));
        } catch (PwmDBException e) {
            throw new IllegalStateException("unexpected pwmDB error while modifying queue: " + e.getMessage(), e);
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    public void addLast(final String s) {
        try {
            LOCK.writeLock().lock();
            internalQueue.addLast(Collections.singletonList(s));
        } catch (PwmDBException e) {
            throw new IllegalStateException("unexpected pwmDB error while modifying queue: " + e.getMessage(), e);
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    public boolean offerFirst(final String s) {
        try {
            LOCK.writeLock().lock();
            internalQueue.addFirst(Collections.singletonList(s));
            return true;
        } catch (PwmDBException e) {
            throw new IllegalStateException("unexpected pwmDB error while modifying queue: " + e.getMessage(), e);
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    public boolean offerLast(final String s) {
        try {
            LOCK.writeLock().lock();
            internalQueue.addLast(Collections.singletonList(s));
            return true;
        } catch (PwmDBException e) {
            throw new IllegalStateException("unexpected pwmDB error while modifying queue: " + e.getMessage(), e);
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    public String removeFirst() {
        final String value = pollFirst();
        if (value == null) {
            throw new NoSuchElementException();
        }
        return value;
    }

    public String removeLast() {
        final String value = pollLast();
        if (value == null) {
            throw new NoSuchElementException();
        }
        return value;
    }

    public String pollFirst() {
        try {
            LOCK.writeLock().lock();
            if (internalQueue.size() == 0) {
                return null;
            }
            final String value = internalQueue.getFirst(1).get(0);
            internalQueue.removeFirst(1);
            return value;
        } catch (PwmDBException e) {
            throw new IllegalStateException("unexpected pwmDB error while modifying queue: " + e.getMessage(), e);
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    public String pollLast() {
        try {
            LOCK.writeLock().lock();
            if (internalQueue.size() == 0) {
                return null;
            }
            final String value = internalQueue.getLast(1).get(0);
            internalQueue.removeLast(1);
            return value;
        } catch (PwmDBException e) {
            throw new IllegalStateException("unexpected pwmDB error while modifying queue: " + e.getMessage(), e);
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    public String getFirst() {
        final String value = peekFirst();
        if (value == null) {
            throw new NoSuchElementException();
        }
        return value;
    }

    public String getLast() {
        final String value = peekLast();
        if (value == null) {
            throw new NoSuchElementException();
        }
        return value;
    }

    public String peekFirst() {
        try {
            LOCK.readLock().lock();
            if (internalQueue.size() == 0) {
                return null;
            }
            return internalQueue.getFirst(1).get(0);
        } catch (PwmDBException e) {
            throw new IllegalStateException("unexpected pwmDB error while modifying queue: " + e.getMessage(), e);
        } finally {
            LOCK.readLock().unlock();
        }
    }

    public String peekLast() {
        try {
            LOCK.readLock().lock();
            if (internalQueue.size() == 0) {
                return null;
            }
            return internalQueue.getLast(1).get(0);
        } catch (PwmDBException e) {
            throw new IllegalStateException("unexpected pwmDB error while modifying queue: " + e.getMessage(), e);
        } finally {
            LOCK.readLock().unlock();
        }
    }

    public boolean removeFirstOccurrence(final Object o) {
        throw new UnsupportedOperationException();
    }

    public boolean removeLastOccurrence(final Object o) {
        throw new UnsupportedOperationException();
    }

    public void push(final String s) {
        this.addFirst(s);
    }

    public String pop() {
        final String value = this.removeFirst();
        if (value == null) {
            throw new NoSuchElementException();
        }
        return value;
    }

    public Iterator<String> descendingIterator() {
        return new InnerIterator<String>(internalQueue, false);
    }

// --------------------- Interface Iterable ---------------------

    public Iterator<String> iterator() {
        return new InnerIterator<String>(internalQueue, true);
    }

// --------------------- Interface Queue ---------------------


    public boolean offer(final String s) {
        this.add(s);
        return true;
    }

    public String remove() {
        return this.removeFirst();
    }

    public String poll() {
        final String value = this.removeFirst();
        if (value == null) {
            throw new NoSuchElementException();
        }
        return value;
    }

    public String element() {
        return this.getFirst();
    }

    public String peek() {
        return this.peekFirst();
    }

// -------------------------- INNER CLASSES --------------------------

    private class InnerIterator<K> implements Iterator {
        private Position position;
        private final InternalQueue internalQueue;
        private final boolean first;
        private final int initialModCount;


        private InnerIterator(final InternalQueue internalQueue, final boolean first) {
            this.internalQueue = internalQueue;
            this.first = first;
            initialModCount = internalQueue.getModCount();
            position = internalQueue.size() == 0 ? null : first ? internalQueue.headPosition : internalQueue.tailPosition;
        }

        public boolean hasNext() {
            if (internalQueue.getModCount() != initialModCount) {
                throw new ConcurrentModificationException();
            }
            return position != null;
        }

        public String next() {
            if (internalQueue.getModCount() != initialModCount) {
                throw new ConcurrentModificationException();
            }
            if (position == null) {
                throw new NoSuchElementException();
            }
            try {
                final String nextValue = internalQueue.pwmDB.get(internalQueue.DB, position.toString());
                if (first) {
                    position = position == internalQueue.tailPosition ? null : position.previous();
                } else {
                    position = position == internalQueue.headPosition ? null : position.next();
                }
                return nextValue;
            } catch (PwmDBException e) {
                throw new IllegalStateException("unexpected pwmDB error while iterating queue: " + e.getMessage(), e);
            }
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    private static class Position {
        private final static int RADIX = 36;
        private final static BigInteger MAXIMUM_POSITION = new BigInteger("zzzzzz", RADIX);
        private final static BigInteger MINIMUM_POSITION = BigInteger.ZERO;

        private final BigInteger bigInt;

        private Position(final BigInteger bigInt) {
            this.bigInt = bigInt;
        }

        public Position(final String bigInt) {
            this.bigInt = new BigInteger(bigInt, RADIX);
        }

        public Position next() {
            BigInteger next = bigInt.add(BigInteger.ONE);
            if (next.compareTo(MAXIMUM_POSITION) > 0) {
                next = MINIMUM_POSITION;
            }
            return new Position(next);
        }

        public Position previous() {
            BigInteger previous = bigInt.subtract(BigInteger.ONE);
            if (previous.compareTo(MINIMUM_POSITION) < 0) {
                previous = MAXIMUM_POSITION;
            }
            return new Position(previous);
        }

        public BigInteger distanceToHead(final Position head) {
            final int compareToValue = head.bigInt.compareTo(this.bigInt);
            if (compareToValue == 0) {
                return BigInteger.ZERO;
            } else if (compareToValue == 1) {
                return head.bigInt.subtract(this.bigInt);
            }

            final BigInteger tailToMax = MAXIMUM_POSITION.subtract(this.bigInt);
            final BigInteger minToHead = head.bigInt.subtract(MINIMUM_POSITION);
            return minToHead.add(tailToMax).add(BigInteger.ONE);
        }

        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append(bigInt.toString(RADIX).toUpperCase());
            while (sb.length() < 6) {
                sb.insert(0, "0");
            }
            return sb.toString();
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            final Position position = (Position) o;

            return bigInt.equals(position.bigInt);
        }

        @Override
        public int hashCode() {
            return bigInt.hashCode();
        }
    }

    private static class InternalQueue {
        private final PwmDB pwmDB;
        private final PwmDB.DB DB;
        private volatile Position headPosition;
        private volatile Position tailPosition;
        private boolean empty;
        private int modCount;

        private InternalQueue(final PwmDB pwmDB, final PwmDB.DB DB)
                throws PwmDBException {
            if (pwmDB == null) {
                throw new NullPointerException("PwnDB cannot be null");
            }

            if (pwmDB.getStatus() != PwmDB.Status.OPEN) {
                throw new IllegalStateException("PwmDB must hae a status of " + PwmDB.Status.OPEN);
            }

            if (DB == null) {
                throw new NullPointerException("DB cannot be null");
            }

            this.pwmDB = pwmDB;
            this.DB = DB;
            init();
        }

        private void init()
                throws PwmDBException {
            if (!checkVersion()) {
                clear();
            }

            final String headPositionStr = pwmDB.get(DB, KEY_HEAD_POSITION);
            final String tailPositionStr = pwmDB.get(DB, KEY_TAIL_POSITION);

            headPosition = headPositionStr != null && headPositionStr.length() > 0 ? new Position(headPositionStr) : new Position("0");
            tailPosition = tailPositionStr != null && tailPositionStr.length() > 0 ? new Position(tailPositionStr) : new Position("0");

            empty = pwmDB.get(DB, headPosition.toString()) == null;

            LOGGER.debug("loaded for db " + DB + "; headPosition=" + headPosition + ", tailPosition=" + tailPosition + ", size=" + this.size());

            if (developerDebug) {
                LOGGER.trace("debug INIT\n" + debugOutput());
            }
        }

        private boolean checkVersion() throws PwmDBException {
            final String storedVersion = pwmDB.get(DB, KEY_VERSION);
            if (storedVersion == null || !VALUE_VERSION.equals(storedVersion)) {
                LOGGER.warn("values in db " + DB + " use an outdated format, the stored events will be purged!");
                return false;
            }
            return true;
        }

        public void clear()
                throws PwmDBException {
            pwmDB.truncate(DB);

            headPosition = new Position("0");
            tailPosition = new Position("0");
            pwmDB.put(DB, KEY_HEAD_POSITION, headPosition.toString());
            pwmDB.put(DB, KEY_TAIL_POSITION, tailPosition.toString());

            pwmDB.put(DB, KEY_VERSION, VALUE_VERSION);

            empty = true;
            modCount++;

            if (developerDebug) {
                LOGGER.trace("debug CLEAR\n" + debugOutput());
            }
        }

        public int getModCount() {
            return modCount;
        }

        public int size() {
            return empty ? 0 : tailPosition.distanceToHead(headPosition).intValue() + 1;
        }

        public void removeFirst(final int removalCount) throws PwmDBException {
            if (removalCount < 1 || empty) {
                return;
            }

            if (removalCount >= size()) {
                clear();
                return;
            }

            final List<String> removalKeys = new ArrayList<String>();
            Position nextHead = headPosition;
            while (removalKeys.size() < removalCount && nextHead != tailPosition) {
                removalKeys.add(nextHead.toString());
                nextHead = nextHead.previous();
            }
            pwmDB.removeAll(DB, removalKeys);
            pwmDB.put(DB, KEY_TAIL_POSITION, nextHead.toString());
            headPosition = nextHead;
            modCount++;

            if (developerDebug) {
                LOGGER.trace("debug removeFIRST\n" + debugOutput());
            }
        }

        public void removeLast(final int removalCount) throws PwmDBException {
            if (removalCount < 1 || empty) {
                return;
            }

            if (removalCount >= size()) {
                clear();
                return;
            }

            final List<String> removalKeys = new ArrayList<String>();
            Position nextTail = tailPosition;
            while (removalKeys.size() < removalCount && nextTail != headPosition) {
                removalKeys.add(nextTail.toString());
                nextTail = nextTail.next();
            }
            pwmDB.removeAll(DB, removalKeys);
            pwmDB.put(DB, KEY_TAIL_POSITION, nextTail.toString());
            tailPosition = nextTail;
            modCount++;

            if (developerDebug) {
                LOGGER.trace("debug removeLAST\n" + debugOutput());
            }
        }

        public void addFirst(final Collection<String> values)
                throws PwmDBException {
            if (values == null || values.isEmpty()) {
                return;
            }

            if (size() + values.size() > MAX_SIZE) {
                throw new IllegalStateException("queue overflow");
            }

            final Iterator<String> valueIterator = values.iterator();

            final Map<String, String> keyValueMap = new HashMap<String, String>();
            Position nextHead = headPosition;

            if (empty) {
                keyValueMap.put(nextHead.toString(), valueIterator.next());
            }

            while (valueIterator.hasNext()) {
                nextHead = nextHead.next();
                keyValueMap.put(nextHead.toString(), valueIterator.next());
            }

            pwmDB.putAll(DB, keyValueMap);
            pwmDB.put(DB, KEY_HEAD_POSITION, String.valueOf(nextHead));
            headPosition = nextHead;
            modCount++;
            empty = false;

            if (developerDebug) {
                LOGGER.trace("debug addFirst\n" + debugOutput());
            }
        }

        public void addLast(final Collection<String> values) throws PwmDBException {
            if (values == null || values.isEmpty()) {
                return;
            }

            if (size() + values.size() > MAX_SIZE) {
                throw new IllegalStateException("queue overflow");
            }

            final Iterator<String> valueIterator = values.iterator();

            final Map<String, String> keyValueMap = new HashMap<String, String>();
            Position nextTail = tailPosition;

            if (empty) {
                keyValueMap.put(nextTail.toString(), valueIterator.next());
            }

            while (valueIterator.hasNext()) {
                nextTail = nextTail.previous();
                keyValueMap.put(nextTail.toString(), valueIterator.next());
            }

            pwmDB.putAll(DB, keyValueMap);
            pwmDB.put(DB, KEY_TAIL_POSITION, String.valueOf(nextTail));
            tailPosition = nextTail;
            modCount++;
            empty = false;

            if (developerDebug) {
                LOGGER.trace("debug addLast\n" + debugOutput());
            }
        }

        public List<String> getFirst(int getCount)
                throws PwmDBException {
            if (getCount < 1 || empty) {
                return Collections.emptyList();
            }

            if (getCount > size()) {
                getCount = size();
            }

            final List<String> returnList = new ArrayList<String>();

            Position nextHead = headPosition;
            while (returnList.size() < getCount) {
                returnList.add(pwmDB.get(DB, nextHead.toString()));
                nextHead = nextHead.previous();
            }

            if (developerDebug) {
                LOGGER.trace("debug getFirst\n" + debugOutput());
            }

            return returnList;
        }

        public List<String> getLast(int getCount)
                throws PwmDBException {
            if (getCount < 1 || empty) {
                return Collections.emptyList();
            }

            if (getCount > size()) {
                getCount = size();
            }

            final List<String> returnList = new ArrayList<String>();

            Position nextTail = tailPosition;
            while (returnList.size() < getCount) {
                returnList.add(pwmDB.get(DB, nextTail.toString()));
                nextTail = nextTail.next();
            }

            if (developerDebug) {
                LOGGER.trace("debug getLast\n" + debugOutput());
            }

            return returnList;
        }

        public String debugOutput() {
            final StringBuilder sb = new StringBuilder();
            try {
                sb.append("tailPosition=").append(tailPosition).append(", headPosition=").append(headPosition).append(", modCount=").append(modCount).append(", db=").append(DB);
                sb.append(", size=").append(size());

                Position pos = new Position("ZZZZZS");
                for (int i = 0; i < 20; i++) {
                    sb.append("\n").append(pos.toString()).append("=").append(pwmDB.get(DB, pos.toString()));
                    pos = pos.next();
                }
            } catch (PwmDBException e) {
                e.printStackTrace();
            }

            return sb.toString();
        }
    }
}
