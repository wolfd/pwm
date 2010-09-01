<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2010 The PWM Project
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

<%@ page import="password.pwm.PwmSession" %>
<%@ page import="java.text.SimpleDateFormat" %>
<%@ page import="password.pwm.PwmConstants" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<%-- begin pwm footer --%>
<div id="footer">
    <script type="text/javascript" src="<%=request.getContextPath()%>/resources/<pwm:url url='idletimer.js'/>"></script>
    <span class="idle_status" id="idle_status">
        &nbsp;
    </span>
    <br/>
    PWM <%= PwmConstants.SERVLET_VERSION %> |
    <% if (PwmSession.getSessionStateBean(session).isAuthenticated()) { %>
    <%= PwmSession.getUserInfoBean(session).getUserID()%> 
    |
    <% } %>
    <%
        final password.pwm.bean.SessionStateBean sessionStateBean = PwmSession.getSessionStateBean(session);
        final java.text.DateFormat df = SimpleDateFormat.getDateTimeInstance(SimpleDateFormat.DEFAULT, SimpleDateFormat.DEFAULT, request.getLocale());
        out.write(df.format(new java.util.Date()));
    %>
    |
    src:
    <%
        final String userIP = sessionStateBean.getSrcAddress();
        if (userIP == null) {
            out.write("unknown");
        } else {
            out.write(userIP);
        }
    %>
    <%-- fields for javascript display fields --%>
    <script type="text/javascript">
        PWM_STRINGS['Button_Logout']='<pwm:Display key="Button_Logout"/>';
        PWM_STRINGS['Display_IdleTimeout']='<pwm:Display key="Display_IdleTimeout"/>';
        PWM_STRINGS['Display_Day']='<pwm:Display key="Display_Day"/>';
        PWM_STRINGS['Display_Days']='<pwm:Display key="Display_Days"/>';
        PWM_STRINGS['Display_Hour']='<pwm:Display key="Display_Hour"/>';
        PWM_STRINGS['Display_Hours']='<pwm:Display key="Display_Hours"/>';
        PWM_STRINGS['Display_Minute']='<pwm:Display key="Display_Minute"/>';
        PWM_STRINGS['Display_Minutes']='<pwm:Display key="Display_Minutes"/>';
        PWM_STRINGS['Display_Second']='<pwm:Display key="Display_Second"/>';
        PWM_STRINGS['Display_Seconds']='<pwm:Display key="Display_Seconds"/>';
        PWM_STRINGS['Display_PleaseWait']='<pwm:Display key="Display_PleaseWait"/>';
        PWM_STRINGS['Display_IdleWarningTitle']='<pwm:Display key="Display_IdleWarningTitle"/>';
        PWM_STRINGS['Display_IdleWarningMessage']='<pwm:Display key="Display_IdleWarningMessage"/>';
        PWM_STRINGS['url-logout']='<%=request.getContextPath()%>/public/<pwm:url url='Logout?idle=true'/>';
        PWM_STRINGS['url-command']='<%=request.getContextPath()%>/public/<pwm:url url='CommandServlet'/>';
    </script>
    <script type="text/javascript">initCountDownTimer(<%= request.getSession().getMaxInactiveInterval() %>);</script>
</div>
