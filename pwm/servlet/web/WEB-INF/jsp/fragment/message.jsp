<%--
~ Password Management Servlets (PWM)
~ http://code.google.com/p/pwm/
~
~ Copyright (c) 2006-2009 Novell, Inc.
~ Copyright (c) 2009-2011 The PWM Project
~
~ This program is free software; you can redistribute it and/or modify
~ it under the terms of the GNU General Public License as published by
~ the Free Software Foundation; either version 2 of the License, or
~ (at your option) any later version.
~
~ This program is distributed in the hope that it will be useful,
~ but WITHOUT ANY WARRANTY; without even the implied warranty of
~ MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
~ GNU General Public License for more details.
~
~ You should have received a copy of the GNU General Public License
~ along with this program; if not, write to the Free Software
~ Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
--%>

<%--
  ~ This file is imported by most JSPs, it shows the error/success message bar.
  --%>
<%@ taglib uri="pwm" prefix="pwm" %>
<% if (PwmSession.getPwmSession(session).getSessionStateBean().getSessionError() != null) { %>
<span id="message" class="message message-error"><pwm:ErrorMessage/></span>
<% } else if (PwmSession.getPwmSession(session).getSessionStateBean().getSessionSuccess() != null) { %>
<span id="message" class="message message-success"><pwm:SuccessMessage/></span>
<% } else { %>
<span id="message" class="message">&nbsp;</span>
<% } %>
<div id="capslockwarning" style="display:none;"><pwm:Display key="Display_CapsLockIsOn"/></div>

