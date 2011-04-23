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

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.jdom.CDATA;
import org.jdom.Comment;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import password.pwm.util.Helper;
import password.pwm.PwmConstants;
import password.pwm.util.Base64Util;
import password.pwm.util.PwmLogger;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Jason D. Rivard
 */
public class StoredConfiguration implements Serializable, Cloneable {

    private static final PwmLogger LOGGER = PwmLogger.getLogger(StoredConfiguration.class);
    private static final DateFormat STORED_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");
    private static final String XML_FORMAT_VERSION = "2";

    public static final String PROPERTY_KEY_SETTING_CHECKSUM = "settingsChecksum";
    public static final String PROPERTY_KEY_CONFIG_IS_EDITABLE = "configIsEditable";
    public static final String PROPERTY_KEY_CONFIG_EPOCH = "configEpoch";
    public static final String PROPERTY_KEY_TEMPLATE = "templateKey";

    private Date createTime = new Date();
    private Date modifyTime = new Date();
    private Map<PwmSetting, StoredValue> settingMap = new HashMap<PwmSetting, StoredValue>();
    private Map<String, String> propertyMap = new HashMap<String, String>();

    private boolean locked = false;

    static {
        STORED_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("Zulu"));
    }

    public void lock() {
        settingMap = Collections.unmodifiableMap(settingMap);
        propertyMap = Collections.unmodifiableMap(propertyMap);
        locked = true;
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        final StoredConfiguration clonedConfig = (StoredConfiguration) super.clone();
        clonedConfig.createTime = this.createTime;
        clonedConfig.modifyTime = this.modifyTime;
        clonedConfig.settingMap = new HashMap<PwmSetting, StoredValue>();
        clonedConfig.settingMap.putAll(this.settingMap);
        clonedConfig.propertyMap = new HashMap<String, String>();
        clonedConfig.propertyMap.putAll(this.propertyMap);
        clonedConfig.locked = false;
        return clonedConfig;
    }

    public Date getModifyTime() {
        return modifyTime;
    }

    public static StoredConfiguration getDefaultConfiguration() {
        return new StoredConfiguration();
    }

    public PwmSetting.Template template() {
        final String propertyValue = propertyMap.get(PROPERTY_KEY_TEMPLATE);
        try {
            return PwmSetting.Template.valueOf(propertyValue);
        } catch (IllegalArgumentException e) {
            return PwmSetting.Template.DEFAULT;
        } catch (NullPointerException e) {
            return PwmSetting.Template.DEFAULT;
        }
    }

    private static StoredValue defaultValue(final PwmSetting pwmSetting, final PwmSetting.Template template) {
        switch (pwmSetting.getSyntax()) {
            case STRING:
            case BOOLEAN:
            case NUMERIC:
                return StoredValue.StoredValueString.fromJsonString(pwmSetting.getDefaultValue(template));
            case PASSWORD:
                return StoredValue.StoredValuePassword.fromJsonString(pwmSetting.getDefaultValue(template));
            case LOCALIZED_STRING:
            case LOCALIZED_TEXT_AREA:
                return StoredValue.StoredValueLocaleList.fromJsonString(pwmSetting.getDefaultValue(template));
            case LOCALIZED_STRING_ARRAY:
                return StoredValue.StoredValueLocaleMap.fromJsonString(pwmSetting.getDefaultValue(template));
            case STRING_ARRAY:
                return StoredValue.StoredValueList.fromJsonString(pwmSetting.getDefaultValue(template));

            default:
                throw new IllegalArgumentException("unable to read default value for: " + pwmSetting.toString());
        }
    }

    public boolean isDefaultValue(final PwmSetting setting) {
        return !settingMap.containsKey(setting);
    }

    public String readSetting(final PwmSetting setting) {
        switch (setting.getSyntax()) {
            case STRING:
            case BOOLEAN:
            case NUMERIC:
            case PASSWORD:
                final StoredValue value = settingMap.get(setting);
                return (String) (value == null ? defaultValue(setting,template()) : value).toNativeObject();

            default:
                throw new IllegalArgumentException("may not read setting as string: " + setting.toString());
        }
    }

    public void writeSetting(final PwmSetting setting, final String value) {
        preModifyActions();
        switch (setting.getSyntax()) {
            case STRING:
            case BOOLEAN:
            case NUMERIC:
                settingMap.put(setting, new StoredValue.StoredValueString(value));
                break;
            case PASSWORD:
                settingMap.put(setting, new StoredValue.StoredValuePassword(value));
                break;

            default:
                throw new IllegalArgumentException("may not write setting as string: " + setting.toString());
        }
    }

    public void resetSetting(final PwmSetting setting) {
        preModifyActions();
        settingMap.remove(setting);
    }

    public Map<String, String> readLocalizedStringSetting(final PwmSetting setting) {
        if (PwmSetting.Syntax.LOCALIZED_STRING != setting.getSyntax() && PwmSetting.Syntax.LOCALIZED_TEXT_AREA != setting.getSyntax()) {
            throw new IllegalArgumentException("may not read LOCALIZED_STRING or LOCALIZED_TEXT_AREA values for setting: " + setting.toString());
        }

        final StoredValue value = settingMap.get(setting);
        return (Map<String, String>) (value == null ? defaultValue(setting,template()) : value).toNativeObject();
    }

    public void writeLocalizedSetting(final PwmSetting setting, final Map<String, String> values) {
        preModifyActions();
        if (PwmSetting.Syntax.LOCALIZED_STRING != setting.getSyntax() && PwmSetting.Syntax.LOCALIZED_TEXT_AREA != setting.getSyntax()) {
            throw new IllegalArgumentException("may not write value to non-LOCALIZED_STRING or LOCALIZED_TEXT_AREA setting: " + setting.toString());
        }

        settingMap.put(setting, new StoredValue.StoredValueLocaleList(values));
    }

    public List<String> readStringArraySetting(final PwmSetting setting) {
        if (PwmSetting.Syntax.STRING_ARRAY != setting.getSyntax()) {
            throw new IllegalArgumentException("may not read STRING_ARRAY value for setting: " + setting.toString());
        }

        final StoredValue value = settingMap.get(setting);
        return (List<String>) (value == null ? defaultValue(setting,template()) : value).toNativeObject();
    }

    public void writeStringArraySetting(final PwmSetting setting, final List<String> values) {
        preModifyActions();
        if (PwmSetting.Syntax.STRING_ARRAY != setting.getSyntax()) {
            throw new IllegalArgumentException("may not write STRING_ARRAY value to setting: " + setting.toString());
        }

        settingMap.put(setting, new StoredValue.StoredValueList(values));
    }

    public Map<String, List<String>> readLocalizedStringArraySetting(final PwmSetting setting) {
        if (PwmSetting.Syntax.LOCALIZED_STRING_ARRAY != setting.getSyntax()) {
            throw new IllegalArgumentException("may not read LOCALIZED_STRING_ARRAY value for setting: " + setting.toString());
        }

        final StoredValue value = settingMap.get(setting);
        return (Map<String, List<String>>) (value == null ? defaultValue(setting,template()) : value).toNativeObject();
    }

    public void writeLocalizedStringArraySetting(final PwmSetting setting, final Map<String, List<String>> values) {
        preModifyActions();
        if (PwmSetting.Syntax.LOCALIZED_STRING_ARRAY != setting.getSyntax()) {
            throw new IllegalArgumentException("may not write LOCALIZED_STRING_ARRAY value to setting: " + setting.toString());
        }

        settingMap.put(setting, new StoredValue.StoredValueLocaleMap(values));
    }

    public String readProperty(final String propertyName) {
        return propertyMap.get(propertyName);
    }

    public void writeProperty(final String propertyName, final String propertyValue) {
        preModifyActions();
        if (propertyValue == null) {
            propertyMap.remove(propertyName);
        } else {
            propertyMap.put(propertyName, propertyValue);
        }
    }

    public Set<String> readPropertyKeys() {
        return Collections.unmodifiableSet(propertyMap.keySet());
    }

    public String toXml()
            throws IOException {
        final Element pwmConfigElement = new Element("PwmConfiguration");
        pwmConfigElement.addContent(new Comment("Configuration file generated for PWM Password Self Service"));
        pwmConfigElement.addContent(new Comment("WARNING: This configuration file contains sensitive security information, please handle with care!"));
        pwmConfigElement.addContent(new Comment("NOTICE: This file is encoded as UTF-8.  Do not save or edit this file with an editor that does not support UTF-8 encoding."));

        { // write properties section
            final Element propertiesElement = new Element("properties");
            propertyMap.put(PROPERTY_KEY_SETTING_CHECKSUM, settingChecksum());
            for (final String key : propertyMap.keySet()) {
                final Element propertyElement = new Element("property");
                propertyElement.setAttribute("key", key);
                propertyElement.addContent(propertyMap.get(key));
                propertiesElement.addContent(propertyElement);
            }
            pwmConfigElement.addContent(propertiesElement);
        }

        final Element settingsElement = new Element("settings");
        final Map<PwmSetting.Category, List<PwmSetting>> valuesByCategory = PwmSetting.valuesByCategory(null);
        for (final PwmSetting.Category category : valuesByCategory.keySet()) {
            for (final PwmSetting setting : valuesByCategory.get(category)) {
                final Element settingElement = new Element("setting");
                settingElement.setAttribute("key", setting.getKey());
                settingElement.setAttribute("syntax", setting.getSyntax().toString());

                {
                    final Element labelElement = new Element("label");
                    labelElement.addContent(setting.getLabel(Locale.getDefault()));
                    settingElement.addContent(labelElement);
                }

                if (isDefaultValue(setting)) {
                    settingElement.addContent(new Element("default"));
                } else {
                    final List<Element> valueElements;
                    if (setting.getSyntax() == PwmSetting.Syntax.PASSWORD) {
                        final String key = STORED_DATE_FORMAT.format(createTime) + StoredConfiguration.class.getSimpleName();
                        valueElements = ((StoredValue.StoredValuePassword) settingMap.get(setting)).toXmlValues("value", key);
                    } else {
                        valueElements = settingMap.get(setting).toXmlValues("value");
                    }
                    for (final Element loopValueElement : valueElements) {
                        settingElement.addContent(loopValueElement);
                    }
                }

                settingsElement.addContent(settingElement);
            }
        }
        pwmConfigElement.addContent(settingsElement);

        pwmConfigElement.setAttribute("pwmVersion", PwmConstants.PWM_VERSION);
        pwmConfigElement.setAttribute("pwmBuild", PwmConstants.BUILD_NUMBER);
        pwmConfigElement.setAttribute("createTime", STORED_DATE_FORMAT.format(createTime));
        pwmConfigElement.setAttribute("modifyTime", STORED_DATE_FORMAT.format(modifyTime));
        pwmConfigElement.setAttribute("xmlVersion", XML_FORMAT_VERSION);

        final XMLOutputter outputter = new XMLOutputter();
        outputter.setFormat(Format.getPrettyFormat());
        return outputter.outputString(new Document(pwmConfigElement));
    }


    public static StoredConfiguration fromXml(final String xmlData)
            throws Exception {
        final SAXBuilder builder = new SAXBuilder();
        final Reader in = new StringReader(xmlData);
        final Document inputDocument;
        try {
            inputDocument = builder.build(in);
        } catch (Exception e) {
            throw new Exception("error parsing xml data: " + e.getMessage());
        }

        final Set<PwmSetting> seenSettings = new HashSet<PwmSetting>();

        final StoredConfiguration newConfiguration = StoredConfiguration.getDefaultConfiguration();
        try {
            final Element rootElement = inputDocument.getRootElement();
            final String createTimeString = rootElement.getAttributeValue("createTime");
            if (createTimeString == null) {
                throw new IllegalArgumentException("missing createTime timestamp");
            }
            final String modifyTimeString = rootElement.getAttributeValue("modifyTime");
            newConfiguration.createTime = STORED_DATE_FORMAT.parse(createTimeString);
            final Element settingsElement = rootElement.getChild("settings");
            final List settingElements = settingsElement.getChildren("setting");
            for (final Object loopSetting : settingElements) {
                final Element settingElement = (Element) loopSetting;
                final String keyName = settingElement.getAttributeValue("key");
                final PwmSetting pwmSetting = PwmSetting.forKey(keyName);
                seenSettings.add(pwmSetting);

                if (pwmSetting == null) {
                    LOGGER.info("unknown setting key while parsing input configuration: " + keyName);
                } else {
                    if (settingElement.getChild("value") != null) {
                        switch (pwmSetting.getSyntax()) {
                            case LOCALIZED_STRING:
                            case LOCALIZED_TEXT_AREA: {
                                final List valueElements = settingElement.getChildren("value");
                                final Map<String, String> values = new TreeMap<String, String>();
                                for (final Object loopValue : valueElements) {
                                    final Element loopValueElement = (Element) loopValue;
                                    final String localeString = loopValueElement.getAttributeValue("locale");
                                    final String value = loopValueElement.getText();
                                    values.put(localeString == null ? "" : localeString, value);
                                }
                                newConfiguration.writeLocalizedSetting(pwmSetting, values);
                            }
                            break;

                            case STRING_ARRAY: {
                                final List valueElements = settingElement.getChildren("value");
                                final List<String> values = new ArrayList<String>();
                                for (final Object loopValue : valueElements) {
                                    final Element loopValueElement = (Element) loopValue;
                                    final String value = loopValueElement.getText();
                                    values.add(value);
                                }
                                newConfiguration.writeStringArraySetting(pwmSetting, values);
                            }
                            break;

                            case LOCALIZED_STRING_ARRAY: {
                                final List valueElements = settingElement.getChildren("value");
                                final Map<String, List<String>> values = new TreeMap<String, List<String>>();
                                for (final Object loopValue : valueElements) {
                                    final Element loopValueElement = (Element) loopValue;
                                    final String localeString = loopValueElement.getAttributeValue("locale") == null ? "" : loopValueElement.getAttributeValue("locale");
                                    final String value = loopValueElement.getText();
                                    List<String> valueList = values.get(localeString);
                                    if (valueList == null) {
                                        valueList = new ArrayList<String>();
                                        values.put(localeString, valueList);
                                    }
                                    valueList.add(value);
                                }
                                newConfiguration.writeLocalizedStringArraySetting(pwmSetting, values);
                            }
                            break;

                            case PASSWORD: {
                                final Element valueElement = settingElement.getChild("value");
                                final String encodedValue = valueElement.getText();
                                try {
                                    final String key = STORED_DATE_FORMAT.format(newConfiguration.createTime) + StoredConfiguration.class.getSimpleName();
                                    final String decodedValue = TextConversations.decryptValue(encodedValue, key);
                                    newConfiguration.writeSetting(pwmSetting, decodedValue);
                                } catch (Exception e) {
                                    newConfiguration.writeSetting(pwmSetting, "");
                                    throw new RuntimeException("unable to decode value: " + e.getMessage());
                                }
                            }
                            break;

                            default:
                                final Element valueElement = settingElement.getChild("value");
                                final String value = valueElement.getText();
                                newConfiguration.writeSetting(pwmSetting, value);
                        }
                    }
                }
            }

            final Element propertiesElement = rootElement.getChild("properties");
            if (propertiesElement != null) {
                for (final Object loopElementObj : propertiesElement.getChildren("property")) {
                    final Element element = (Element) loopElementObj;
                    final String key = element.getAttributeValue("key");
                    final String value = element.getText();
                    newConfiguration.propertyMap.put(key, value);
                }
            }

            if (modifyTimeString == null) {
                throw new IllegalArgumentException("missing modifyTime timestamp");
            }
            newConfiguration.modifyTime = STORED_DATE_FORMAT.parse(modifyTimeString);

            for (final PwmSetting setting : PwmSetting.values()) {
                if (!seenSettings.contains(setting)) {
                    LOGGER.info("missing setting key while parsing input configuration: " + setting.getKey() + ", will use default value");
                }
            }

        } catch (Exception e) {
            throw new Exception("Error reading configuration file format: " + e.getMessage());
        }

        LOGGER.debug("successfully loaded configuration with " + newConfiguration.settingMap.size() + " setting values, epoch " + newConfiguration.readProperty(StoredConfiguration.PROPERTY_KEY_CONFIG_EPOCH));
        return newConfiguration;
    }


    public String toString() {
        return toString(false);
    }

    public String toString(final PwmSetting setting) {
        final StringBuilder outputString = new StringBuilder();
        outputString.append(setting.getKey()).append("=");
        if (setting.isConfidential()) {
            outputString.append("**removed**");
        } else {
            outputString.append(settingMap.get(setting));
        }
        return outputString.toString();
    }

    public String toString(final boolean linebreaks) {
        //organize into default / non-default sets.
        final Set<PwmSetting> defaultSet = new HashSet<PwmSetting>(Arrays.asList(PwmSetting.values()));
        final Set<PwmSetting> modifiedSet = new HashSet<PwmSetting>(defaultSet);
        for (final Iterator<PwmSetting> settingIter = modifiedSet.iterator(); settingIter.hasNext();) {
            if (!isDefaultValue(settingIter.next())) {
                settingIter.remove();
            }
        }
        defaultSet.removeAll(modifiedSet);

        final StringBuilder outputString = new StringBuilder();

        outputString.append("modified=[");
        for (final Iterator<PwmSetting> settingIter = modifiedSet.iterator(); settingIter.hasNext();) {
            final PwmSetting setting = settingIter.next();
            outputString.append(toString(setting));
            outputString.append(settingIter.hasNext() ? linebreaks ? "\n" : ", " : "");
        }
        outputString.append("] default=[");
        for (final Iterator<PwmSetting> settingIter = defaultSet.iterator(); settingIter.hasNext();) {
            final PwmSetting setting = settingIter.next();
            outputString.append(toString(setting));
            outputString.append(settingIter.hasNext() ? linebreaks ? "\n" : ", " : "");
        }
        outputString.append("]");

        return outputString.toString();
    }

    public String settingChecksum() throws IOException {

        final StringBuilder sb = new StringBuilder();
        sb.append("PwmSettingsChecksum");

        for (final PwmSetting loopSetting : PwmSetting.values()) {
            if (!isDefaultValue(loopSetting)) {
                sb.append(loopSetting.getKey());
                sb.append("=");
                sb.append(settingMap.get(loopSetting));
            }
        }

        sb.append(modifyTime);
        sb.append(createTime);

        final InputStream is = new ByteArrayInputStream(sb.toString().getBytes("UTF-8"));
        return Helper.md5sum(is);
    }

    public List<String> validateValues() {

        final List<String> errorStrings = new ArrayList<String>();

        for (final PwmSetting loopSetting : PwmSetting.values()) {
            final StringBuilder errorString = new StringBuilder();
            errorString.append(loopSetting.getCategory().getLabel(Locale.getDefault()));
            errorString.append("-");
            errorString.append(loopSetting.getLabel(Locale.getDefault()));
            errorString.append(" ");

            final Pattern loopPattern = loopSetting.getRegExPattern();

            switch (loopSetting.getSyntax()) {
                case NUMERIC: {
                    final String value = this.readSetting(loopSetting);
                    if ((value == null || value.length() < 1) && loopSetting.isRequired()) {
                        errorString.append("missing required value");
                        errorStrings.add(errorString.toString());
                    } else {
                        try {
                            Long.parseLong(value);
                        } catch (Exception e) {
                            errorString.append("can not parse numeric value:").append(e.getMessage());
                            errorStrings.add(errorString.toString());
                        }
                    }
                }
                break;

                case BOOLEAN: {
                    final String value = this.readSetting(loopSetting);
                    if ((value == null || value.length() < 1) && loopSetting.isRequired()) {
                        errorString.append("missing required value");
                        errorStrings.add(errorString.toString());
                    } else {
                        try {
                            Boolean.parseBoolean(value);
                        } catch (Exception e) {
                            errorString.append("can not parse boolean value:").append(e.getMessage());
                            errorStrings.add(errorString.toString());
                        }
                    }
                }
                break;

                case STRING_ARRAY: {
                    final List<String> values = this.readStringArraySetting(loopSetting);
                    for (final String value : values) {
                        if ((value == null || value.length() < 1) && loopSetting.isRequired()) {
                            errorString.append("missing required value");
                            errorStrings.add(errorString.toString());
                        } else {
                            final Matcher matcher = loopPattern.matcher(value);
                            if (value != null && value.length() > 0 && !matcher.matches()) {
                                errorString.append("incorrect value format for value: ").append(value);
                                errorStrings.add(errorString.toString());
                            }
                        }
                    }
                }
                break;

                case LOCALIZED_STRING:
                case LOCALIZED_TEXT_AREA: {
                    final Map<String, String> values = this.readLocalizedStringSetting(loopSetting);
                    for (final String locale : values.keySet()) {
                        final String value = values.get(locale);
                        if ((value == null || value.length() < 1) && loopSetting.isRequired()) {
                            errorString.append("missing required value");
                            errorStrings.add(errorString.toString());
                        } else {
                            if (loopSetting.getSyntax() == PwmSetting.Syntax.LOCALIZED_STRING) {
                                final Matcher matcher = loopPattern.matcher(value);
                                if (value != null && value.length() > 0 && !matcher.matches()) {
                                    errorString.append("incorrect value format for locale '").append(locale).append("': ").append(value);
                                    errorStrings.add(errorString.toString());
                                }
                            }
                        }
                    }
                }
                break;

                case LOCALIZED_STRING_ARRAY: {
                    final Map<String, List<String>> values = this.readLocalizedStringArraySetting(loopSetting);
                    for (final String locale : values.keySet()) {
                        for (final String value : values.get(locale)) {
                            if ((value == null || value.length() < 1) && loopSetting.isRequired()) {
                                errorString.append("missing required value");
                                errorStrings.add(errorString.toString());
                            } else if (value != null) {
                                final Matcher matcher = loopPattern.matcher(value);
                                if (value != null && value.length() > 0 && !matcher.matches()) {
                                    errorString.append("incorrect value format for locale '").append(locale).append("': ").append(value);
                                    errorStrings.add(errorString.toString());
                                }
                            }
                        }
                    }
                }
                break;

                default: {
                    final String value = readSetting(loopSetting);
                    if ((value == null || value.length() < 1) && loopSetting.isRequired()) {
                        errorString.append("missing required value");
                        errorStrings.add(errorString.toString());
                    } else {
                        final Matcher matcher = loopPattern.matcher(value);
                        if (value != null && value.length() > 0 && !matcher.matches()) {
                            errorString.append("incorrect value format for value: '").append(value);
                            errorStrings.add(errorString.toString());
                        }
                    }
                }
            }
        }

        return errorStrings;
    }

    private void preModifyActions() {
        if (locked) {
            throw new UnsupportedOperationException("StoredConfiguration is locked and cannot be modifed");
        }
        modifyTime = new Date();
    }

    private static class TextConversations {
        private static String encryptValue(final String value, final String key)
                throws NoSuchAlgorithmException, IOException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException {
            if (value == null || value.length() < 1) {
                return "";
            }

            final SecretKey sks = makeKey(key);
            final Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, sks, cipher.getParameters());
            final byte[] encrypted = cipher.doFinal(value.getBytes());
            return Base64Util.encodeBytes(encrypted);
        }

        private static String decryptValue(final String value, final String key)
                throws NoSuchAlgorithmException, IOException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
            if (value == null || value.length() < 1) {
                return "";
            }

            final SecretKey sks = makeKey(key);
            final byte[] decoded = Base64Util.decode(value);
            final Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, sks);
            final byte[] decrypted = cipher.doFinal(decoded);
            return new String(decrypted);
        }

        private static SecretKey makeKey(final String text)
                throws NoSuchAlgorithmException, UnsupportedEncodingException {
            final MessageDigest md = MessageDigest.getInstance("SHA1");
            md.update(text.getBytes("iso-8859-1"), 0, text.length());
            final byte[] key = new byte[16];
            System.arraycopy(md.digest(), 0, key, 0, 16);
            return new SecretKeySpec(key, "AES");
        }
    }


    private interface StoredValue extends Serializable {
        String toJsonString();

        List<Element> toXmlValues(final String valueElementName);

        Object toNativeObject();

        static class StoredValueString implements StoredValue {
            final String value;

            public StoredValueString(final String value) {
                this.value = value;
            }

            public static StoredValueString fromJsonString(final String input) {
                return new StoredValueString(input);
            }

            public String toJsonString() {
                return value == null ? "" : value;
            }

            public List<Element> toXmlValues(final String valueElementName) {
                final Element valueElement = new Element(valueElementName);
                valueElement.addContent(new CDATA(value));
                return Collections.singletonList(valueElement);
            }

            public String toNativeObject() {
                return value;
            }

            public String toString() {
                return toJsonString();
            }
        }

        static class StoredValueLocaleList implements StoredValue {
            final Map<String, String> values;

            public StoredValueLocaleList(final Map<String, String> values) {
                this.values = values;
            }

            public static StoredValueLocaleList fromJsonString(final String input) {
                if (input == null) {
                    return new StoredValueLocaleList(Collections.<String, String>emptyMap());
                }

                final Gson gson = new Gson();
                final Map<String, String> srcMap = gson.fromJson(input, new TypeToken<Map<String, String>>() {
                }.getType());
                final Map<String, String> returnMap = new TreeMap<String, String>(srcMap);
                return new StoredValueLocaleList(returnMap);
            }

            public String toJsonString() {
                final Gson gson = new Gson();
                if (values == null) {
                    return gson.toJson(Collections.emptyMap());
                }
                return gson.toJson(values);
            }

            public List<Element> toXmlValues(final String valueElementName) {
                final List<Element> returnList = new ArrayList<Element>();
                for (final String locale : values.keySet()) {
                    final String value = values.get(locale);
                    final Element valueElement = new Element(valueElementName);
                    valueElement.addContent(new CDATA(value));
                    if (locale != null && locale.length() > 0) {
                        valueElement.setAttribute("locale", locale);
                    }
                    returnList.add(valueElement);
                }
                return returnList;
            }

            public Map<String, String> toNativeObject() {
                return Collections.unmodifiableMap(values);
            }

            public String toString() {
                return toJsonString();
            }
        }

        static class StoredValueList implements StoredValue {
            final List<String> values;

            public StoredValueList(final List<String> values) {
                this.values = values;
            }

            public static StoredValueList fromJsonString(final String input) {
                if (input == null) {
                    return new StoredValueList(Collections.<String>emptyList());
                }

                final Gson gson = new Gson();
                final List<String> srcList = gson.fromJson(input, new TypeToken<List<String>>() {
                }.getType());
                return new StoredValueList(srcList);
            }

            public String toJsonString() {
                final Gson gson = new Gson();
                if (values == null) {
                    return gson.toJson(Collections.emptyList());
                }
                return gson.toJson(values);
            }

            public List<Element> toXmlValues(final String valueElementName) {
                final List<Element> returnList = new ArrayList<Element>();
                for (final String value : values) {
                    final Element valueElement = new Element(valueElementName);
                    valueElement.addContent(new CDATA(value));
                    returnList.add(valueElement);
                }
                return returnList;
            }

            public List<String> toNativeObject() {
                return Collections.unmodifiableList(values);
            }

            public String toString() {
                return toJsonString();
            }
        }

        static class StoredValueLocaleMap implements StoredValue {
            final Map<String, List<String>> values;

            public StoredValueLocaleMap(final Map<String, List<String>> values) {
                this.values = values;
            }

            public static StoredValueLocaleMap fromJsonString(final String input) {
                if (input == null) {
                    return new StoredValueLocaleMap(Collections.<String, List<String>>emptyMap());
                }

                final Gson gson = new Gson();
                final Map<String, List<String>> srcMap = gson.fromJson(input, new TypeToken<Map<String, List<String>>>() {
                }.getType());
                final Map<String, List<String>> returnMap = new TreeMap<String, List<String>>(srcMap);
                return new StoredValueLocaleMap(returnMap);
            }

            public String toJsonString() {
                final Gson gson = new Gson();
                if (values == null) {
                    return gson.toJson(Collections.emptyMap());
                }
                return gson.toJson(values);
            }

            public List<Element> toXmlValues(final String valueElementName) {
                final List<Element> returnList = new ArrayList<Element>();
                for (final String locale : values.keySet()) {
                    for (final String value : values.get(locale)) {
                        final Element valueElement = new Element(valueElementName);
                        valueElement.addContent(new CDATA(value));
                        if (locale != null && locale.length() > 0) {
                            valueElement.setAttribute("locale", locale);
                        }
                        returnList.add(valueElement);
                    }
                }
                return returnList;
            }

            public Map<String, List<String>> toNativeObject() {
                return Collections.unmodifiableMap(values);
            }

            public String toString() {
                return toJsonString();
            }
        }

        static class StoredValuePassword extends StoredValueString {

            public StoredValuePassword(final String value) {
                super(value);
            }

            public static StoredValueString fromJsonString(final String input) {
                return new StoredValuePassword(input);
            }

            public List<Element> toXmlValues(final String valueElementName) {
                throw new IllegalStateException("password xml output requires hash key");
            }

            public List<Element> toXmlValues(final String valueElementName, final String key) {
                if (value == null || value.length() < 1) {
                    final Element valueElement = new Element(valueElementName);
                    return Collections.singletonList(valueElement);
                }
                final Element valueElement = new Element(valueElementName);
                try {
                    final String encodedValue = TextConversations.encryptValue(value, key);
                    valueElement.addContent(encodedValue);
                } catch (Exception e) {
                    valueElement.addContent("");
                    throw new RuntimeException("missing required AES and SHA1 libraries, or other crypto fault: " + e.getMessage());
                }
                return Collections.singletonList(valueElement);
            }

            public String toString() {
                return "***removed***";
            }
        }
    }
}
