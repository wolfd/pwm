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

package password.pwm.tag;

import password.pwm.PwmSession;
import password.pwm.config.Message;
import password.pwm.config.PwmSetting;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.jsp.JspTagException;
import java.util.Locale;

/**
 * @author Jason D. Rivard
 */
public class SuccessMessageTag extends PwmAbstractTag {
// ------------------------ INTERFACE METHODS ------------------------


// --------------------- Interface Tag ---------------------

    public int doEndTag()
            throws javax.servlet.jsp.JspTagException {
        try {
            final HttpServletRequest req = (HttpServletRequest) pageContext.getRequest();
            final PwmSession pwmSession = PwmSession.getPwmSession(req);
            final Message successMsg = pwmSession.getSessionStateBean().getSessionSuccess();
            final String successField = pwmSession.getSessionStateBean().getSessionSuccessField();

            boolean messageWritten = false;
            if (successMsg != null && successMsg == Message.SUCCESS_PASSWORDCHANGE) {
                final Locale userLocale = pwmSession.getSessionStateBean().getLocale();
                final String configuredMessage = pwmSession.getConfig().readLocalizedStringSetting(PwmSetting.PASSWORD_CHANGE_SUCCESS_MESSAGE, userLocale);
                if (configuredMessage != null && configuredMessage.length() > 0) {
                    pageContext.getOut().write(configuredMessage);
                    messageWritten = true;
                }
            }

            if (!messageWritten && successMsg != null) {
                final String errorMsg = successMsg.getLocalizedMessage(pwmSession.getSessionStateBean().getLocale(), successField);
                pageContext.getOut().write(errorMsg);
            }
        } catch (Exception e) {
            throw new JspTagException(e.getMessage());
        }
        return EVAL_PAGE;
    }
}

