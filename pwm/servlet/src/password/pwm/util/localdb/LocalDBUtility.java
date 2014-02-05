/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2014 The PWM Project
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

package password.pwm.util.localdb;

import password.pwm.PwmConstants;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.util.ProgressInfo;
import password.pwm.util.TimeDuration;
import password.pwm.util.TransactionSizeCalculator;
import password.pwm.util.csv.CsvReader;
import password.pwm.util.csv.CsvWriter;

import java.io.*;
import java.text.DecimalFormat;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class LocalDBUtility {

    final static List<LocalDB.DB> BACKUP_IGNORE_DBs;
    private final LocalDB localDB;
    private int exportLineCounter;
    private int importLineCounter;

    static {
        final LocalDB.DB[] ignoredDBsArray = {
                LocalDB.DB.SEEDLIST_META,
                LocalDB.DB.SEEDLIST_WORDS,
                LocalDB.DB.WORDLIST_META,
                LocalDB.DB.WORDLIST_WORDS,
        };
        BACKUP_IGNORE_DBs = Collections.unmodifiableList(Arrays.asList(ignoredDBsArray));
    }

    public LocalDBUtility(LocalDB localDB) {
        this.localDB = localDB;
    }

    public void exportLocalDB(final OutputStream outputFileStream, final PrintStream debugOutput, final boolean showLineCount)
            throws PwmOperationalException, IOException
    {
        if (outputFileStream == null) {
            throw new PwmOperationalException(PwmError.ERROR_UNKNOWN,"outputFileStream for exportLocalDB cannot be null");
        }

        writeStringToOut(debugOutput,"counting records in LocalDB...");
        final int totalLines;
        if (showLineCount) {
            exportLineCounter = 0;
            for (final LocalDB.DB loopDB : LocalDB.DB.values()) {
                if (!BACKUP_IGNORE_DBs.contains(loopDB)) {
                    exportLineCounter += localDB.size(loopDB);
                }
            }
            totalLines = exportLineCounter;
            writeStringToOut(debugOutput," total lines: " + totalLines);
        } else {
            totalLines = 0;
        }
        exportLineCounter = 0;

        writeStringToOut(debugOutput,"export beginning");
        final long startTime = System.currentTimeMillis();
        final Timer statTimer = new Timer(true);
        statTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                final String percentStr;
                if (showLineCount) {
                    final float percentComplete = (float) exportLineCounter / (float) totalLines;
                    percentStr = DecimalFormat.getPercentInstance().format(percentComplete);
                } else {
                    percentStr = "n/a";
                }

                writeStringToOut(debugOutput," exported " + exportLineCounter + " records, " + percentStr + " complete");
            }
        },30 * 1000, 30 * 1000);


        CsvWriter csvWriter = null;
        try {
            csvWriter = new CsvWriter(new OutputStreamWriter(new GZIPOutputStream(outputFileStream)),',');
            for (LocalDB.DB loopDB : LocalDB.DB.values()) {
                if (!BACKUP_IGNORE_DBs.contains(loopDB)) {
                    for (final Iterator<String> iter = localDB.iterator(loopDB); iter.hasNext();) {
                        final String key = iter.next();
                        final String value = localDB.get(loopDB, key);
                        csvWriter.writeRecord(new String[] {loopDB.toString(),key,value});
                        exportLineCounter++;
                    }
                }
            }
        } finally {
            if (csvWriter != null) {
                csvWriter.close();
            }
        }

        writeStringToOut(debugOutput, "export complete, exported " + exportLineCounter + " records in " + TimeDuration.fromCurrent(startTime).asLongString());
        statTimer.cancel();
    }

    private static void writeStringToOut(final PrintStream out, final String string) {
        if (out == null) {
            return;
        }

        out.println(PwmConstants.DEFAULT_DATETIME_FORMAT.format(new Date()) + " " + string);
    }

    public void importLocalDB(final File inputFile, final PrintStream out)
            throws PwmOperationalException, IOException
    {
        if (inputFile == null) {
            throw new PwmOperationalException(PwmError.ERROR_UNKNOWN,"inputFile for importLocalDB cannot be null");
        }

        if (!inputFile.exists()) {
            throw new PwmOperationalException(PwmError.ERROR_UNKNOWN,"inputFile for importLocalDB does not exist");
        }

        writeStringToOut(out, "counting records in input file...");
        importLineCounter = 0;
        CsvReader csvReader = null;
        try {
            csvReader = new CsvReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(inputFile))),',');
            while (csvReader.readRecord()) {
                importLineCounter++;
            }
        } finally {
            if (csvReader != null) {csvReader.close();}
        }
        final int totalLines = importLineCounter;

        if (totalLines <= 0) {
            throw new PwmOperationalException(PwmError.ERROR_UNKNOWN,"inputFile for importLocalDB is empty");
        }

        writeStringToOut(out, "clearing LocalDB...");
        for (final LocalDB.DB loopDB : LocalDB.DB.values()) {
            writeStringToOut(out, " truncating " + loopDB.toString());
            localDB.truncate(loopDB);
        }
        writeStringToOut(out, "LocalDB cleared");

        importLineCounter = 0;
        writeStringToOut(out, " total lines: " + totalLines);

        writeStringToOut(out, "beginning restore...");

        final Date startTime = new Date();
        final Timer statTimer = new Timer(true);
        final TransactionSizeCalculator transactionCalculator = new TransactionSizeCalculator(900, 50, 50 * 1000);
        statTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                final ProgressInfo progressInfo = new ProgressInfo(startTime, totalLines, importLineCounter);
                writeStringToOut(out," restored " + progressInfo.debugOutput() + ", transactionSize=" + transactionCalculator.getTransactionSize());
            }
        },15 * 1000, 30 * 1000);

        final Map<LocalDB.DB,Map<String,String>> transactionMap = new HashMap<LocalDB.DB, Map<String, String>>();
        for (final LocalDB.DB loopDB : LocalDB.DB.values()) {
            transactionMap.put(loopDB,new HashMap<String, String>());
        }

        try {
            csvReader = new CsvReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(inputFile))),',');
            while (csvReader.readRecord()) {
                final LocalDB.DB db = LocalDB.DB.valueOf(csvReader.get(0));
                final String key = csvReader.get(1);
                final String value = csvReader.get(2);
                localDB.put(db, key, value);
                transactionMap.get(db).put(key,value);
                int cachedTransactions = 0;
                for (final LocalDB.DB loopDB : LocalDB.DB.values()) {
                    cachedTransactions += transactionMap.get(loopDB).keySet().size();
                }
                if (cachedTransactions >= transactionCalculator.getTransactionSize()) {
                    final long startTxnTime = System.currentTimeMillis();
                    for (final LocalDB.DB loopDB : LocalDB.DB.values()) {
                        localDB.putAll(loopDB, transactionMap.get(loopDB));
                        importLineCounter += transactionMap.get(loopDB).size();
                        transactionMap.get(loopDB).clear();
                    }
                    transactionCalculator.recordLastTransactionDuration(TimeDuration.fromCurrent(startTxnTime));
                }

            }
        } finally {
            if (csvReader != null) {csvReader.close();}
        }

        for (final LocalDB.DB loopDB : LocalDB.DB.values()) {
            localDB.putAll(loopDB, transactionMap.get(loopDB));
            transactionMap.get(loopDB).clear();
        }

        writeStringToOut(out, "restore complete, restored " + importLineCounter + " records in " + TimeDuration.fromCurrent(startTime).asLongString());
        statTimer.cancel();
    }
}
