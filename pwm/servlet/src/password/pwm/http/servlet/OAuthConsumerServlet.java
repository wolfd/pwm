/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
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

package password.pwm.http.servlet;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.http.bean.LoginInfoBean;
import password.pwm.http.client.PwmHttpClient;
import password.pwm.ldap.auth.AuthenticationType;
import password.pwm.ldap.auth.SessionAuthenticator;
import password.pwm.util.*;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class OAuthConsumerServlet extends PwmServlet {
    private static final PwmLogger LOGGER = PwmLogger.forClass(OAuthConsumerServlet.class);

    @Override
    protected ProcessAction readProcessAction(PwmRequest request)
            throws PwmUnrecoverableException
    {
        return null;
    }

    @Override
    protected void processAction(final PwmRequest pwmRequest)
            throws ServletException, IOException, ChaiUnavailableException, PwmUnrecoverableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final Configuration config = pwmApplication.getConfig();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final Settings settings = Settings.fromConfiguration(pwmApplication.getConfig());

        if (!pwmSession.getSessionStateBean().isOauthInProgress()) {
            final String errorMsg = "oauth consumer reached, but oauth authentication has not yet been initiated.";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_OAUTH_ERROR,errorMsg);
            LOGGER.error(pwmSession, errorMsg);
            pwmRequest.respondWithError(errorInformation);
            return;
        }

        final String oauthRequestError = pwmRequest.readParameterAsString("error");
        if (oauthRequestError != null && !oauthRequestError.isEmpty()) {
            final String errorMsg = "error detected from oauth request parameter: " + oauthRequestError;
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_OAUTH_ERROR,errorMsg,"Remote Error: " + oauthRequestError,null);
            LOGGER.error(pwmSession, errorMsg);
            pwmRequest.respondWithError(errorInformation);
            return;
        }

        // mark the inprogress flag to false, if we read this far and fail user needs to start over.
        pwmSession.getSessionStateBean().setOauthInProgress(false);

        final String requestStateStr = pwmRequest.readParameterAsString(
                config.readAppProperty(AppProperty.HTTP_PARAM_OAUTH_STATE));
        if (requestStateStr == null || requestStateStr.isEmpty()) {
            final String errorMsg = "state parameter is missing from oauth request";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_OAUTH_ERROR,errorMsg);
            LOGGER.error(pwmSession,errorMsg);
            pwmRequest.respondWithError(errorInformation);
            return;
        } else if (!requestStateStr.equals(pwmSession.getSessionStateBean().getSessionVerificationKey())) {
            final String errorMsg = "state value does not match current session key value";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_OAUTH_ERROR,errorMsg);
            LOGGER.error(pwmSession,errorMsg);
            pwmRequest.respondWithError(errorInformation);
            return;
        }

        final String requestCodeStr = pwmRequest.readParameterAsString(config.readAppProperty(AppProperty.HTTP_PARAM_OAUTH_CODE));
        LOGGER.trace(pwmRequest, "received code from oauth server: " + requestCodeStr);

        final OAuthResolveResults resolveResults;
        {
            resolveResults = makeOAuthResolveRequest(pwmRequest, settings, requestCodeStr);
            if (resolveResults == null || resolveResults.getAccessToken() == null || resolveResults.getAccessToken().isEmpty()) {
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_OAUTH_ERROR,"OAuth server did not respond with an access token");
                LOGGER.error(pwmRequest, errorInformation);
                pwmRequest.respondWithError(errorInformation);
                return;
            }

            if (resolveResults.getExpiresSeconds() > 0) {
                if (resolveResults.getRefreshToken() == null || resolveResults.getRefreshToken().isEmpty()) {
                    final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_OAUTH_ERROR,"OAuth server gave expiration for access token, but did not provide a refresh token");
                    LOGGER.error(pwmRequest, errorInformation);
                    pwmRequest.respondWithError(errorInformation);
                    return;
                }
            }
        }

        final String oauthSuppliedUsername;
        {
            final String getAttributeResponseBodyStr = makeOAuthGetAttributeRequest(pwmRequest, resolveResults.getAccessToken(), settings);
            final Map<String, String> getAttributeResultValues = JsonUtil.deserializeStringMap(getAttributeResponseBodyStr);
            oauthSuppliedUsername = getAttributeResultValues.get(settings.getDnAttributeName());
            if (oauthSuppliedUsername == null || oauthSuppliedUsername.isEmpty()) {
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_OAUTH_ERROR,"OAuth server did not respond with an username attribute value");
                LOGGER.error(pwmRequest, errorInformation);
                pwmRequest.respondWithError(errorInformation);
                return;
            }
        }

        LOGGER.debug(pwmSession, "received user login id value from OAuth server: " + oauthSuppliedUsername);

        try {
            final SessionAuthenticator sessionAuthenticator = new SessionAuthenticator(pwmApplication, pwmSession);
            sessionAuthenticator.authUserWithUnknownPassword(oauthSuppliedUsername, AuthenticationType.AUTH_WITHOUT_PASSWORD);

            if (resolveResults.getExpiresSeconds() > 0) {
                final LoginInfoBean loginInfoBean = pwmRequest.getPwmSession().getLoginInfoBean();

                final Date accessTokenExpirationDate = new Date(System.currentTimeMillis() + 1000 * resolveResults.getExpiresSeconds());
                LOGGER.trace(pwmRequest, "noted oauth access token expiration at timestamp " + PwmConstants.DEFAULT_DATETIME_FORMAT.format(accessTokenExpirationDate));
                loginInfoBean.setOauthExpiration(accessTokenExpirationDate);
                loginInfoBean.setOauthRefreshToken(resolveResults.getRefreshToken());
            }

            // recycle the session to prevent session fixation attack.
            pwmRequest.getPwmSession().getSessionStateBean().setSessionIdRecycleNeeded(true);

            // see if there is a an original request url
            pwmRequest.sendRedirectToPreLoginUrl();
        } catch (ChaiUnavailableException e) {
            final String errorMsg = "unable to reach ldap server during OAuth authentication attempt: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_DIRECTORY_UNAVAILABLE, errorMsg);
            LOGGER.error(pwmSession, errorInformation);
            pwmRequest.respondWithError(errorInformation);
            return;
        } catch (PwmException e) {
            final String errorMsg = "error during OAuth authentication attempt: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_OAUTH_ERROR, errorMsg);
            LOGGER.error(pwmSession, errorInformation);
            pwmRequest.respondWithError(errorInformation);
            return;
        }

        LOGGER.trace(pwmSession, "OAuth login sequence successfully completed");
    }

    private static OAuthResolveResults makeOAuthResolveRequest(
            final PwmRequest pwmRequest,
            final Settings settings,
            final String requestCode
    )
            throws IOException, PwmUnrecoverableException
    {
        final Configuration config = pwmRequest.getConfig();
        final String requestUrl = settings.getCodeResolveUrl();
        final String grant_type = config.readAppProperty(AppProperty.OAUTH_ID_ACCESS_GRANT_TYPE);
        final String redirect_uri = figureOauthSelfEndPointUrl(pwmRequest);
        final String clientID = settings.getClientID();

        final Map<String,String> requestParams = new HashMap<>();
        requestParams.put(config.readAppProperty(AppProperty.HTTP_PARAM_OAUTH_CODE),requestCode);
        requestParams.put(config.readAppProperty(AppProperty.HTTP_PARAM_OAUTH_GRANT_TYPE),grant_type);
        requestParams.put(config.readAppProperty(AppProperty.HTTP_PARAM_OAUTH_REDIRECT_URI), redirect_uri);
        requestParams.put(config.readAppProperty(AppProperty.HTTP_PARAM_OAUTH_CLIENT_ID), clientID);

        final RestResults restResults = makeHttpRequest(pwmRequest, "OAuth code resolver", settings, requestUrl, requestParams);
        final HttpResponse httpResponse = restResults.getHttpResponse();

        if (httpResponse.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
            throw new PwmUnrecoverableException(new ErrorInformation(
                    PwmError.ERROR_OAUTH_ERROR,
                    "unexpected HTTP status code (" + httpResponse.getStatusLine().getStatusCode() + ")"
            ));
        }

        final String resolveResponseBodyStr = restResults.getResponseBody();

        final Map<String, String> resolveResultValues = JsonUtil.deserializeStringMap(resolveResponseBodyStr);
        final OAuthResolveResults oAuthResolveResults = new OAuthResolveResults();

        oAuthResolveResults.setAccessToken(resolveResultValues.get(config.readAppProperty(AppProperty.HTTP_PARAM_OAUTH_ACCESS_TOKEN)));
        oAuthResolveResults.setRefreshToken(resolveResultValues.get(config.readAppProperty(AppProperty.HTTP_PARAM_OAUTH_REFRESH_TOKEN)));
        oAuthResolveResults.setExpiresSeconds(0);
        try {
            oAuthResolveResults.setExpiresSeconds(Integer.parseInt(resolveResultValues.get(config.readAppProperty(AppProperty.HTTP_PARAM_OAUTH_EXPIRES))));
        } catch (Exception e) {
            LOGGER.warn(pwmRequest, "error parsing oauth expires value in resolve request: " + e.getMessage());
        }

        return oAuthResolveResults;
    }

    public static boolean checkOAuthExpiration(
            final PwmRequest pwmRequest
    )
    {
        if (!Boolean.parseBoolean(pwmRequest.getConfig().readAppProperty(AppProperty.OAUTH_ENABLE_TOKEN_REFRESH))) {
            return false;
        }

        final LoginInfoBean loginInfoBean = pwmRequest.getPwmSession().getLoginInfoBean();
        final Date expirationDate = loginInfoBean.getOauthExpiration();

        if (expirationDate == null || (new Date()).before(expirationDate)) {
            //not expired
            return false;
        }

        LOGGER.trace(pwmRequest, "oauth access token has expired, attempting to refresh");

        final Settings settings = Settings.fromConfiguration(pwmRequest.getConfig());
        try {
            OAuthResolveResults resolveResults = makeOAuthRefreshRequest(pwmRequest, settings,
                    loginInfoBean.getOauthRefreshToken());
            if (resolveResults != null) {
                if (resolveResults.getExpiresSeconds() > 0) {
                    final Date accessTokenExpirationDate = new Date(System.currentTimeMillis() + 1000 * resolveResults.getExpiresSeconds());
                    LOGGER.trace(pwmRequest, "noted oauth access token expiration at timestamp " + PwmConstants.DEFAULT_DATETIME_FORMAT.format(accessTokenExpirationDate));
                    loginInfoBean.setOauthExpiration(accessTokenExpirationDate);
                    loginInfoBean.setOauthRefreshToken(resolveResults.getRefreshToken());
                    return false;
                }
            }
        } catch (PwmUnrecoverableException | IOException e) {
            LOGGER.error(pwmRequest, "error while processing oauth token refresh:" + e.getMessage());
        }
        LOGGER.error(pwmRequest, "unable to refresh oauth token for user, unauthenticated session");
        pwmRequest.getPwmSession().unauthenticateUser();
        return true;
    }

    private static OAuthResolveResults makeOAuthRefreshRequest(
            final PwmRequest pwmRequest,
            final Settings settings,
            final String refreshCode
    )
            throws IOException, PwmUnrecoverableException
    {
        final Configuration config = pwmRequest.getConfig();
        final String requestUrl = settings.getCodeResolveUrl();
        final String grant_type = config.readAppProperty(AppProperty.OAUTH_ID_REFRESH_GRANT_TYPE);

        final Map<String,String> requestParams = new HashMap<>();
        requestParams.put(config.readAppProperty(AppProperty.HTTP_PARAM_OAUTH_REFRESH_TOKEN),refreshCode);
        requestParams.put(config.readAppProperty(AppProperty.HTTP_PARAM_OAUTH_GRANT_TYPE),grant_type);

        final RestResults restResults = makeHttpRequest(pwmRequest, "OAuth refresh resolver", settings, requestUrl, requestParams);
        final HttpResponse httpResponse = restResults.getHttpResponse();

        if (httpResponse.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
            throw new PwmUnrecoverableException(new ErrorInformation(
                    PwmError.ERROR_OAUTH_ERROR,
                    "unexpected HTTP status code (" + httpResponse.getStatusLine().getStatusCode() + ")"
            ));
        }

        final String resolveResponseBodyStr = restResults.getResponseBody();

        final Map<String, String> resolveResultValues = JsonUtil.deserializeStringMap(resolveResponseBodyStr);
        final OAuthResolveResults oAuthResolveResults = new OAuthResolveResults();

        oAuthResolveResults.setAccessToken(resolveResultValues.get(config.readAppProperty(AppProperty.HTTP_PARAM_OAUTH_ACCESS_TOKEN)));
        oAuthResolveResults.setRefreshToken(refreshCode);
        oAuthResolveResults.setExpiresSeconds(0);
        try {
            oAuthResolveResults.setExpiresSeconds(Integer.parseInt(resolveResultValues.get(config.readAppProperty(AppProperty.HTTP_PARAM_OAUTH_EXPIRES))));
        } catch (Exception e) {
            LOGGER.warn(pwmRequest, "error parsing oauth expires value in resolve request: " + e.getMessage());
        }

        return oAuthResolveResults;
    }

    private static String makeOAuthGetAttributeRequest(
            final PwmRequest pwmRequest,
            final String accessToken,
            final Settings settings
    )
            throws IOException, PwmUnrecoverableException
    {
        final Configuration config = pwmRequest.getConfig();
        final String requestUrl = settings.getAttributesUrl();
        final Map<String,String> requestParams = new HashMap<>();
        requestParams.put(config.readAppProperty(AppProperty.HTTP_PARAM_OAUTH_ACCESS_TOKEN),accessToken);
        requestParams.put(config.readAppProperty(AppProperty.HTTP_PARAM_OAUTH_ATTRIBUTES),settings.getDnAttributeName());

        final RestResults restResults = makeHttpRequest(pwmRequest, "OAuth getattribute", settings, requestUrl, requestParams);
        final HttpResponse httpResponse = restResults.getHttpResponse();

        if (httpResponse.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
            throw new PwmUnrecoverableException(new ErrorInformation(
                    PwmError.ERROR_OAUTH_ERROR,
                    "unexpected HTTP status code (" + httpResponse.getStatusLine().getStatusCode() + ")"
            ));
        }

        return restResults.getResponseBody();
    }

    private static RestResults makeHttpRequest(
            final PwmRequest pwmRequest,
            final String debugText,
            final Settings settings,
            final String requestUrl,
            final Map<String,String> requestParams
    )
            throws IOException, PwmUnrecoverableException
    {
        final Date startTime = new Date();
        final String requestBody = ServletHelper.appendAndEncodeUrlParameters("", requestParams);
        LOGGER.trace(pwmRequest, "beginning " + debugText + " request to " + requestUrl + ", body: \n" + requestBody);
        final HttpPost httpPost = new HttpPost(requestUrl);
        httpPost.setHeader(PwmConstants.HttpHeader.Authorization.getHttpName(),
                new BasicAuthInfo(settings.getClientID(), settings.getSecret()).toAuthHeader());
        final StringEntity bodyEntity = new StringEntity(requestBody);
        bodyEntity.setContentType(PwmConstants.ContentTypeValue.form.getHeaderValue());
        httpPost.setEntity(bodyEntity);

        final HttpResponse httpResponse = PwmHttpClient.getHttpClient(pwmRequest.getConfig()).execute(httpPost);
        final String bodyResponse = EntityUtils.toString(httpResponse.getEntity());

        final StringBuilder debugOutput = new StringBuilder();
        debugOutput.append(debugText).append(
                TimeDuration.fromCurrent(startTime).asCompactString()).append(", status: ").append(
                httpResponse.getStatusLine()).append("\n");
        for (Header responseHeader : httpResponse.getAllHeaders()) {
            debugOutput.append(" response header: ").append(responseHeader.getName()).append(": ").append(
                    responseHeader.getValue()).append("\n");
        }

        debugOutput.append(" body:\n ").append(bodyResponse);
        LOGGER.trace(pwmRequest, debugOutput.toString());
        return new RestResults(httpResponse, bodyResponse);
    }

    public static String figureOauthSelfEndPointUrl(final PwmRequest pwmRequest) {
        final HttpServletRequest req = pwmRequest.getHttpServletRequest();
        final String redirect_uri;
        try {
            final URI requestUri = new URI(req.getRequestURL().toString());
            redirect_uri = requestUri.getScheme() + "://" + requestUri.getHost()
                    + (requestUri.getPort() > 0 ? ":" + requestUri.getPort() : "")
                    + req.getContextPath() + "/public/" + PwmConstants.URL_SERVLET_OAUTH_CONSUMER;
        } catch (URISyntaxException e) {
            throw new IllegalStateException("unable to parse inbound request uri while generating oauth redirect: " + e.getMessage());
        }
        return redirect_uri;
    }

    public static class Settings implements Serializable {
        private String loginURL;
        private String codeResolveUrl;
        private String attributesUrl;
        private String clientID;
        private PasswordData secret;
        private String dnAttributeName;

        public String getLoginURL()
        {
            return loginURL;
        }

        public String getCodeResolveUrl()
        {
            return codeResolveUrl;
        }

        public String getAttributesUrl()
        {
            return attributesUrl;
        }

        public String getClientID()
        {
            return clientID;
        }

        public PasswordData getSecret()
        {
            return secret;
        }

        public String getDnAttributeName()
        {
            return dnAttributeName;
        }

        public boolean oAuthIsConfigured() {
            return (loginURL != null && !loginURL.isEmpty())
                    && (codeResolveUrl != null && !codeResolveUrl.isEmpty())
                    && (attributesUrl != null && !attributesUrl.isEmpty())
                    && (clientID != null && !clientID.isEmpty())
                    && (secret != null)
                    && (dnAttributeName != null && !dnAttributeName.isEmpty());
        }

        public static Settings fromConfiguration(final Configuration config) {
            final Settings settings = new Settings();
            settings.loginURL = config.readSettingAsString(PwmSetting.OAUTH_ID_LOGIN_URL);
            settings.codeResolveUrl = config.readSettingAsString(PwmSetting.OAUTH_ID_CODERESOLVE_URL);
            settings.attributesUrl = config.readSettingAsString(PwmSetting.OAUTH_ID_ATTRIBUTES_URL);
            settings.clientID = config.readSettingAsString(PwmSetting.OAUTH_ID_CLIENTNAME);
            settings.secret = config.readSettingAsPassword(PwmSetting.OAUTH_ID_SECRET);
            settings.dnAttributeName = config.readSettingAsString(PwmSetting.OAUTH_ID_DN_ATTRIBUTE_NAME);
            return settings;
        }
    }

    static class RestResults {
        final HttpResponse httpResponse;
        final String responseBody;

        RestResults(
                HttpResponse httpResponse,
                String responseBody
        )
        {
            this.httpResponse = httpResponse;
            this.responseBody = responseBody;
        }

        public HttpResponse getHttpResponse()
        {
            return httpResponse;
        }

        public String getResponseBody()
        {
            return responseBody;
        }
    }

    static class OAuthResolveResults implements Serializable {
        private String accessToken;
        private int expiresSeconds;
        private String refreshToken;


        public String getAccessToken()
        {
            return accessToken;
        }

        public void setAccessToken(String accessToken)
        {
            this.accessToken = accessToken;
        }

        public int getExpiresSeconds()
        {
            return expiresSeconds;
        }

        public void setExpiresSeconds(int expiresSeconds)
        {
            this.expiresSeconds = expiresSeconds;
        }

        public String getRefreshToken()
        {
            return refreshToken;
        }

        public void setRefreshToken(String refreshToken)
        {
            this.refreshToken = refreshToken;
        }
    }
}
