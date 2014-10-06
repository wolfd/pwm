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

package password.pwm.http.filter;

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.SessionStateBean;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.SessionVerificationMode;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.ContextManager;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.http.PwmURL;
import password.pwm.util.*;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.stats.Statistic;

import javax.servlet.*;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

/**
 * This session filter (invoked by the container through the web.xml descriptor) wraps all calls to the
 * servlets in the container.
 * <p/>
 * It is responsible for managing some aspects of the user session and also for enforcing security
 * functionality such as intruder lockout.
 *
 * @author Jason D. Rivard
 */
public class SessionFilter implements Filter {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.forClass(SessionFilter.class);

    public void init(final FilterConfig filterConfig)
            throws ServletException
    {
    }

    public void doFilter(
            final ServletRequest servletRequest,
            final ServletResponse servletResponse,
            final FilterChain filterChain
    )
            throws IOException, ServletException {

        final HttpServletRequest req = (HttpServletRequest) servletRequest;
        final HttpServletResponse resp = (HttpServletResponse) servletResponse;

        try {
            processFilter(req,resp,filterChain);
        } catch (PwmUnrecoverableException e) {
            LOGGER.fatal("unexpected error processing session filter: " + e.getMessage());
            ServletHelper.forwardToErrorPage(req,resp,true);
        }
    }

    private void processFilter(final HttpServletRequest req, final HttpServletResponse resp, final FilterChain filterChain)
            throws PwmUnrecoverableException, IOException, ServletException
    {
        final PwmRequest pwmRequest = PwmRequest.forRequest(req,resp);
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final Configuration config = pwmRequest.getConfig();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();

        ServletHelper.handleRequestInitialization(req, pwmApplication, pwmSession);

        {
            final boolean sendNoise = Boolean.parseBoolean(config.readAppProperty(AppProperty.HTTP_HEADER_SEND_XNOISE));
            if (sendNoise) {
                try {
                    resp.setHeader( "X-" + PwmConstants.PWM_APP_NAME + "-Noise", PwmRandom.getInstance().alphaNumericString(PwmRandom.getInstance().nextInt(100)+10));
                } catch (Exception e) { /* noop */ }
            }
        }

        try {
            ServletHelper.handleRequestSecurityChecks(req, pwmApplication, pwmSession);
        } catch (PwmUnrecoverableException e) {
            LOGGER.error(pwmRequest, e.getErrorInformation());
            pwmRequest.respondWithError(e.getErrorInformation());
            if (PwmError.ERROR_INTRUDER_SESSION != e.getError()) {
                pwmSession.invalidate();
            }
            return;
        }

        // mark last url
        if (!new PwmURL(req).isCommandServletURL()) {
            ssBean.setLastRequestURL(req.getRequestURI());
        }

        // mark last request time.
        ssBean.setSessionLastAccessedTime(new Date());

        // debug the http session headers
        if (!pwmSession.getSessionStateBean().isDebugInitialized()) {
            LOGGER.trace(pwmSession, ServletHelper.debugHttpHeaders(req));
            pwmSession.getSessionStateBean().setDebugInitialized(true);
        }

        // output request information to debug log
        pwmRequest.debugHttpRequestToLog();

        // check the page leave notice
        if (checkPageLeaveNotice(pwmSession, config)) {
            LOGGER.warn("invalidating session due to dirty page leave time greater then configured timeout");
            pwmSession.invalidate();
            resp.sendRedirect(req.getRequestURI());
            return;
        }

        //override session locale due to parameter
        handleLocaleParam(pwmRequest);

        //set the session's theme
        handleThemeParam(pwmRequest);

        // make sure connection is secure.
        if (config.readSettingAsBoolean(PwmSetting.REQUIRE_HTTPS) && !req.isSecure()) {
            pwmRequest.respondWithError(PwmError.ERROR_SECURE_REQUEST_REQUIRED.toInfo());
            return;
        }

        //check for session verification failure
        if (!ssBean.isSessionVerified()) {
            // ignore resource requests
            final SessionVerificationMode mode = config.readSettingAsEnum(PwmSetting.ENABLE_SESSION_VERIFICATION,
                    SessionVerificationMode.class);
            if (mode == SessionVerificationMode.OFF) {
                ssBean.setSessionVerified(true);
            } else {
                if (verifySession(pwmRequest, mode)) {
                    return;
                }
            }
        }
        {
            final String forwardURLParamName = config.readAppProperty(AppProperty.HTTP_PARAM_NAME_FORWARD_URL);
            final String forwardURL = pwmRequest.readParameterAsString(forwardURLParamName);
            if (forwardURL != null && forwardURL.length() > 0) {
                try {
                    Helper.checkUrlAgainstWhitelist(pwmApplication, pwmRequest.getSessionLabel(), forwardURL);
                } catch (PwmOperationalException e) {
                    LOGGER.error(pwmRequest, e.getErrorInformation());
                    pwmRequest.respondWithError(e.getErrorInformation());
                    return;
                }
                ssBean.setForwardURL(forwardURL);
                LOGGER.debug(pwmRequest, "forwardURL parameter detected in request, setting session forward url to " + forwardURL);
            }
        }

        {
            final String logoutURLParamName = config.readAppProperty(AppProperty.HTTP_PARAM_NAME_LOGOUT_URL);
            final String logoutURL = pwmRequest.readParameterAsString(logoutURLParamName);
            if (logoutURL != null && logoutURL.length() > 0) {
                try {
                    Helper.checkUrlAgainstWhitelist(pwmApplication, pwmRequest.getSessionLabel(), logoutURL);
                } catch (PwmOperationalException e) {
                    LOGGER.error(pwmRequest, e.getErrorInformation());
                    pwmRequest.respondWithError(e.getErrorInformation());
                    return;
                }
                ssBean.setLogoutURL(logoutURL);
                LOGGER.debug(pwmRequest, "logoutURL parameter detected in request, setting session logout url to " + logoutURL);
            }
        }

        final String skipCaptcha = pwmRequest.readParameterAsString(PwmConstants.PARAM_SKIP_CAPTCHA);
        if (skipCaptcha != null && skipCaptcha.length() > 0) {
            final String configValue = config.readSettingAsString(PwmSetting.CAPTCHA_SKIP_PARAM);
            if (configValue != null && configValue.equals(skipCaptcha)) {
                LOGGER.trace(pwmSession, "valid skipCaptcha value in request, skipping captcha check for this session");
                ssBean.setPassedCaptcha(true);
            } else {
                LOGGER.error(pwmSession, "skipCaptcha value is in request, however value '" + skipCaptcha + "' does not match configured value");
            }
        }

        if ("true".equalsIgnoreCase(pwmRequest.readParameterAsString(
                pwmRequest.getConfig().readAppProperty(AppProperty.HTTP_PARAM_NAME_PASSWORD_EXPIRED)))) {
            pwmSession.getUserInfoBean().getPasswordState().setExpired(true);
        }

        if (!resp.isCommitted()) {
            resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate, proxy-revalidate");
            ServletHelper.addPwmResponseHeaders(pwmRequest, true);
        }

        try {
            filterChain.doFilter(req, resp);
        } catch (Exception e) {
            LOGGER.warn(pwmSession, "unhandled exception", e);
            throw new ServletException(e);
        } catch (Throwable e) {
            LOGGER.warn(pwmSession, "unhandled exception " + e.getMessage(), e);
            throw new ServletException(e);
        }

        // update last request time.
        ssBean.setSessionLastAccessedTime(new Date());

        if (pwmApplication.getStatisticsManager() != null) {
            pwmApplication.getStatisticsManager().incrementValue(Statistic.HTTP_REQUESTS);
        }
    }

    public void destroy() {
    }

    public static String rewriteURL(final String url, final ServletRequest request, final ServletResponse response) throws PwmUnrecoverableException {
        if (urlSessionsAllowed(request) && url != null) {
            final HttpServletResponse resp = (HttpServletResponse) response;
            final String newURL = resp.encodeURL(resp.encodeURL(url));
            if (!url.equals(newURL)) {
                final PwmSession pwmSession = PwmSession.getPwmSession((HttpServletRequest) request);
                LOGGER.trace(pwmSession, "rewriting URL from '" + url + "' to '" + newURL + "'");
            }
            return newURL;
        } else {
            return url;
        }
    }

    public static String rewriteRedirectURL(final String url, final ServletRequest request, final ServletResponse response) throws PwmUnrecoverableException {
        if (urlSessionsAllowed(request) && url != null) {
            final HttpServletResponse resp = (HttpServletResponse) response;
            final String newURL = resp.encodeRedirectURL(resp.encodeURL(url));
            if (!url.equals(newURL)) {
                final PwmSession pwmSession = PwmSession.getPwmSession((HttpServletRequest) request);
                LOGGER.trace(pwmSession, "rewriting redirect URL from '" + url + "' to '" + newURL + "'");
            }
            return newURL;
        } else {
            return url;
        }
    }

    private static boolean urlSessionsAllowed(final ServletRequest request) throws PwmUnrecoverableException {
        final HttpServletRequest req = (HttpServletRequest) request;
        final PwmApplication theManager = ContextManager.getPwmApplication(req);
        return theManager != null && theManager.getConfig() != null && theManager.getConfig().readSettingAsBoolean(PwmSetting.ALLOW_URL_SESSIONS);
    }

    /**
     * Attempt to determine if user agent is able to track sessions (either via url rewriting or cookies).
     */
    private static boolean verifySession(
            final PwmRequest pwmRequest,
            final SessionVerificationMode mode
    )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        final SessionStateBean ssBean = pwmRequest.getPwmSession().getSessionStateBean();
        final HttpServletRequest req = pwmRequest.getHttpServletRequest();
        final HttpServletResponse resp = pwmRequest.getHttpServletResponse();

        if (pwmRequest.getURL().isCommandServletURL()) {
            return false;
        }

        final String keyFromRequest = pwmRequest.readParameterAsString(PwmConstants.PARAM_VERIFICATION_KEY);

        // request doesn't have key, so make a new one, store it in the session, and redirect back here with the new key.
        if (keyFromRequest == null || keyFromRequest.length() < 1) {

            final String returnURL = figureValidationURL(req, ssBean.getSessionVerificationKey());

            LOGGER.trace(pwmRequest, "session has not been validated, redirecting with verification key to " + returnURL);

            resp.setHeader("Connection","close");  // better chance of detecting un-sticky sessions this way
            if (mode == SessionVerificationMode.VERIFY_AND_CACHE) {
                req.setAttribute("Location",returnURL);
                ServletHelper.forwardToJsp(req, resp, PwmConstants.JSP_URL.INIT);
            } else {
                resp.sendRedirect(SessionFilter.rewriteRedirectURL(returnURL, req, resp));
            }
            return true;
        }

        // else, request has a key, so investigate.
        if (keyFromRequest.equals(ssBean.getSessionVerificationKey())) {
            final String returnURL = figureValidationURL(req, null);

            // session looks, good, mark it as such and return;
            LOGGER.trace(pwmRequest, "session validated, redirecting to original request url: " + returnURL);
            ssBean.setSessionVerified(true);
            resp.sendRedirect(SessionFilter.rewriteRedirectURL(returnURL, req, resp));
            return true;
        }

        // user's session is messed up.  send to error page.
        final String errorMsg = "client unable to reply with session key";
        final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_BAD_SESSION,errorMsg);
        LOGGER.error(pwmRequest, errorInformation);
        pwmRequest.respondWithError(errorInformation, true);
        return true;
    }

    private static String figureValidationURL(final HttpServletRequest req, final String validationKey) {
        final StringBuilder sb = new StringBuilder();
        sb.append(req.getRequestURL());
        if (!req.getParameterMap().isEmpty()) {
            sb.append("?");
        }
        for (final Enumeration paramEnum = req.getParameterNames(); paramEnum.hasMoreElements(); ) {
            final String paramName = (String)paramEnum.nextElement();
            if (!PwmConstants.PARAM_VERIFICATION_KEY.equals(paramName)) {
                final List<String> paramValues = Arrays.asList(req.getParameterValues(paramName));

                for (final Iterator<String> valueIter = paramValues.iterator(); valueIter.hasNext();) {
                    final String value = valueIter.next();
                    sb.append(paramName).append("=");
                    sb.append(StringUtil.urlEncode(value));
                    if (valueIter.hasNext()) {
                        sb.append("&");
                    }
                }
                if (paramEnum.hasMoreElements()) {
                    sb.append("&");
                }
            }
        }

        if (validationKey != null) {
            sb.append(sb.toString().contains("?") ? "&" : "?");
            sb.append(PwmConstants.PARAM_VERIFICATION_KEY).append("=").append(validationKey);
        }

        return sb.toString();
    }


    private static boolean checkPageLeaveNotice(final PwmSession pwmSession, final Configuration config) {
        final long configuredSeconds = config.readSettingAsLong(PwmSetting.SECURITY_PAGE_LEAVE_NOTICE_TIMEOUT);
        if (configuredSeconds <= 0) {
            return false;
        }

        final Date currentPageLeaveNotice = pwmSession.getSessionStateBean().getPageLeaveNoticeTime();
        pwmSession.getSessionStateBean().setPageLeaveNoticeTime(null);
        if (currentPageLeaveNotice == null) {
            return false;
        }

        if (TimeDuration.fromCurrent(currentPageLeaveNotice).getTotalSeconds() <= configuredSeconds) {
            return false;
        }

        return true;
    }

    private static void handleLocaleParam(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException
    {
        final Configuration config = pwmRequest.getConfig();
        final String localeParamName = config.readAppProperty(AppProperty.HTTP_PARAM_NAME_LOCALE);
        final String localeCookieName = config.readAppProperty(AppProperty.HTTP_COOKIE_LOCALE_NAME);
        final String requestedLocale = pwmRequest.readParameterAsString(localeParamName);
        if (requestedLocale != null && requestedLocale.length() > 0) {
            LOGGER.debug(pwmRequest, "detected locale request parameter " + localeParamName+ " with value " + requestedLocale);
            if (pwmRequest.getPwmSession().setLocale(pwmRequest.getPwmApplication(), requestedLocale)) {
                final Cookie newCookie = new Cookie(localeCookieName, requestedLocale);
                newCookie.setMaxAge(Integer.parseInt(config.readAppProperty(AppProperty.HTTP_COOKIE_LOCALE_AGE)));
                newCookie.setPath(pwmRequest.getContextPath() + "/");
                pwmRequest.getHttpServletResponse().addCookie(newCookie);
            }
        }
    }

    private static void handleThemeParam(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException
    {
        final Configuration config = pwmRequest.getConfig();
        final String themeParameterName = config.readAppProperty(AppProperty.HTTP_PARAM_NAME_THEME);
        final String themeReqParameter = pwmRequest.readParameterAsString(themeParameterName);
        if (themeReqParameter != null && !themeReqParameter.isEmpty()) {
            pwmRequest.getPwmSession().getSessionStateBean().setTheme(themeReqParameter);
            final String themeCookieName = config.readAppProperty(AppProperty.HTTP_COOKIE_THEME_NAME);
            if (themeCookieName != null && themeCookieName.length() > 0) {
                final Cookie newCookie = new Cookie(themeCookieName, themeReqParameter);
                newCookie.setMaxAge(Integer.parseInt(config.readAppProperty(AppProperty.HTTP_COOKIE_THEME_AGE)));
                newCookie.setPath(pwmRequest.getContextPath() + "/");
                final String configuredTheme = config.readSettingAsString(PwmSetting.INTERFACE_THEME);
                if (configuredTheme != null && configuredTheme.equalsIgnoreCase(themeReqParameter)) {
                    newCookie.setMaxAge(0);
                }
                pwmRequest.getHttpServletResponse().addCookie(newCookie);
            }
        }

    }

}
