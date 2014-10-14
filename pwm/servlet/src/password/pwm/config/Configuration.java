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

package password.pwm.config;

import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.util.StringHelper;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.UserIdentity;
import password.pwm.config.option.*;
import password.pwm.config.policy.HelpdeskProfile;
import password.pwm.config.policy.PwmPasswordPolicy;
import password.pwm.config.policy.PwmPasswordRule;
import password.pwm.config.value.*;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.i18n.LocaleHelper;
import password.pwm.util.PasswordData;
import password.pwm.util.SecureHelper;
import password.pwm.util.logging.PwmLogLevel;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.operations.PasswordUtility;

import javax.crypto.SecretKey;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.security.cert.X509Certificate;
import java.util.*;

/**
 * @author Jason D. Rivard
 */
public class Configuration implements Serializable {
// ------------------------------ FIELDS ------------------------------

    private final static PwmLogger LOGGER = PwmLogger.forClass(Configuration.class);

    private final StoredConfiguration storedConfiguration;

    private long newUserPasswordPolicyCacheTime = System.currentTimeMillis();
    private DataCache dataCache = new DataCache();

    // --------------------------- CONSTRUCTORS ---------------------------

    public Configuration(final StoredConfiguration storedConfiguration) {
        this.storedConfiguration = storedConfiguration;
    }

// ------------------------ CANONICAL METHODS ------------------------

    public String toString() {
        final StringBuilder outputText = new StringBuilder();
        outputText.append("  ");
        outputText.append(storedConfiguration.toString(true));
        return outputText.toString().replaceAll("\n","\n  ");
    }

    public String toString(final PwmSetting pwmSetting) {
        return this.storedConfiguration.readSetting(pwmSetting).toDebugString(false,PwmConstants.DEFAULT_LOCALE);
    }

// -------------------------- OTHER METHODS --------------------------

    public List<FormConfiguration> readSettingAsForm(final PwmSetting setting) {
        if (PwmSettingSyntax.FORM != setting.getSyntax()) {
            throw new IllegalArgumentException("may not read FORM value for setting: " + setting.toString());
        }

        final StoredValue value = readStoredValue(setting);
        return (List<FormConfiguration>)value.toNativeObject();
    }

    public List<UserPermission> readSettingAsUserPermission(final PwmSetting setting) {
        if (PwmSettingSyntax.USER_PERMISSION != setting.getSyntax()) {
            throw new IllegalArgumentException("may not read FORM value for setting: " + setting.toString());
        }

        final StoredValue value = readStoredValue(setting);
        return (List<UserPermission>)value.toNativeObject();
    }

    public Map<String,LdapProfile> getLdapProfiles() {
        if (dataCache.ldapProfiles != null) {
            return dataCache.ldapProfiles;
        }

        final List<String> profiles = storedConfiguration.profilesForSetting(PwmSetting.LDAP_PROFILE_LIST);
        final LinkedHashMap<String,LdapProfile> returnList = new LinkedHashMap<>();
        for (String profileID : profiles) {
            if ("".equals(profileID)) {
                profileID = PwmConstants.PROFILE_ID_DEFAULT;
            }
            final LdapProfile ldapProfile = LdapProfile.makeFromStoredConfiguration(this.storedConfiguration, profileID);
            if (ldapProfile.readSettingAsBoolean(PwmSetting.LDAP_PROFILE_ENABLED)) {
                returnList.put(profileID, ldapProfile);
            }
        }

        dataCache.ldapProfiles = Collections.unmodifiableMap(returnList);
        return dataCache.ldapProfiles;
    }

    public EmailItemBean readSettingAsEmail(final PwmSetting setting, final Locale locale) {
        if (PwmSettingSyntax.EMAIL != setting.getSyntax()) {
            throw new IllegalArgumentException("may not read EMAIL value for setting: " + setting.toString());
        }

        final Map<String, EmailItemBean> storedValues = (Map<String, EmailItemBean>)readStoredValue(setting).toNativeObject();
        final Map<Locale, EmailItemBean> availableLocaleMap = new LinkedHashMap<>();
        for (final String localeStr : storedValues.keySet()) {
            availableLocaleMap.put(LocaleHelper.parseLocaleString(localeStr), storedValues.get(localeStr));
        }
        final Locale matchedLocale = LocaleHelper.localeResolver(locale, availableLocaleMap.keySet());

        return availableLocaleMap.get(matchedLocale);
    }

    public <E extends Enum<E>> E readSettingAsEnum(PwmSetting setting,  Class<E> enumClass) {
        /*
        if (PwmSettingSyntax.SELECT!= setting.getSyntax()) {
            throw new IllegalArgumentException("may not read ACTION value for setting: " + setting.toString());
        }
        */

        final StoredValue value = readStoredValue(setting);
        return JavaTypeConverter.valueToEnum(value, enumClass, setting);
    }


    public <E extends Enum<E>> Set<E> readSettingAsOptionList(final PwmSetting setting,  Class<E> enumClass) {
        if (PwmSettingSyntax.OPTIONLIST != setting.getSyntax()) {
            throw new IllegalArgumentException("may not read OPTIONLIST value for setting: " + setting.toString());
        }

        final StoredValue value = readStoredValue(setting);
        return JavaTypeConverter.valueToOptionList(value, enumClass);
    }




    public MessageSendMethod readSettingAsTokenSendMethod(final PwmSetting setting) {
        return MessageSendMethod.valueOf(readSettingAsString(setting));
    }


    public List<ActionConfiguration> readSettingAsAction(final PwmSetting setting) {
        if (PwmSettingSyntax.ACTION != setting.getSyntax()) {
            throw new IllegalArgumentException("may not read ACTION value for setting: " + setting.toString());
        }

        final StoredValue value = readStoredValue(setting);
        return (List<ActionConfiguration>)value.toNativeObject();
    }

    public List<String> readSettingAsLocalizedStringArray(final PwmSetting setting, final Locale locale) {
        if (PwmSettingSyntax.LOCALIZED_STRING_ARRAY != setting.getSyntax()) {
            throw new IllegalArgumentException("may not read LOCALIZED_STRING_ARRAY value for setting: " + setting.toString());
        }

        final StoredValue value = readStoredValue(setting);
        return JavaTypeConverter.valueToLocalizedStringArray(value, locale);
    }

    public String readSettingAsString(final PwmSetting setting) {
        return JavaTypeConverter.valueToString(readStoredValue(setting));
    }

    public PasswordData readSettingAsPassword(final PwmSetting setting)
    {
        return JavaTypeConverter.valueToPassword(readStoredValue(setting));
    }

    static abstract class JavaTypeConverter {
        static String valueToString(final StoredValue value) {
            if (value == null) return null;
            if ((!(value instanceof StringValue)) && (!(value instanceof BooleanValue))) {
                throw new IllegalArgumentException("setting value is not readable as string");
            }
            final Object nativeObject = value.toNativeObject();
            if (nativeObject == null) return null;
            return nativeObject.toString();
        }

        static PasswordData valueToPassword(final StoredValue value) {
            if (value == null) return null;
            if ((!(value instanceof PasswordValue))) {
                throw new IllegalArgumentException("setting value is not readable as password");
            }
            final Object nativeObject = value.toNativeObject();
            if (nativeObject == null) return null;
            return (PasswordData)nativeObject;
        }

        static List<String> valueToStringArray(final StoredValue value) {
            if (!(value instanceof StringArrayValue)) {
                throw new IllegalArgumentException("setting value is not readable as string array");
            }

            final List<String> results = new ArrayList<>((List<String>)value.toNativeObject());
            for (final Iterator iter = results.iterator(); iter.hasNext();) {
                final Object loopString = iter.next();
                if (loopString == null || loopString.toString().length() < 1) {
                    iter.remove();
                }
            }
            return results;
        }

        static boolean valueToBoolean(final StoredValue value) {
            if (!(value instanceof BooleanValue)) {
                throw new IllegalArgumentException("may not read BOOLEAN value for setting");
            }

            return (Boolean)value.toNativeObject();
        }

        static String valueToLocalizedString(final StoredValue value, final Locale locale) {
            if (!(value instanceof LocalizedStringValue)) {
                throw new IllegalArgumentException("may not read LOCALIZED_STRING or LOCALIZED_TEXT_AREA values for setting");
            }

            final Map<String, String> availableValues = (Map<String, String>)value.toNativeObject();
            final Map<Locale, String> availableLocaleMap = new LinkedHashMap<>();
            for (final String localeStr : availableValues.keySet()) {
                availableLocaleMap.put(LocaleHelper.parseLocaleString(localeStr), availableValues.get(localeStr));
            }
            final Locale matchedLocale = LocaleHelper.localeResolver(locale, availableLocaleMap.keySet());

            return availableLocaleMap.get(matchedLocale);
        }

        static List<String> valueToLocalizedStringArray(final StoredValue value, final Locale locale) {
            if (!(value instanceof LocalizedStringArrayValue)) {
                throw new IllegalArgumentException("may not read LOCALIZED_STRING_ARRAY value");
            }
            final Map<String, List<String>> storedValues = (Map<String, List<String>>)value.toNativeObject();
            final Map<Locale, List<String>> availableLocaleMap = new LinkedHashMap<>();
            for (final String localeStr : storedValues.keySet()) {
                availableLocaleMap.put(LocaleHelper.parseLocaleString(localeStr), storedValues.get(localeStr));
            }
            final Locale matchedLocale = LocaleHelper.localeResolver(locale, availableLocaleMap.keySet());

            return availableLocaleMap.get(matchedLocale);
        }

        static <E extends Enum<E>> E valueToEnum(StoredValue value,  Class<E> enumClass, final PwmSetting setting) {
            final String strValue = (String)value.toNativeObject();
            try {
                return (E)enumClass.getMethod("valueOf", String.class).invoke(null, strValue);
            } catch (InvocationTargetException e1) {
                if (e1.getCause() instanceof IllegalArgumentException) {
                    LOGGER.error("illegal setting value for option '" + strValue + "' for setting key '" + setting.getKey() + "' is not recognized, will use default");
                }
            } catch (Exception e1) {
                LOGGER.error("unexpected error", e1);
            }

            return null;
        }

        static <E extends Enum<E>> Set<E> valueToOptionList(final StoredValue value,  Class<E> enumClass) {
            final Set<E> returnSet = new HashSet<>();
            final Set<String> strValues = (Set<String>)value.toNativeObject();
            for (final String strValue : strValues) {
                try {
                    returnSet.add((E)enumClass.getMethod("valueOf", String.class).invoke(null, strValue));
                } catch (InvocationTargetException e1) {
                    if (e1.getCause() instanceof IllegalArgumentException) {
                        LOGGER.error("illegal setting value for option '" + strValue + "' is not recognized, will use default");
                    }
                } catch (Exception e1) {
                    LOGGER.error("unexpected error", e1);
                }
            }

            return Collections.unmodifiableSet(returnSet);
        }
    }

    public Map<Locale,String> readLocalizedBundle(final String className, final String keyName) {
        final String key = className + "-" + keyName;
        if (dataCache.customText.containsKey(key)) {
            return dataCache.customText.get(key);
        }


        final Map<String,String> storedValue = storedConfiguration.readLocaleBundleMap(className,keyName);
        if (storedValue == null || storedValue.isEmpty()) {
            dataCache.customText.put(key,null);
            return null;
        }

        final Map<Locale,String> localizedMap = new LinkedHashMap<>();
        for (final String localeKey : storedValue.keySet()) {
            localizedMap.put(LocaleHelper.parseLocaleString(localeKey),storedValue.get(localeKey));
        }

        dataCache.customText.put(key, localizedMap);
        return localizedMap;
    }

    public PwmLogLevel getEventLogLocalDBLevel() {
        final String value = readSettingAsString(PwmSetting.EVENTS_LOCALDB_LOG_LEVEL);
        for (final PwmLogLevel logLevel : PwmLogLevel.values()) {
            if (logLevel.toString().equalsIgnoreCase(value)) {
                return logLevel;
            }
        }

        return PwmLogLevel.TRACE;
    }

    public List<String> getChallengeProfiles() {
        return storedConfiguration.profilesForSetting(PwmSetting.CHALLENGE_PROFILE_LIST);
    }

    public ChallengeProfile getChallengeProfile(final String profile, final Locale locale) {
        if (!"".equals(profile) && !getChallengeProfiles().contains(profile)) {
            throw new IllegalArgumentException("unknown challenge profileID specified: " + profile);
        }

        if (!dataCache.challengeProfile.containsKey(profile)) {
            dataCache.challengeProfile.put(profile,new HashMap<Locale,ChallengeProfile>());
        }

        if (dataCache.challengeProfile.get(profile).containsKey(locale)) {
            return dataCache.challengeProfile.get(profile).get(locale);
        }

        final ChallengeProfile challengeProfile = new ChallengeProfile(profile,locale,storedConfiguration);
        dataCache.challengeProfile.get(profile).put(locale,challengeProfile);
        return challengeProfile;
    }

    public List<String> getHelpdeskProfiles() {
        return storedConfiguration.profilesForSetting(PwmSetting.HELPDESK_PROFILE_LIST);
    }

    public String determineHelpdeskProfileForUser(
            final PwmApplication pwmApplication,
            final UserIdentity userIdentity
    ) {
        final List<String> profiles = getHelpdeskProfiles();
        if (profiles.isEmpty()) {
            throw new IllegalStateException("no available helpdesk profiles");
        } else if (profiles.size() == 1) {
            LOGGER.trace("only one helpdesk profile defined, returning default");
            return "";
        }

        /*
        for (final String profile : profiles) {
            if (!PwmConstants.DEFAULT_CHALLENGE_PROFILE.equalsIgnoreCase(profile)) {
                final HelpdeskProfile loopPolicy = pwmApplication.getConfig().getHelpdeskProfile(profile);
                final List<UserPermission> queryMatch = loopPolicy();
                if (queryMatch != null && !queryMatch.isEmpty()) {
                    LOGGER.debug("testing challenge profiles '" + profile + "'");
                    try {
                        boolean match = Helper.testUserPermissions(pwmApplication,null,userIdentity,queryMatch);
                        if (match) {
                            return profile;
                        }
                    } catch (PwmUnrecoverableException e) {
                        LOGGER.error("unexpected error while testing password policy profile '" + profile + "', error: " + e.getMessage());
                    }
                }
            }
        }
        */

        return PwmConstants.DEFAULT_CHALLENGE_PROFILE;
    }


    public HelpdeskProfile getHelpdeskProfile(final String profile) {
        if (!"".equals(profile) && !getHelpdeskProfiles().contains(profile)) {
            throw new IllegalArgumentException("unknown helpdesk profileID specified: " + profile);
        }

        if (dataCache.helpdeskProfile.containsKey(profile)) {
            return dataCache.helpdeskProfile.get(profile);
        }

        final HelpdeskProfile helpdeskProfile = HelpdeskProfile.makeFromStoredConfiguration(storedConfiguration, profile);
        dataCache.helpdeskProfile.put(profile,helpdeskProfile);
        return helpdeskProfile;
    }

    public long readSettingAsLong(final PwmSetting setting) {
        if (PwmSettingSyntax.NUMERIC != setting.getSyntax()) {
            throw new IllegalArgumentException("may not read NUMERIC value for setting: " + setting.toString());
        }

        return (Long)readStoredValue(setting).toNativeObject();
    }

    public PwmPasswordPolicy getPasswordPolicy(final String profile, final Locale locale)
    {
        if (dataCache.cachedPasswordPolicy.containsKey(profile) && dataCache.cachedPasswordPolicy.get(profile).containsKey(
                locale)) {
            return dataCache.cachedPasswordPolicy.get(profile).get(locale);
        }

        final PwmPasswordPolicy policy = initPasswordPolicy(profile,locale);
        if (!dataCache.cachedPasswordPolicy.containsKey(profile)) {
            dataCache.cachedPasswordPolicy.put(profile,new HashMap<Locale,PwmPasswordPolicy>());
        }
        dataCache.cachedPasswordPolicy.get(profile).put(locale,policy);
        return policy;
    }

    public List<String> getPasswordProfiles() {
        return storedConfiguration.profilesForSetting(PwmSetting.PASSWORD_PROFILE_LIST);
    }

    protected PwmPasswordPolicy initPasswordPolicy(final String profile, final Locale locale)
    {
        final Map<String, String> passwordPolicySettings = new LinkedHashMap<>();
        for (final PwmPasswordRule rule : PwmPasswordRule.values()) {
            if (rule.getPwmSetting() != null) {
                final String value;
                final PwmSetting pwmSetting = rule.getPwmSetting();
                switch (rule) {
                    case DisallowedAttributes:
                    case DisallowedValues:
                    case CharGroupsValues:
                        value = StringHelper.stringCollectionToString(
                                JavaTypeConverter.valueToStringArray(storedConfiguration.readSetting(pwmSetting,profile)), "\n");
                        break;
                    case RegExMatch:
                    case RegExNoMatch:
                        value = StringHelper.stringCollectionToString(
                                JavaTypeConverter.valueToStringArray(storedConfiguration.readSetting(pwmSetting,
                                        profile)), ";;;");
                        break;
                    case ChangeMessage:
                        value = JavaTypeConverter.valueToLocalizedString(
                                storedConfiguration.readSetting(pwmSetting, profile), locale);
                        break;
                    case ADComplexityLevel:
                        value = JavaTypeConverter.valueToEnum(
                                storedConfiguration.readSetting(pwmSetting,profile),
                                ADPolicyComplexity.class,
                                pwmSetting
                        ).toString();
                        break;
                    default:
                        value = String.valueOf(
                                storedConfiguration.readSetting(pwmSetting, profile).toNativeObject());
                }
                passwordPolicySettings.put(rule.getKey(), value);
            }
        }

        // set case sensitivity
        final String caseSensitivitySetting = JavaTypeConverter.valueToString(storedConfiguration.readSetting(
                PwmSetting.PASSWORD_POLICY_CASE_SENSITIVITY));
        if (!"read".equals(caseSensitivitySetting)) {
            passwordPolicySettings.put(PwmPasswordRule.CaseSensitive.getKey(),caseSensitivitySetting);
        }

        // set pwm-specific values
        final PwmPasswordPolicy passwordPolicy = PwmPasswordPolicy.createPwmPasswordPolicy(passwordPolicySettings);
        passwordPolicy.setProfileID(profile);
        if (!PwmConstants.DEFAULT_PASSWORD_PROFILE.equalsIgnoreCase(profile)) {
            final List<UserPermission> queryMatch = (List<UserPermission>)storedConfiguration.readSetting(PwmSetting.PASSWORD_POLICY_QUERY_MATCH,profile).toNativeObject();
            passwordPolicy.setUserPermissions(queryMatch);
        }
        passwordPolicy.setRuleText(JavaTypeConverter.valueToLocalizedString(storedConfiguration.readSetting(PwmSetting.PASSWORD_POLICY_RULE_TEXT,profile),locale));
        return passwordPolicy;
    }

    public List<String> readSettingAsStringArray(final PwmSetting setting) {
        return JavaTypeConverter.valueToStringArray(readStoredValue(setting));
    }

    public String readSettingAsLocalizedString(final PwmSetting setting, final Locale locale) {
        return JavaTypeConverter.valueToLocalizedString(readStoredValue(setting), locale);
    }

    public static Map<String, String> convertStringListToNameValuePair(final Collection<String> input, final String separator) {
        if (input == null) {
            return Collections.emptyMap();
        }

        final Map<String, String> returnMap = new LinkedHashMap<>();
        for (final String loopStr : input) {
            if (loopStr != null && separator != null && loopStr.contains(separator)) {
                final int seperatorLocation = loopStr.indexOf(separator);
                final String key = loopStr.substring(0, seperatorLocation);
                final String value = loopStr.substring(seperatorLocation + separator.length(), loopStr.length());
                returnMap.put(key, value);
            } else {
                returnMap.put(loopStr, "");
            }
        }

        return returnMap;
    }

    public boolean isDefaultValue(final PwmSetting pwmSetting) {
        return storedConfiguration.isDefaultValue(pwmSetting);
    }

    public Collection<Locale> localesForSetting(final PwmSetting setting) {
        final Collection<Locale> returnCollection = new ArrayList<>();
        switch (setting.getSyntax()) {
            case LOCALIZED_TEXT_AREA:
            case LOCALIZED_STRING:
                for (final String localeStr : ((Map<String, String>)readStoredValue(setting).toNativeObject()).keySet()) {
                    returnCollection.add(LocaleHelper.parseLocaleString(localeStr));
                }
                break;

            case LOCALIZED_STRING_ARRAY:
                for (final String localeStr : ((Map<String, List<String>>)readStoredValue(setting).toNativeObject()).keySet()) {
                    returnCollection.add(LocaleHelper.parseLocaleString(localeStr));
                }
                break;
        }

        return returnCollection;
    }

    public String readProperty(final StoredConfiguration.ConfigProperty key) {
        return storedConfiguration.readConfigProperty(key);
    }

    public boolean readSettingAsBoolean(final PwmSetting setting) {
        return JavaTypeConverter.valueToBoolean(readStoredValue(setting));
    }

    public Map<FileValue.FileInformation,FileValue.FileContent> readSettingAsFile(final PwmSetting setting) {
        FileValue fileValue = (FileValue)storedConfiguration.readSetting(setting);
        return (Map)fileValue.toNativeObject();
    }

    public X509Certificate[] readSettingAsCertificate(final PwmSetting setting) {
        if (PwmSettingSyntax.X509CERT != setting.getSyntax()) {
            throw new IllegalArgumentException("may not read X509CERT value for setting: " + setting.toString());
        }
        if (readStoredValue(setting) == null) {
            return new X509Certificate[0];
        }
        return (X509Certificate[])readStoredValue(setting).toNativeObject();
    }

    public String toDebugString() {
        return storedConfiguration.toString(true);
    }

    public String getNotes() {
        return storedConfiguration.readConfigProperty(StoredConfiguration.ConfigProperty.PROPERTY_KEY_NOTES);
    }

    public SecretKey getSecurityKey() throws PwmUnrecoverableException {
        final PasswordData configValue = readSettingAsPassword(PwmSetting.PWM_SECURITY_KEY);
        if (configValue == null) {
            final String errorMsg = "Security Key value is not configured";
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_INVALID_SECURITY_KEY, errorMsg);
            throw new PwmUnrecoverableException(errorInfo);
        }

        final String rawValue = configValue.getStringValue();
        if (rawValue.length() < 32) {
            final String errorMsg = "Security Key must be greater than 32 characters in length";
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_INVALID_SECURITY_KEY, errorMsg);
            throw new PwmUnrecoverableException(errorInfo);
        }

        try {
            return SecureHelper.makeKey(rawValue);
        } catch (Exception e) {
            final String errorMsg = "unexpected error generating Security Key crypto: " + e.getMessage();
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_INVALID_SECURITY_KEY, errorMsg);
            LOGGER.error(errorInfo.toDebugStr(),e);
            throw new PwmUnrecoverableException(errorInfo);
        }
    }

    public List<DataStorageMethod> getResponseStorageLocations(final PwmSetting setting) {

        return getGenericStorageLocations(setting);
    }

    public List<DataStorageMethod> getOtpSecretStorageLocations(final PwmSetting setting) {
        return getGenericStorageLocations(setting);
    }

    private List<DataStorageMethod> getGenericStorageLocations(final PwmSetting setting) {
        final String input = readSettingAsString(setting);
        final List<DataStorageMethod> storageMethods = new ArrayList<>();
        for (final String rawValue : input.split("-")) {
            try {
                storageMethods.add(DataStorageMethod.valueOf(rawValue));
            } catch (IllegalArgumentException e) {
                LOGGER.error("unknown STORAGE_METHOD found: " + rawValue);
            }
        }
        return storageMethods;
    }

    public PwmPasswordPolicy getNewUserPasswordPolicy(final PwmApplication pwmApplication, final Locale userLocale)
            throws PwmUnrecoverableException, ChaiUnavailableException {

        {
            final long maxNewUserCacheMS = Long.parseLong(pwmApplication.getConfig().readAppProperty(AppProperty.CONFIG_NEWUSER_PASSWORD_POLICY_CACHE_MS));
            if ((System.currentTimeMillis() - newUserPasswordPolicyCacheTime) > maxNewUserCacheMS) {
                dataCache.newUserPasswordPolicy.clear();
            }

            final PwmPasswordPolicy cachedPolicy = dataCache.newUserPasswordPolicy.get(userLocale);
            if (cachedPolicy != null) {
                return cachedPolicy;
            }

            final LdapProfile defaultLdapProfile = getLdapProfiles().get(PwmConstants.PROFILE_ID_DEFAULT);
            final String configuredNewUserPasswordDN = readSettingAsString(PwmSetting.NEWUSER_PASSWORD_POLICY_USER);
            if (configuredNewUserPasswordDN == null || configuredNewUserPasswordDN.length() < 1) {
                final PwmPasswordPolicy thePolicy = getPasswordPolicy(PwmConstants.DEFAULT_PASSWORD_PROFILE, userLocale);
                dataCache.newUserPasswordPolicy.put(userLocale,thePolicy);
                return thePolicy;
            } else {

                final String lookupDN;
                if (configuredNewUserPasswordDN.equalsIgnoreCase("TESTUSER") ) {
                    lookupDN = defaultLdapProfile.readSettingAsString(PwmSetting.LDAP_TEST_USER_DN);
                } else {
                    lookupDN = configuredNewUserPasswordDN;
                }

                final PwmPasswordPolicy thePolicy;
                if (lookupDN == null || lookupDN.isEmpty()) {
                    thePolicy = getPasswordPolicy(PwmConstants.PROFILE_ID_DEFAULT,userLocale);
                } else {
                    final ChaiUser chaiUser = ChaiFactory.createChaiUser(lookupDN, pwmApplication.getProxyChaiProvider(PwmConstants.PROFILE_ID_DEFAULT));
                    final UserIdentity userIdentity = new UserIdentity(lookupDN,PwmConstants.PROFILE_ID_DEFAULT);
                    thePolicy = PasswordUtility.readPasswordPolicyForUser(pwmApplication, null, userIdentity, chaiUser, userLocale);
                }
                dataCache.newUserPasswordPolicy.put(userLocale,thePolicy);
                return thePolicy;
            }
        }
    }

    public List<Locale> getKnownLocales() {
        if (dataCache.localeFlagMap == null) {
            dataCache.localeFlagMap = figureLocaleFlagMap();
        }
        return Collections.unmodifiableList(new ArrayList<>(dataCache.localeFlagMap.keySet()));
    }

    public Map<Locale,String> getKnownLocaleFlagMap() {
        if (dataCache.localeFlagMap == null) {
            dataCache.localeFlagMap = figureLocaleFlagMap();
        }
        return dataCache.localeFlagMap;
    }

    private Map<Locale,String> figureLocaleFlagMap() {
        final String defaultLocaleAsString = PwmConstants.DEFAULT_LOCALE.toString();

        final List<String> inputList = readSettingAsStringArray(PwmSetting.KNOWN_LOCALES);
        final Map<String,String> inputMap = convertStringListToNameValuePair(inputList,"::");

        // Sort the map by display name
        final Map<String,String> sortedMap = new TreeMap<>();
        for (final String localeString : inputMap.keySet()) {
            final Locale theLocale = LocaleHelper.parseLocaleString(localeString);
            if (theLocale != null) {
                sortedMap.put(theLocale.getDisplayName(), localeString);
            }
        }

        final List<String> returnList = new ArrayList<>();

        //ensure default is first.
        returnList.add(defaultLocaleAsString);
        for (final String localeDisplayString : sortedMap.keySet()) {
            final String localeString = sortedMap.get(localeDisplayString);
            if (!defaultLocaleAsString.equals(localeString)) {
                returnList.add(localeString);
            }
        }

        final Map<Locale,String> localeFlagMap = new LinkedHashMap<>();
        for (final String localeString : returnList) {
            final Locale loopLocale = LocaleHelper.parseLocaleString(localeString);
            if (loopLocale != null) {
                final String flagCode = inputMap.containsKey(localeString) ? inputMap.get(localeString) : loopLocale.getCountry();
                localeFlagMap.put(loopLocale, flagCode);
            }
        }
        return Collections.unmodifiableMap(localeFlagMap);
    }

    public RecoveryAction getRecoveryAction() {
        final String stringValue = readSettingAsString(PwmSetting.FORGOTTEN_PASSWORD_ACTION);
        try {
            return RecoveryAction.valueOf(stringValue);
        } catch (IllegalArgumentException e) {
            LOGGER.error("unknown recovery action value: " + stringValue);
            return RecoveryAction.RESETPW;
        }
    }

    public TokenStorageMethod getTokenStorageMethod() {
        try {
            return TokenStorageMethod.valueOf(readSettingAsString(PwmSetting.TOKEN_STORAGEMETHOD));
        } catch (Exception e) {
            final String errorMsg = "unknown storage method specified: " + readSettingAsString(PwmSetting.TOKEN_STORAGEMETHOD);
            ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_INVALID_CONFIG,errorMsg);
            LOGGER.warn(errorInformation.toDebugStr());
            return null;
        }
    }

    public PwmSetting.Template getTemplate() {
        return storedConfiguration.getTemplate();
    }

    public boolean hasDbConfigured() {
        if (readSettingAsString(PwmSetting.DATABASE_CLASS) == null || readSettingAsString(PwmSetting.DATABASE_CLASS).length() < 1) {
            return false;
        }
        if (readSettingAsString(PwmSetting.DATABASE_URL) == null || readSettingAsString(PwmSetting.DATABASE_URL).length() < 1) {
            return false;
        }
        if (readSettingAsString(PwmSetting.DATABASE_USERNAME) == null || readSettingAsString(PwmSetting.DATABASE_USERNAME).length() < 1) {
            return false;
        }
        if (readSettingAsPassword(PwmSetting.DATABASE_PASSWORD) == null) {
            return false;
        }

        return true;
    }

    public String readAppProperty(AppProperty property) {
        final String configValue = storedConfiguration.readAppProperty(property);
        if (configValue != null) {
            return configValue;
        }

        return property.getDefaultValue();
    }

    private Convenience helper = new Convenience();

    public Convenience helper() {
        return helper;
    }

    public class Convenience {
        public List<DataStorageMethod> getCrReadPreference() {
            final List<DataStorageMethod> readPreferences = getResponseStorageLocations(PwmSetting.FORGOTTEN_PASSWORD_READ_PREFERENCE);
            if (readPreferences.size() == 1 && readPreferences.get(0) == DataStorageMethod.AUTO) {
                readPreferences.clear();
                if (hasDbConfigured()) {
                    readPreferences.add(DataStorageMethod.DB);
                } else {
                    readPreferences.add(DataStorageMethod.LDAP);
                }
            }

            final String wsURL = readSettingAsString(PwmSetting.EDIRECTORY_PWD_MGT_WEBSERVICE_URL);
            if (wsURL != null && wsURL.length() > 0) {
                readPreferences.add(DataStorageMethod.NMASUAWS);
            }


            if (readSettingAsBoolean(PwmSetting.EDIRECTORY_USE_NMAS_RESPONSES)) {
                readPreferences.add(DataStorageMethod.NMAS);
            }

            return readPreferences;
        }

        public List<DataStorageMethod> getCrWritePreference() {
            final List<DataStorageMethod> writeMethods = getResponseStorageLocations(PwmSetting.FORGOTTEN_PASSWORD_WRITE_PREFERENCE);
            if (writeMethods.size() == 1 && writeMethods.get(0) == DataStorageMethod.AUTO) {
                writeMethods.clear();
                if (hasDbConfigured()) {
                    writeMethods.add(DataStorageMethod.DB);
                } else {
                    writeMethods.add(DataStorageMethod.LDAP);
                }
            }
            if (readSettingAsBoolean(PwmSetting.EDIRECTORY_STORE_NMAS_RESPONSES)) {
                writeMethods.add(DataStorageMethod.NMAS);
            }
            return writeMethods;
        }

        public boolean shouldHaveDbConfigured() {
            final PwmSetting[] settingsToCheck = new PwmSetting[] {
                    PwmSetting.FORGOTTEN_PASSWORD_READ_PREFERENCE,
                    PwmSetting.FORGOTTEN_PASSWORD_WRITE_PREFERENCE,
                    PwmSetting.INTRUDER_STORAGE_METHOD,
                    PwmSetting.EVENTS_USER_STORAGE_METHOD
            };

            for (final PwmSetting loopSetting : settingsToCheck) {
                if (getResponseStorageLocations(loopSetting).contains(DataStorageMethod.DB)) {
                    return true;
                }
            }
            return false;
        }
    }

    private StoredValue readStoredValue(final PwmSetting setting) {
        if (dataCache.settings.containsKey(setting)) {
            return dataCache.settings.get(setting);
        }

        final StoredValue readValue = storedConfiguration.readSetting(setting);
        dataCache.settings.put(setting, readValue);
        return readValue;
    }

    private static class DataCache implements Serializable {
        private final Map<String,Map<Locale,PwmPasswordPolicy>> cachedPasswordPolicy = new HashMap<>();
        private final Map<String,Map<Locale,ChallengeProfile>> challengeProfile = new HashMap<>();
        private final Map<String,HelpdeskProfile> helpdeskProfile = new HashMap<>();
        private final Map<Locale,PwmPasswordPolicy> newUserPasswordPolicy = new HashMap<>();
        private Map<Locale,String> localeFlagMap = null;
        private Map<String,LdapProfile> ldapProfiles;
        private final Map<PwmSetting, StoredValue> settings = new EnumMap<>(PwmSetting.class);
        private final Map<String,Map<Locale,String>> customText = new HashMap<>();
    }

    public Map<AppProperty,String> readAllNonDefaultAppProperties() {
        final LinkedHashMap<AppProperty,String> nonDefaultProperties = new LinkedHashMap<>();
        for (final AppProperty loopProperty : AppProperty.values()) {
            final String configuredValue = readAppProperty(loopProperty);
            final String defaultValue = loopProperty.getDefaultValue();
            if (configuredValue != null && !configuredValue.equals(defaultValue)) {
                nonDefaultProperties.put(loopProperty,configuredValue);
            }
        }
        return nonDefaultProperties;
    }
}
