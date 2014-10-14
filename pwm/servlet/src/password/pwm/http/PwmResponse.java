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

package password.pwm.http;

import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.SessionStateBean;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.i18n.Message;
import password.pwm.util.Helper;
import password.pwm.util.StringUtil;
import password.pwm.util.logging.PwmLogger;
import password.pwm.ws.server.RestResultBean;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class PwmResponse extends PwmHttpResponseWrapper {
    private static final PwmLogger LOGGER = PwmLogger.forClass(PwmResponse.class);

    final private PwmRequest pwmRequest;

    public PwmResponse(
            HttpServletResponse response,
            PwmRequest pwmRequest,
            Configuration configuration
    )
    {
        super(response, configuration);
        this.pwmRequest = pwmRequest;
    }

    public void forwardToJsp(
            final PwmConstants.JSP_URL jspURL
    )
            throws ServletException, IOException, PwmUnrecoverableException
    {
        final HttpServletRequest httpServletRequest = pwmRequest.getHttpServletRequest();
        final ServletContext servletContext = httpServletRequest.getSession().getServletContext();
        final String url = jspURL.getPath();
        try {
            LOGGER.trace(pwmRequest.getSessionLabel(), "forwarding to " + url);
        } catch (Exception e) {
            /* noop, server may not be up enough to do the log output */
        }
        servletContext.getRequestDispatcher(url).forward(httpServletRequest, this.getHttpServletResponse());
    }

    public void forwardToSuccessPage(Message message, final String field)
            throws ServletException, PwmUnrecoverableException, IOException
    {
        pwmRequest.getPwmSession().getSessionStateBean().setSessionSuccess(message, field);
        forwardToSuccessPage();
    }

    public void forwardToSuccessPage(
    )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();

        if (!pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.DISPLAY_SUCCESS_PAGES)) {
            ssBean.setSessionSuccess(null, null);
            LOGGER.trace(pwmSession, "skipping success page due to configuration setting.");
            final StringBuilder redirectURL = new StringBuilder();
            redirectURL.append(pwmRequest.getContextPath());
            redirectURL.append("/public/CommandServlet");
            redirectURL.append("?processAction=continue");
            redirectURL.append("&pwmFormID=");
            redirectURL.append(Helper.buildPwmFormID(pwmSession.getSessionStateBean()));
            sendRedirect(redirectURL.toString());
            return;
        }

        try {
            if (ssBean.getSessionSuccess() == null) {
                ssBean.setSessionSuccess(Message.SUCCESS_UNKNOWN, null);
            }

            forwardToJsp(PwmConstants.JSP_URL.SUCCESS);
        } catch (PwmUnrecoverableException e) {
            LOGGER.error("unexpected error sending user to success page: " + e.toString());
        }
    }

    public void outputJsonResult(
            final RestResultBean restResultBean
    )
            throws IOException
    {
        final HttpServletResponse resp = this.getHttpServletResponse();
        final String outputString = restResultBean.toJson();
        resp.setContentType(PwmConstants.ContentTypeValue.json.getHeaderValue());
        resp.getWriter().print(outputString);
        resp.getWriter().flush();
    }

    public void forwardToLoginPage(
    )
            throws IOException {
        final String loginServletURL = pwmRequest.getContextPath() + "/private/" + PwmConstants.URL_SERVLET_LOGIN;
        sendRedirect(loginServletURL);
    }

    public void writeCookie(final String cookieName, final String cookieValue, final int seconds) {
        final Cookie theCookie = new Cookie(cookieName, StringUtil.urlEncode(cookieValue));
        theCookie.setMaxAge(seconds);
        this.getHttpServletResponse().addCookie(theCookie);
    }

}
