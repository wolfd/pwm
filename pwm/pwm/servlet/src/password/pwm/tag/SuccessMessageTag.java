/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2012 The PWM Project
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

import password.pwm.ContextManager;
import password.pwm.PwmApplication;
import password.pwm.PwmSession;
import password.pwm.config.Message;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.jsp.JspTagException;

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
            final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);

            final Message successMsg = pwmSession.getSessionStateBean().getSessionSuccess();
            final String successField = pwmSession.getSessionStateBean().getSessionSuccessField();

            final String errorMsg = successMsg.getLocalizedMessage(pwmSession.getSessionStateBean().getLocale(), successField, pwmApplication.getConfig());
            pageContext.getOut().write(errorMsg);
        } catch (Exception e) {
            throw new JspTagException(e.getMessage());
        }
        return EVAL_PAGE;
    }
}

