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

package password.pwm;

import com.novell.ldapchai.ChaiConstant;
import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.cr.ChallengeSet;
import com.novell.ldapchai.cr.ResponseSet;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.impl.edir.entry.EdirEntries;
import com.novell.ldapchai.provider.ChaiProvider;
import com.novell.ldapchai.util.SearchHelper;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.Configuration;
import password.pwm.config.PasswordStatus;
import password.pwm.config.PwmPasswordRule;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.ValidationException;
import password.pwm.util.Helper;
import password.pwm.util.PwmLogger;
import password.pwm.util.TimeDuration;

import java.util.*;

public class UserStatusHelper {

    private static final PwmLogger LOGGER = PwmLogger.getLogger(UserStatusHelper.class);


    private UserStatusHelper() {
    }


    /**
     * Read password status values from directory
     *
     * @param pwmSession     users pwm Session
     * @param theUser        the user to check
     * @param passwordPolicy the password policy to use for checking if current password violates current policy
     * @return bean describing the status of the user's password
     * @throws com.novell.ldapchai.exception.ChaiUnavailableException
     *          of directory is unavailable
     */
    public static PasswordStatus readPasswordStatus(
            final PwmSession pwmSession,
            final ChaiUser theUser,
            final PwmPasswordPolicy passwordPolicy
    )
            throws ChaiUnavailableException {
        final PasswordStatus returnState = new PasswordStatus();
        final Configuration config = pwmSession.getConfig();
        final String userDN = theUser.getEntryDN();

        final long startTime = System.currentTimeMillis();
        LOGGER.trace(pwmSession, "beginning password status check process for " + userDN);

        // check if password meets existing policy.
        if (passwordPolicy.getRuleHelper().readBooleanValue(PwmPasswordRule.EnforceAtLogin)) {
            final String password = pwmSession.getUserInfoBean().getUserCurrentPassword();
            if (password != null && password.length() > 0) {
                try {
                    Validator.testPasswordAgainstPolicy(password, pwmSession, false, passwordPolicy);
                } catch (ValidationException e) {
                    LOGGER.info(pwmSession, "user " + userDN + " password does not conform to current password policy (" + e.getMessage() + "), marking as requiring change.");
                    returnState.setViolatesPolicy(true);
                } catch (PwmException e) {
                    LOGGER.info(pwmSession, "user " + userDN + " password does not conform to current password policy (" + e.getMessage() + "), marking as requiring change.");
                    returnState.setViolatesPolicy(true);
                }
            }
        }

        try {
            final boolean ldapPasswordExpired = theUser.isPasswordExpired();

            if (ldapPasswordExpired) {
                returnState.setExpired(true);
                LOGGER.trace(pwmSession, "password for " + userDN + " appears to be expired");
            } else {
                LOGGER.trace(pwmSession, "password for " + userDN + " does not appear to be expired");
            }
        } catch (ChaiOperationException e) {
            LOGGER.info(pwmSession, "error reading user attrs for " + userDN + " while reading isPasswordExpired(): " + e.getMessage());
        }

        try {
            final Date ldapPasswordExpirationTime = theUser.readPasswordExpirationDate();

            if (ldapPasswordExpirationTime != null) {
                final long diff = ldapPasswordExpirationTime.getTime() - System.currentTimeMillis();

                // now check to see if the user's expire time is within the 'preExpireTime' setting.
                final long preExpireMs = config.readSettingAsLong(PwmSetting.PASSWORD_EXPIRE_PRE_TIME) * 1000;
                if (diff < preExpireMs) {
                    LOGGER.info(pwmSession, "user " + userDN + " password will expire within " + TimeDuration.asCompactString(diff) + ", marking as expired");
                    returnState.setPreExpired(true);
                }

                // now check to see if the user's expire time is within the 'preWarnTime' setting.
                final long preWarnMs = config.readSettingAsLong(PwmSetting.PASSWORD_EXPIRE_WARN_TIME) * 1000;
                if (diff < preWarnMs) {
                    LOGGER.info(pwmSession, "user " + userDN + " password will expire within " + TimeDuration.asCompactString(diff) + ", marking as warn");
                    returnState.setWarnPeriod(true);
                }
            }

        } catch (ChaiOperationException e) {
            LOGGER.info(pwmSession, "error reading user attrs for " + userDN + " while reading passwordExpirationDate(): " + e.getMessage());
        }

        LOGGER.debug(pwmSession, "completed user password status check for " + userDN + " " + returnState + " (" + TimeDuration.fromCurrent(startTime).asCompactString() + ")");
        return returnState;
    }

    public static boolean checkIfResponseConfigNeeded(final PwmSession pwmSession, final ChaiUser theUser, final ChallengeSet challengeSet)
            throws ChaiUnavailableException, PwmException {
        if (!pwmSession.getSessionStateBean().isAuthenticated()) {
            throw PwmException.createPwmException(new ErrorInformation(PwmError.ERROR_AUTHENTICATION_REQUIRED, "user must be authenticated to check if response configuration is needed"));
        }

        LOGGER.trace(pwmSession, "beginning check to determine if responses need to be configured for user");

        final String userDN = theUser.getEntryDN();

        if (!Helper.testUserMatchQueryString(pwmSession, userDN, pwmSession.getConfig().readSettingAsString(PwmSetting.QUERY_MATCH_CHECK_RESPONSES))) {
            LOGGER.debug(pwmSession, "checkIfResponseConfigNeeded: " + userDN + " is not eligible for checkIfResponseConfigNeeded due to query match");
            return false;
        }

        // check to be sure there are actually challenges in the challenge set
        if (challengeSet == null || challengeSet.getChallenges().isEmpty()) {
            LOGGER.debug(pwmSession, "checkIfResponseConfigNeeded: no challenge sets configured for user " + userDN);
            return false;
        }

        // read the user's response
        final ResponseSet usersResponses = CrUtility.readUserResponseSet(pwmSession, theUser);

        try {
            // check if responses exist
            if (usersResponses == null) {
                throw new Exception("no responses configured");
            }

            // check if responses meet the challenge set policy for the user
            usersResponses.meetsChallengeSetRequirements(challengeSet);

            LOGGER.debug(pwmSession, "checkIfResponseConfigNeeded: " + userDN + " has good responses");
            return false;
        } catch (Exception e) {
            LOGGER.debug(pwmSession, "checkIfResponseConfigNeeded: " + userDN + " does not have good responses: " + e.getMessage());
            return true;
        }
    }


    public static void populateActorUserInfoBean(
            final PwmSession pwmSession,
            final String userDN,
            final String userCurrentPassword
    )
            throws ChaiUnavailableException, PwmException {
        final ChaiProvider provider = pwmSession.getSessionManager().getChaiProvider();
        final UserInfoBean uiBean = pwmSession.getUserInfoBean();
        populateUserInfoBean(uiBean, pwmSession, userDN, userCurrentPassword, provider);
    }


    public static void populateUserInfoBean(
            final UserInfoBean uiBean,
            final PwmSession pwmSession,
            final String userDN,
            final String userCurrentPassword,
            final ChaiProvider provider
    )
            throws ChaiUnavailableException, PwmException {
        final long methodStartTime = System.currentTimeMillis();

        if (userDN != null && userDN.length() < 1) {
            throw new NullPointerException("userDN can not be null");
        }

        if (userCurrentPassword != null && userCurrentPassword.length() < 1) {
            throw new NullPointerException("userCurrentPassword can not be null");
        }

        uiBean.setUserCurrentPassword(userCurrentPassword);

        final ChaiUser theUser = ChaiFactory.createChaiUser(userDN, provider);

        try {
            uiBean.setUserDN(theUser.readCanonicalDN());
        } catch (ChaiOperationException e) {
            LOGGER.warn("error reading canonical DN" + e);
            uiBean.setUserDN(userDN);
        }

        //populate password policy
        uiBean.setPasswordPolicy(PwmPasswordPolicy.createPwmPasswordPolicy(pwmSession, theUser));

        //populate c/r challenge set. 
        uiBean.setChallengeSet(CrUtility.readUserChallengeSet(pwmSession, theUser, uiBean.getPasswordPolicy(), pwmSession.getSessionStateBean().getLocale()));

        //populate all user attributes.
        try {
            final Set<String> interestingUserAttributes = new HashSet<String>(pwmSession.getConfig().getAllUsedLdapAttributes());
            interestingUserAttributes.addAll(uiBean.getPasswordPolicy().getRuleHelper().getDisallowedAttributes());
            interestingUserAttributes.add(ChaiConstant.ATTR_LDAP_PASSWORD_EXPIRE_TIME);
            interestingUserAttributes.add(pwmSession.getContextManager().getConfig().readSettingAsString(PwmSetting.LDAP_NAMING_ATTRIBUTE));
            interestingUserAttributes.add(pwmSession.getContextManager().getConfig().readSettingAsString(PwmSetting.LDAP_GUID_ATTRIBUTE));
            if (uiBean.getPasswordPolicy().getRuleHelper().readBooleanValue(PwmPasswordRule.ADComplexity)) {
                interestingUserAttributes.add("sAMAccountName");
                interestingUserAttributes.add("displayName");
                interestingUserAttributes.add("fullname");
                interestingUserAttributes.add("cn");
            }
            final Properties allUserAttrs = theUser.readStringAttributes(interestingUserAttributes);
            uiBean.setAllUserAttributes(allUserAttrs);
        } catch (ChaiOperationException e) {
            LOGGER.warn("error retrieving user attributes " + e);
        }

        // set userID
        final String ldapNamingAttribute = pwmSession.getConfig().readSettingAsString(PwmSetting.LDAP_NAMING_ATTRIBUTE);
        uiBean.setUserID(uiBean.getAllUserAttributes().getProperty(ldapNamingAttribute));

        // set guid
        final String userGuidAttribute = pwmSession.getConfig().readSettingAsString(PwmSetting.LDAP_GUID_ATTRIBUTE);
        uiBean.setUserGuid(uiBean.getAllUserAttributes().getProperty(userGuidAttribute));

        // set email address
        final String ldapEmailAttribute = pwmSession.getConfig().readSettingAsString(PwmSetting.EMAIL_USER_MAIL_ATTRIBUTE);
        uiBean.setUserEmailAddress(uiBean.getAllUserAttributes().getProperty(ldapEmailAttribute));

        // write password state
        uiBean.setPasswordState(readPasswordStatus(pwmSession, theUser, uiBean.getPasswordPolicy()));

        final String userPasswordExpireTime = uiBean.getAllUserAttributes().getProperty(ChaiConstant.ATTR_LDAP_PASSWORD_EXPIRE_TIME, "");
        if (userPasswordExpireTime.length() > 0) {
            uiBean.setPasswordExpirationTime(EdirEntries.convertZuluToDate(userPasswordExpireTime));
        } else {
            uiBean.setPasswordExpirationTime(null);
        }

        // write response state
        uiBean.setRequiresResponseConfig(checkIfResponseConfigNeeded(pwmSession, theUser, pwmSession.getUserInfoBean().getChallengeSet()));

        // fetch last password modification time;
        final String pwdLastModifiedStr = uiBean.getAllUserAttributes().getProperty(pwmSession.getConfig().readSettingAsString(PwmSetting.PASSWORD_LAST_UPDATE_ATTRIBUTE), "");
        if (pwdLastModifiedStr.length() > 0) {
            try {
                uiBean.setPasswordLastModifiedTime(EdirEntries.convertZuluToDate(pwdLastModifiedStr));
            } catch (Exception e) {
                LOGGER.error(pwmSession, "error parsing password last modified value: " + e.getMessage());
            }
        }

        LOGGER.trace(pwmSession, "populateUserInfoBean for " + userDN + " completed in " + TimeDuration.fromCurrent(methodStartTime).asCompactString());
    }

    /**
     * For a given username, find an appropriate objectDN.  Uses parameters in the PWM
     * configuration to specify how the search should be performed.
     * <p/>
     * If exactly one match is discovered, then that value is returned.  Otherwise if
     * no matches or if multiple matches are discovered then null is returned.  Multiple
     * matches are considered an error condition.
     * <p/>
     * If the username appears to already be a valid DN, then the context search is not performed
     * and instead the username value is returned.
     *
     * @param username   username to search for
     * @param pwmSession for grabbing required beans
     * @param context    specify context to use to search, or null to use pwm configured attribute
     * @return the discovered objectDN of the user, or null if none found.
     * @throws com.novell.ldapchai.exception.ChaiUnavailableException
     *          of directory is unavailable
     */
    public static String convertUsernameFieldtoDN(
            final String username,
            final PwmSession pwmSession,
            final String context
    )
            throws ChaiUnavailableException {
        // if no username supplied, just return empty string
        if (username == null || username.length() < 1) {
            return "";
        }

        {   //if supplied user name starts with username attr assume its the full dn and skip the search
            final String usernameAttribute = pwmSession.getContextManager().getConfig().readSettingAsString(PwmSetting.LDAP_NAMING_ATTRIBUTE);
            if (username.toLowerCase().startsWith(usernameAttribute.toLowerCase() + "=")) {
                LOGGER.trace(pwmSession, "username appears to be a DN; skipping username search");
                return username;
            }
        }

        final String searchDN = determineContextForSearch(pwmSession, context);
        LOGGER.trace(pwmSession, "attempting username search for '" + username + "'" + " in context " + searchDN);

        final SearchHelper searchHelper = new SearchHelper();
        {
            final String filterSetting = pwmSession.getConfig().readSettingAsString(PwmSetting.USERNAME_SEARCH_FILTER);
            final String filter = filterSetting.replace(PwmConstants.VALUE_REPLACEMENT_USERNAME, username);
            searchHelper.setFilter(filter);
            searchHelper.setAttributes("");
            searchHelper.setSearchScope(ChaiProvider.SEARCH_SCOPE.SUBTREE);
        }

        LOGGER.trace(pwmSession, "search for username: " + searchHelper.getFilter() + ", searchDN: " + searchDN);

        try {
            final SessionManager sessionMgr = pwmSession.getSessionManager();
            assert sessionMgr != null;

            final ChaiProvider provider = pwmSession.getContextManager().getProxyChaiProvider();
            assert provider != null;

            final Map<String, Properties> results = provider.search(searchDN, searchHelper);

            if (results == null || results.size() == 0) {
                LOGGER.trace(pwmSession, "no matches found");
                return null;
            } else if (results.size() > 1) {
                LOGGER.trace(pwmSession, "multiple matches found");
                LOGGER.warn(pwmSession, "multiple matches found when doing search for username: " + username);
            } else {
                final String userDN = results.keySet().iterator().next();
                LOGGER.trace(pwmSession, "username match found: " + userDN);
                return userDN;
            }
        } catch (ChaiOperationException e) {
            LOGGER.warn(pwmSession, "error during username search: " + e.getMessage());
        }
        return null;
    }

    public static String determineContextForSearch(final PwmSession pwmSession, final String context) {
        final String configuredLdapContextlessRoot = pwmSession.getConfig().readSettingAsString(PwmSetting.LDAP_CONTEXTLESS_ROOT);
        if (context == null || context.length() < 1) {
            return configuredLdapContextlessRoot;
        }

        // validate if supplied context is configured root
        if (context.equals(configuredLdapContextlessRoot)) {
            return context;
        }

        // see if the baseDN is one of the configured login contexts.
        final Map<String, String> contextsSettings = pwmSession.getConfig().getLoginContexts();
        if (contextsSettings.containsKey(context)) {
            if (contextsSettings.keySet().contains(context)) {
                return context;
            }
        }

        LOGGER.warn(pwmSession, "attempt to use '" + context + "' context for search, but is not a configured context, changing search base to default context");
        return configuredLdapContextlessRoot;
    }
}
