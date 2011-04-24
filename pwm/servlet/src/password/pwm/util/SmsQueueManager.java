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

import com.google.gson.Gson;
import org.apache.commons.lang.StringEscapeUtils;
import password.pwm.ContextManager;
import password.pwm.PwmConstants;
import password.pwm.PwmService;
import password.pwm.bean.SmsItemBean;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthRecord;
import password.pwm.util.db.PwmDB;
import password.pwm.util.db.PwmDBException;
import password.pwm.util.db.PwmDBStoredQueue;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.net.*;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Menno Pieters, Jason D. Rivard
 */
public class SmsQueueManager implements PwmService {
// ------------------------------ FIELDS ------------------------------

    private static final int ERROR_RETRY_WAIT_TIME_MS = 60 * 1000;

    private static final PwmLogger LOGGER = PwmLogger.getLogger(SmsQueueManager.class);

    private final PwmDBStoredQueue smsSendQueue;
    private final ContextManager theManager;
    private final Proxy proxy;

    private STATUS status = PwmService.STATUS.NEW;
    private volatile boolean threadActive;
    private long maxErrorWaitTimeMS = 5 * 60 * 1000;

    private HealthRecord lastSendFailure;
    
    public enum SmsNumberFormat {
        PLAIN,
        PLUS,
        ZEROS
    }
    
    public enum SmsDataEncoding {
    	NONE,
    	URL,
    	XML,
    	HTML,
    	CSV,
    	JAVA,
    	JAVASCRIPT,
    	SQL
    }

// --------------------------- CONSTRUCTORS ---------------------------

    public SmsQueueManager(final ContextManager theManager)
            throws PwmDBException {
        this.theManager = theManager;
        this.maxErrorWaitTimeMS = theManager.getConfig().readSettingAsLong(PwmSetting.SMS_MAX_QUEUE_AGE) * 1000;
        final String pType = theManager.getConfig().readSettingAsString(PwmSetting.HTTP_PROXYTYPE);
        final String pAddr = theManager.getConfig().readSettingAsString(PwmSetting.HTTP_PROXYSERVER);
        final Long pPort = theManager.getConfig().readSettingAsLong(PwmSetting.HTTP_PROXYPORT);
        final Proxy.Type pt = Proxy.Type.valueOf(pType.toUpperCase());
        Proxy proxy;
        if (pt == Proxy.Type.DIRECT) {
        	proxy = Proxy.NO_PROXY;
        } else {
        	try {
    	    	final Pattern pat = Pattern.compile("^([A-Za-z0-9_\\.-]+\\.[A-Za-z]{2,}|[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}|[0-9A-Fa-f:]{3,})$");
        		final Matcher mat = pat.matcher(pAddr);
        		if (mat.matches()) {
	        		final String hostname = mat.group(1);
		        	int port = (pPort==null)?0:pPort.intValue();
		        	if (port == 0) {
		        		switch (pt) {
		        			case SOCKS:
		        				port = 1080;
		        				break;
		        			case HTTP:
		        				port = 8080;
		        				break;
		        			default:
		        				break;
		        		}
		        	}
        			final InetSocketAddress sa = new InetSocketAddress(hostname, port);
        			proxy = new Proxy(pt, sa);
        			LOGGER.info("Proxy configured: [" + pAddr + "]:" + pPort.toString());
	        	} else {
    	    		LOGGER.error("Proxy address not valid: [" +  pAddr + "]:" + pPort.toString() +". Setting to NOPROXY");
	    	    	proxy = Proxy.NO_PROXY;
        		}
        	} catch (Exception e) {
        		LOGGER.error("Error retrieving proxy address: " + e.getMessage());
   	    		LOGGER.error("Could not instantiate proxy for [" +  pAddr + "]:" + pPort.toString() +". Setting to NOPROXY");
    	    	proxy = Proxy.NO_PROXY;
        	}
        }
       	this.proxy = proxy;

        final PwmDB pwmDB = theManager.getPwmDB();
        smsSendQueue = PwmDBStoredQueue.createPwmDBStoredQueue(pwmDB, PwmDB.DB.SMS_QUEUE);

        status = PwmService.STATUS.OPEN;

        {
            final SmsSendThread smsSendThread = new SmsSendThread();
            smsSendThread.setDaemon(true);
            smsSendThread.setName("pwm-SmsQueueManager");
            smsSendThread.start();
            threadActive = true;
        }
    }
// ------------------------ INTERFACE METHODS ------------------------

// --------------------- Interface PwmService ---------------------

    public void init(final ContextManager contextManager) throws PwmUnrecoverableException {
    }

    public STATUS status() {
        return status;
    }

    public void close() {
        status = PwmService.STATUS.CLOSED;

        {
            final long startTime = System.currentTimeMillis();
            while (threadActive && (System.currentTimeMillis() - startTime) < 300) {
                Helper.pause(100);
            }
        }

        if (threadActive) {
            final long startTime = System.currentTimeMillis();
            LOGGER.info("waiting up to 30 seconds for sms sender thread to close....");

            while (threadActive && (System.currentTimeMillis() - startTime) < 30 * 1000) {
                Helper.pause(100);
            }

            try {
                if (!smsSendQueue.isEmpty()) {
                    LOGGER.warn("closing sms queue with " + smsSendQueue.size() + " message(s) in queue");
                }
            } catch (Exception e) {
                LOGGER.error("unexpected exception while shutting down: " + e.getMessage());
            }
        }

        LOGGER.debug("closed");
    }

    public List<HealthRecord> healthCheck() {
        if (lastSendFailure == null) {
            return null;
        }

        return Collections.singletonList(lastSendFailure);
    }

// -------------------------- OTHER METHODS --------------------------

    public void addSmsToQueue(final SmsItemBean smsItem) throws PwmUnrecoverableException {
        if (status != PwmService.STATUS.OPEN) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_CLOSING));
        }

        if (!determineIfSmsCanBeDelivered(smsItem)) {
            return;
        }
        
        final SmsEvent event = new SmsEvent(smsItem, System.currentTimeMillis());
        if (smsSendQueue.size() < PwmConstants.MAX_SMS_QUEUE_SIZE) {
            try {
                final String jsonEvent = (new Gson()).toJson(event);
                smsSendQueue.addLast(jsonEvent);
            } catch (Exception e) {
                LOGGER.error("error writing to pwmDB queue, discarding sms send request: " + e.getMessage());
            }
        } else {
            LOGGER.warn("sms queue full, discarding sms send request: " + smsItem);
        }
    }

    private boolean determineIfSmsCanBeDelivered(final SmsItemBean smsItem) {
        final Configuration config = theManager.getConfig();
        final String gatewayUrl = config.readSettingAsString(PwmSetting.SMS_GATEWAY_URL);
        final String gatewayUser = config.readSettingAsString(PwmSetting.SMS_GATEWAY_USER);
        final String gatewayPass = config.readSettingAsString(PwmSetting.SMS_GATEWAY_PASSWORD);

        if (gatewayUrl == null || gatewayUrl.length() < 1) {
            LOGGER.debug("discarding sms send event (no SMS gateway url configured) " + smsItem.toString());
            return false;
        }

        if (gatewayUser == null || gatewayUser.length() < 1) {
            LOGGER.debug("discarding sms send event (no SMS gateway user configured) " + smsItem.toString());
            return false;
        }

        if (gatewayPass == null || gatewayPass.length() < 1) {
            LOGGER.debug("discarding sms send event (no SMS gateway password configured) " + smsItem.toString());
            return false;
        }

        if (smsItem.getTo() == null || smsItem.getTo().length() < 1) {
            LOGGER.debug("discarding sms send event (no to address) " + smsItem.toString());
            return false;
        }

        if (smsItem.getMessage() == null || smsItem.getMessage().length() < 1) {
            LOGGER.debug("discarding sms send event (no message) " + smsItem.toString());
            return false;
        }

        return true;
    }

    public int queueSize() {
        return this.smsSendQueue.size();
    }

    private boolean processQueue() {
        while (smsSendQueue.peekFirst() != null) {
            final String jsonEvent = smsSendQueue.peekFirst();
            if (jsonEvent != null) {
                final SmsEvent event = (new Gson()).fromJson(jsonEvent, SmsEvent.class);

                if ((System.currentTimeMillis() - maxErrorWaitTimeMS) > event.getQueueInsertTimestamp()) {
                    LOGGER.debug("discarding sms event due to maximum retry age: " + event.getSmsItem().toString());
                    smsSendQueue.pollFirst();
                } else {
                    final SmsItemBean smsItem = event.getSmsItem();
                    if (!determineIfSmsCanBeDelivered(smsItem)) {
                        smsSendQueue.pollFirst();
                        return true;
                    }

                    final boolean success = sendSms(smsItem);
                    if (!success) {
                        return false;
                    }
                    smsSendQueue.pollFirst();
                }
            }
        }
        return true;
    }

    private boolean sendSms(final SmsItemBean smsItemBean) {
    	boolean success = true;
    	while (success && smsItemBean.hasNextPart()) {
    		success = sendSmsPart(smsItemBean);
    	}
    	return success;
    }
    
    private boolean sendSmsPart(final SmsItemBean smsItemBean) {
        final Configuration config = theManager.getConfig();
        final String gatewayUrl = config.readSettingAsString(PwmSetting.SMS_GATEWAY_URL);
        final String gatewayUser = config.readSettingAsString(PwmSetting.SMS_GATEWAY_USER);
        String senderId = smsItemBean.getFrom();
        final String to = formatSmsNumber(smsItemBean.getTo());
        final String gatewayPass = config.readSettingAsString(PwmSetting.SMS_GATEWAY_PASSWORD);
        final String gatewayMethod = config.readSettingAsString(PwmSetting.SMS_GATEWAY_METHOD);
        final String gatewayAuthMethod = config.readSettingAsString(PwmSetting.SMS_GATEWAY_AUTHMETHOD);
        String requestData = config.readLocalizedStringSetting(PwmSetting.SMS_REQUEST_DATA, smsItemBean.getLocale());
        final String contentType = config.readSettingAsString(PwmSetting.SMS_REQUEST_CONTENT_TYPE);
        final SmsDataEncoding encoding = SmsDataEncoding.valueOf(config.readSettingAsString(PwmSetting.SMS_REQUEST_CONTENT_ENCODING));
        
        // Replace strings in requestData
        requestData = requestData.replaceAll("%USER%", smsDateEncode(gatewayUser, encoding));
        requestData = requestData.replaceAll("%PASS%", smsDateEncode(gatewayPass, encoding));
        if (senderId == null) { senderId = ""; }
        requestData = requestData.replaceAll("%SENDERID%", smsDateEncode(senderId, encoding));
        requestData = requestData.replaceAll("%MESSAGE%", smsDateEncode(smsItemBean.getNextPart(), encoding));
        requestData = requestData.replaceAll("%TO%", smsDateEncode(formatSmsNumber(smsItemBean.getTo()), encoding));
        if (requestData.indexOf("%REQUESTID%")>=0) {
            final String chars = config.readSettingAsString(PwmSetting.SMS_REQUESTID_CHARS);
            final int idLength = new Long(config.readSettingAsLong(PwmSetting.SMS_REQUESTID_LENGTH)).intValue();
            final String requestId = Helper.generateToken(chars, idLength);
            requestData = requestData.replaceAll("%REQUESTID%", smsDateEncode(requestId, encoding));
        }
        
        LOGGER.trace("SMS data: " + requestData);
        try {
            final HttpURLConnection h;
            if (gatewayMethod.equalsIgnoreCase("POST")) {
                // POST request
                final URL u = new URL(gatewayUrl);
                h = (HttpURLConnection) u.openConnection(this.proxy);
                h.setRequestMethod("POST");
                if (contentType != null && contentType.length()>0) {
	                h.setRequestProperty("Content-Type", contentType);
                }
                h.setRequestProperty("Content-Length", String.valueOf(requestData.length()));
                h.setDoOutput(true);
                h.getOutputStream().write(requestData.getBytes());
            } else {
                // GET request
                String fullUrl = gatewayUrl;
                if (!fullUrl.endsWith("?")) {
                    fullUrl += "?";
                }
                fullUrl += requestData;
                final URL u = new URL(fullUrl);
                h = (HttpURLConnection) u.openConnection(this.proxy);
            }
            if (gatewayAuthMethod.equalsIgnoreCase("BASIC") && gatewayUser != null && gatewayPass != null) {
            	final String userpass = gatewayUser + ":" + gatewayPass;
            	final String encodedAuthorization = Base64Util.encodeBytes(userpass.getBytes());
            	h.setRequestProperty("Authorization", "Basic " + encodedAuthorization);
            }
            final InputStream ins = h.getInputStream();
            final InputStreamReader isr = new InputStreamReader(ins);
            final BufferedReader in = new BufferedReader(isr);
            final List<String> okMessages = config.readStringArraySetting(PwmSetting.SMS_RESPONSE_OK_REGEX);
            if (okMessages != null && okMessages.size() > 0) {
                Boolean ok = false;
                String input="-";
                while (!ok && input != null) {
                    input = in.readLine();
                    LOGGER.trace("> " + input);                  
                    ok = matchExpressions(input, okMessages);
                }
                in.close();
                h.disconnect();
                return ok;
            } else { 
                in.close();
                h.disconnect();
                return true;
            }        
        } catch (java.io.IOException e) {
            LOGGER.error("unexpected exception while sending SMS: " + e.getMessage(), e);
        }
        
        return false;
    }
    
    private String smsDateEncode(final String data, final SmsDataEncoding encoding) {
    	final String returnData;
        switch (encoding) {
            case NONE:
                returnData = data;
                break;
            case CSV:
                returnData = StringEscapeUtils.escapeCsv(data);
                break;
            case HTML:
                returnData = StringEscapeUtils.escapeHtml(data);
                break;
            case JAVA:
                returnData = StringEscapeUtils.escapeJava(data);
                break;
            case JAVASCRIPT:
                returnData = StringEscapeUtils.escapeJavaScript(data);
                break;
            case XML:
                returnData = StringEscapeUtils.escapeXml(data);
                break;
            default:
                returnData = URLEncoder.encode(data);
                break;
        }
        return returnData;
    }
    
    private boolean matchExpressions(final String in, final List<String> regexes) {
        if (in != null) {
            if (regexes == null) {
                return true;
            }
            for (final Iterator iter = regexes.iterator(); iter.hasNext();) {
                final String s = (String)iter.next();
                LOGGER.trace("Matching string \""+in+"\" against pattern \"" + s + "\"");
                if (in.matches(s)) return true;
            }
        }
        return false;
    }
    
    private String formatSmsNumber(final String smsNumber) {
        final Configuration config = theManager.getConfig();
        final String cc = config.readSettingAsString(PwmSetting.SMS_DEFAULT_COUNTRY_CODE);
        final SmsNumberFormat format = SmsNumberFormat.valueOf(config.readSettingAsString(PwmSetting.SMS_PHONE_NUMBER_FORMAT).toUpperCase());
        String ret = smsNumber;
        // Remove (0)
        ret = ret.replaceAll("\\(0\\)","");
        // Remove leading double zero, replace by plus
        if (ret.substring(0,1).equals("00")) {
            ret = "+" + ret.substring(2);
        }
        // Replace leading zero by country code
        if (ret.charAt(0) == '0') {
            ret = cc + ret.substring(1);
        }
        // Add a leading plus if necessary
        if (ret.charAt(0) != '+') {
            ret = "+" + ret;
        }
        // Remove any non-numeric, non-plus characters
        final String tmp = ret;
        ret = "";
        for(int i=0;i<tmp.length();i++) {
            if ((i==0&&tmp.charAt(i)=='+')||(
                (tmp.charAt(i)>='0'&&tmp.charAt(i)<='9'))
               ) {
                ret += tmp.charAt(i);
            }
        }
        // Now the number should be in full international format
        // Let's see if we need to change anything:
        switch(format) {
            case PLAIN:
                // remove plus
                ret = ret.substring(1);
                break;
            case PLUS:
                // keep full international format
                break;
            case ZEROS:
                // replace + with 00
                ret = "00" + ret.substring(1);
                break;
            default:
                // keep full international format
                break;
        }
        return ret;
    }

// -------------------------- INNER CLASSES --------------------------
    private class SmsSendThread extends Thread {
        public void run() {
            LOGGER.trace("starting up sms queue processing thread, queue size is " + smsSendQueue.size());

            while (status == PwmService.STATUS.OPEN) {

                boolean success = false;
                try {
                    success = processQueue();
                    if (success) {
                        lastSendFailure = null;
                    }
                } catch (Exception e) {
                    LOGGER.error("unexpected exception while processing sms queue: " + e.getMessage(), e);
                    LOGGER.error("unable to process sms queue successfully; sleeping for " + TimeDuration.asCompactString(ERROR_RETRY_WAIT_TIME_MS));
                }

                final long startTime = System.currentTimeMillis();
                final long sleepTime = success ? 1000 : ERROR_RETRY_WAIT_TIME_MS;
                while (PwmService.STATUS.OPEN == status && (System.currentTimeMillis() - startTime) < sleepTime) {
                    Helper.pause(100);
                }
            }

            // try to clear out the queue before the thread exits...
            try {
                processQueue();
            } catch (Exception e) {
                LOGGER.error("unexpected exception while processing sms queue: " + e.getMessage(), e);
            }

            threadActive = false;
            LOGGER.trace("closing sms queue processing thread");
        }
    }
    
    private static class SmsEvent implements Serializable {
        private SmsItemBean smsItem;
        private long queueInsertTimestamp;

        private SmsEvent() {
        }

        private SmsEvent(SmsItemBean smsItem, long queueInsertTimestamp) {
            this.smsItem = smsItem;
            this.queueInsertTimestamp = queueInsertTimestamp;
        }

        public SmsItemBean getSmsItem() {
            return smsItem;
        }

        public long getQueueInsertTimestamp() {
            return queueInsertTimestamp;
        }
    }
    
}