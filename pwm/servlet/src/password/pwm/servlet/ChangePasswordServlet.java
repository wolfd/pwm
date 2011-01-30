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

package password.pwm.servlet;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.*;
import password.pwm.bean.ChangePasswordBean;
import password.pwm.bean.SessionStateBean;
import password.pwm.config.Message;
import password.pwm.config.PwmPasswordRule;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.ValidationException;
import password.pwm.util.PwmLogger;
import password.pwm.util.RandomPasswordGenerator;
import password.pwm.util.stats.Statistic;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * User interaction servlet for changing (self) passwords.
 *
 * @author Jason D. Rivard.
 */
public class ChangePasswordServlet extends TopServlet {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(ChangePasswordServlet.class);

    public static final int MAX_CACHE_SIZE = 50;
    private static final int DEFAULT_INPUT_LENGTH = 1024;

// -------------------------- OTHER METHODS --------------------------

    public void processRequest(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ServletException, IOException, ChaiUnavailableException, PwmException {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        final ChangePasswordBean cpb = pwmSession.getChangePasswordBean();

        final String processRequestParam = Validator.readStringFromRequest(req, PwmConstants.PARAM_ACTION_REQUEST, DEFAULT_INPUT_LENGTH);

        if (!ssBean.isAuthenticated()) {
            ssBean.setSessionError(new ErrorInformation(PwmError.ERROR_AUTHENTICATION_REQUIRED));
            Helper.forwardToErrorPage(req, resp, this.getServletContext());
            return;
        }

        if (pwmSession.getConfig().readSettingAsBoolean(PwmSetting.PASSWORD_REQUIRE_CURRENT)) {
            if (!pwmSession.getUserInfoBean().isAuthFromUnknownPw()) {
                cpb.setCurrentPasswordRequired(true);
            }
        }

        if (!Permission.checkPermission(Permission.CHANGE_PASSWORD, pwmSession)) {
            ssBean.setSessionError(new ErrorInformation(PwmError.ERROR_UNAUTHORIZED));
            Helper.forwardToErrorPage(req, resp, this.getServletContext());
            return;
        }

        if (processRequestParam != null) {
            if (processRequestParam.equalsIgnoreCase("validate")) {
                handleValidatePasswords(req, resp);
                return;
            } else if (processRequestParam.equalsIgnoreCase("getrandom")) {     // ajax random generator
                handleGetRandom(req, resp);
                return;
            } else if (processRequestParam.equalsIgnoreCase("change")) {        // change request
                this.handleChangeRequest(req, resp);
            } else if (processRequestParam.equalsIgnoreCase("doChange")) {      // wait page call-back
                this.handleDoChangeRequest(req, resp);
            } else if (processRequestParam.equalsIgnoreCase("agree")) {         // accept password change agreement
                LOGGER.debug(pwmSession, "user accepted password change agreement");
                cpb.setAgreementPassed(true);
            } else {
                if (cpb.getPasswordChangeError() != null) {
                    ssBean.setSessionError(cpb.getPasswordChangeError());
                    cpb.setPasswordChangeError(null);
                }
            }
        }

        if (!resp.isCommitted()) {
            this.forwardToJSP(req, resp);
        }
    }

    /**
     * Write the pwm password pre-validation response.  A JSON formatted string is returned to the
     * client containing information about a password's validation against the user's policy.
     * <pre>pwm:[status]:[pre-localized error/success message]</pre>
     *
     * @param req  request
     * @param resp response
     * @throws IOException      for an error
     * @throws ServletException for an error
     * @throws com.novell.ldapchai.exception.ChaiUnavailableException
     *                          if ldap server becomes unavailable
     * @throws password.pwm.error.PwmException
     *                          if an unexpected error occurs
     */
    protected static void handleValidatePasswords(final HttpServletRequest req, final HttpServletResponse resp)
            throws IOException, ServletException, PwmException, ChaiUnavailableException {
        final long startTime = System.currentTimeMillis();
        Validator.validatePwmFormID(req);
        final PwmSession pwmSession = PwmSession.getPwmSession(req);

        final String bodyString = Helper.readRequestBody(req, 10 * 1024);
        final Gson gson = new Gson();
        final Map<String, String> srcMap = gson.fromJson(bodyString, new TypeToken<Map<String, String>>() {
        }.getType());
        final String password1 = srcMap.get("password1") != null ? srcMap.get("password1") : "";
        final String password2 = srcMap.get("password2") != null ? srcMap.get("password2") : "";

        final Map<String, PasswordCheckInfo> cache = pwmSession.getChangePasswordBean().getPasswordTestCache();
        final boolean foundInCache = cache.containsKey(password1);
        final PasswordCheckInfo passwordCheckInfo = foundInCache ? cache.get(password1) : checkEnteredPassword(pwmSession, password1);
        final MATCH_STATUS matchStatus = figureMatchStatus(pwmSession, password1, password2);
        cache.put(password1, passwordCheckInfo); //update the cache

        final String outputString = generateJsonOutputString(pwmSession, passwordCheckInfo, matchStatus);

        {
            final StringBuilder sb = new StringBuilder();
            sb.append("real-time password validator called for ").append(pwmSession.getUserInfoBean().getUserDN());
            sb.append("\n");
            sb.append("  process time: ").append((int) (System.currentTimeMillis() - startTime)).append("ms");
            sb.append(", cached: ").append(foundInCache);
            sb.append(", cacheSize: ").append(cache.size());
            sb.append(", pass: ").append(passwordCheckInfo.isPassed());
            sb.append(", confirm: ").append(matchStatus);
            sb.append(", strength: ").append(passwordCheckInfo.getStrength());
            if (!passwordCheckInfo.isPassed()) {
                sb.append(", err: ").append(passwordCheckInfo.getUserStr());
            }
            sb.append("\n");
            sb.append("  passwordCheckInfo string: ").append(outputString);
            LOGGER.trace(pwmSession, sb.toString());
        }

        pwmSession.getContextManager().getStatisticsManager().incrementValue(Statistic.PASSWORD_RULE_CHECKS);

        resp.setContentType("application/json;charset=utf-8");
        resp.getWriter().print(outputString);
    }

    private static PasswordCheckInfo checkEnteredPassword(
            final PwmSession pwmSession,
            final String password1
    )
            throws PwmException, ChaiUnavailableException {
        boolean pass = false;
        String userMessage;

        if (password1.length() < 0) {
            userMessage = new ErrorInformation(PwmError.PASSWORD_MISSING).toUserStr(pwmSession);
        } else {
            try {
                Validator.testPasswordAgainstPolicy(password1, pwmSession, true);
                userMessage = new ErrorInformation(PwmError.PASSWORD_MEETS_RULES).toUserStr(pwmSession);
                pass = true;
            } catch (ValidationException e) {
                userMessage = e.getError().toUserStr(pwmSession);
                pass = false;
            }
        }

        final int strength = PasswordUtility.checkPasswordStrength(pwmSession.getConfig(), pwmSession, password1);
        return new PasswordCheckInfo(userMessage, pass, strength);
    }

    private static MATCH_STATUS figureMatchStatus(final PwmSession session, final String password1, final String password2) {
        final MATCH_STATUS matchStatus;
        if (password2.length() < 1) {
            matchStatus = MATCH_STATUS.EMPTY;
        } else {
            if (session.getUserInfoBean().getPasswordPolicy().getRuleHelper().readBooleanValue(PwmPasswordRule.CaseSensitive)) {
                matchStatus = password1.equals(password2) ? MATCH_STATUS.MATCH : MATCH_STATUS.NO_MATCH;
            } else {
                matchStatus = password1.equalsIgnoreCase(password2) ? MATCH_STATUS.MATCH : MATCH_STATUS.NO_MATCH;
            }
        }

        return matchStatus;
    }

    private static String generateJsonOutputString(
            final PwmSession pwmSession,
            final PasswordCheckInfo checkInfo,
            final MATCH_STATUS matchStatus
    ) {
        final String userMessage;
        if (checkInfo.isPassed()) {
            switch (matchStatus) {
                case EMPTY:
                    userMessage = new ErrorInformation(PwmError.PASSWORD_MISSING_CONFIRM).toUserStr(pwmSession);
                    break;
                case MATCH:
                    userMessage = new ErrorInformation(PwmError.PASSWORD_MEETS_RULES).toUserStr(pwmSession);
                    break;
                case NO_MATCH:
                    userMessage = new ErrorInformation(PwmError.PASSWORD_DOESNOTMATCH).toUserStr(pwmSession);
                    break;
                default:
                    userMessage = "";
            }
        } else {
            userMessage = checkInfo.getUserStr();
        }

        final Map<String, String> outputMap = new HashMap<String, String>();
        outputMap.put("version", "2");
        outputMap.put("strength", String.valueOf(checkInfo.getStrength()));
        outputMap.put("match", matchStatus.toString());
        outputMap.put("message", userMessage);
        outputMap.put("passed", String.valueOf(checkInfo.isPassed()));

        final Gson gson = new Gson();
        return gson.toJson(outputMap);
    }

    /**
     * Write the pwm password pre-validation response.  A format such as the following is used:
     * <p/>
     * <pre>pwm:[status]:[pre-localized error/success message</pre>
     *
     * @param req  request
     * @param resp response
     * @throws IOException      for an error
     * @throws PwmException     for an error
     * @throws ServletException for an error
     */
    protected static void handleGetRandom(final HttpServletRequest req, final HttpServletResponse resp)
            throws IOException, ServletException, PwmException {
        final long startTime = System.currentTimeMillis();
        Validator.validatePwmFormID(req);
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final String randomPassword = RandomPasswordGenerator.createRandomPassword(pwmSession);

        final Map<String, String> outputMap = new HashMap<String, String>();
        outputMap.put("version", "1");
        outputMap.put("password", randomPassword);

        resp.setContentType("application/json;charset=utf-8");
        final Gson gson = new Gson();
        resp.getOutputStream().print(gson.toJson(outputMap));

        {
            final StringBuilder sb = new StringBuilder();
            sb.append("real-time random password generator called");
            sb.append(" (").append((int) (System.currentTimeMillis() - startTime)).append("ms");
            sb.append(")");
            pwmSession.getContextManager().getStatisticsManager().incrementValue(Statistic.GENERATED_PASSWORDS);
            LOGGER.trace(pwmSession, sb.toString());
        }
    }

    /**
     * Action handler for when user clicks "change password" button.  This copies the
     * password into the changepasswordbean, redirects to the please wait screen, then
     * directs back to the actual doChange.
     *
     * @param req  http request
     * @param resp http response
     * @throws ServletException should never throw
     * @throws IOException      if error writing response
     * @throws com.novell.ldapchai.exception.ChaiUnavailableException
     *                          if ldap server becomes unavailable
     * @throws password.pwm.error.PwmException
     *                          if an unexpected error occurs
     */
    private void handleChangeRequest(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ServletException, IOException, PwmException, ChaiUnavailableException {
        //Fetch the required managers/beans
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        final ChangePasswordBean cpb = pwmSession.getChangePasswordBean();

        Validator.validatePwmFormID(req);
        final String currentPassword = Validator.readStringFromRequest(req, "currentPassword", DEFAULT_INPUT_LENGTH);
        final String password1 = Validator.readStringFromRequest(req, "password1", DEFAULT_INPUT_LENGTH);
        final String password2 = Validator.readStringFromRequest(req, "password2", DEFAULT_INPUT_LENGTH);

        // check the current password
        if (cpb.isCurrentPasswordRequired() && pwmSession.getUserInfoBean().getUserCurrentPassword() != null) {
            if (currentPassword == null || currentPassword.length() < 1) {
                ssBean.setSessionError(new ErrorInformation(PwmError.ERROR_MISSING_PARAMETER));
                LOGGER.debug(pwmSession, "failed password validation check: currentPassword value is missing");
                this.forwardToJSP(req, resp);
                return;
            }

            final boolean caseSensitive = Boolean.parseBoolean(pwmSession.getUserInfoBean().getPasswordPolicy().getValue(PwmPasswordRule.CaseSensitive));
            final boolean passed;
            if (caseSensitive) {
                passed = pwmSession.getUserInfoBean().getUserCurrentPassword().equals(currentPassword);
            } else {
                passed = pwmSession.getUserInfoBean().getUserCurrentPassword().equalsIgnoreCase(currentPassword);
            }

            if (!passed) {
                ssBean.setSessionError(new ErrorInformation(PwmError.ERROR_BAD_CURRENT_PASSWORD));
                pwmSession.getContextManager().getIntruderManager().addBadUserAttempt(pwmSession.getUserInfoBean().getUserDN(), pwmSession);
                LOGGER.debug(pwmSession, "failed password validation check: currentPassword value is incorrect");
                this.forwardToJSP(req, resp);
                return;
            }
        }

        // check the password meets the requirements
        {
            try {
                Validator.testPasswordAgainstPolicy(password1, pwmSession, true);
            } catch (ValidationException e) {
                ssBean.setSessionError(e.getError());
                LOGGER.debug(pwmSession, "failed password validation check: " + e.getError().toDebugStr());
                this.forwardToJSP(req, resp);
                return;
            }
        }

        //make sure the two passwords match
        if (MATCH_STATUS.MATCH != figureMatchStatus(pwmSession, password1, password2)) {
            ssBean.setSessionError(new ErrorInformation(PwmError.PASSWORD_DOESNOTMATCH));
            this.forwardToJSP(req, resp);
            return;
        }

        // password accepted, setup change password
        {
            cpb.setNewPassword(password1);
            LOGGER.trace(pwmSession, "wrote password to changePasswordBean");

            final StringBuilder returnURL = new StringBuilder();
            returnURL.append(req.getContextPath());
            returnURL.append(req.getServletPath());
            returnURL.append("?" + PwmConstants.PARAM_ACTION_REQUEST + "=" + "doChange");
            Helper.forwardToWaitPage(req, resp, this.getServletContext(), returnURL.toString());
        }
    }

    /**
     * Handles the actual change password request.  This action is called via a redirect
     * from the "Please Wait" screen.
     *
     * @param req  http request
     * @param resp http response
     * @throws ServletException         should never throw
     * @throws IOException              if error writing response
     * @throws ChaiUnavailableException if ldap disappears
     * @throws password.pwm.error.PwmException
     *                                  if there is an unexpected error setting password
     */
    private void handleDoChangeRequest(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ServletException, IOException, ChaiUnavailableException, PwmException {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        final ContextManager theManager = pwmSession.getContextManager();

        final ChangePasswordBean cpb = pwmSession.getChangePasswordBean();
        final String newPassword = cpb.getNewPassword();

        if (newPassword == null || newPassword.length() < 1) {
            LOGGER.warn(pwmSession, "entered doChange, but bean does not have a valid password stored in server session");
            cpb.clearPassword();
            return;
        }

        LOGGER.trace(pwmSession, "retrieved password from server session");

        final boolean success = PasswordUtility.setUserPassword(pwmSession, newPassword);

        if (success) {
            if (theManager.getConfig().readSettingAsBoolean(PwmSetting.LOGOUT_AFTER_PASSWORD_CHANGE)) {
                ssBean.setFinishAction(SessionStateBean.FINISH_ACTION.LOGOUT);
            }

            ssBean.setSessionSuccess(Message.SUCCESS_PASSWORDCHANGE);

            UserHistory.updateUserHistory(pwmSession, UserHistory.Record.Event.CHANGE_PASSWORD, null);

            Helper.forwardToSuccessPage(req, resp, this.getServletContext());
        } else {
            final ErrorInformation errorMsg = ssBean.getSessionError();
            if (errorMsg != null) { // add the bad password to the history cache
                cpb.getPasswordTestCache().put(newPassword, new PasswordCheckInfo(
                        errorMsg.toUserStr(pwmSession),
                        false,
                        PasswordUtility.checkPasswordStrength(pwmSession.getConfig(), pwmSession, newPassword)
                ));
            }
            cpb.setPasswordChangeError(errorMsg);
            resp.sendRedirect(SessionFilter.rewriteRedirectURL(PwmConstants.URL_SERVLET_CHANGE_PASSWORD, req, resp));
        }

        cpb.clearPassword();
    }

    private void forwardToJSP(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final Locale userLocale = pwmSession.getSessionStateBean().getLocale();
        final String agreementMsg = pwmSession.getConfig().readLocalizedStringSetting(PwmSetting.PASSWORD_CHANGE_AGREEMENT_MESSAGE, userLocale);
        final ChangePasswordBean cpb = pwmSession.getChangePasswordBean();

        if (agreementMsg != null && agreementMsg.length() > 0 && !cpb.isAgreementPassed()) {
            this.getServletContext().getRequestDispatcher('/' + PwmConstants.URL_JSP_PASSWORD_AGREEMENT).forward(req, resp);
        } else {
            this.getServletContext().getRequestDispatcher('/' + PwmConstants.URL_JSP_PASSWORD_CHANGE).forward(req, resp);
        }
    }

    private enum MATCH_STATUS {
        MATCH, NO_MATCH, EMPTY
    }

    public static class PasswordCheckInfo implements Serializable {
        private final String userStr;
        private final boolean passed;
        private final int strength;

        public PasswordCheckInfo(final String userStr, final boolean passed, final int strength) {
            this.userStr = userStr;
            this.passed = passed;
            this.strength = strength;
        }

        public String getUserStr() {
            return userStr;
        }

        public boolean isPassed() {
            return passed;
        }

        public int getStrength() {
            return strength;
        }
    }
}

