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

<%@ taglib uri="pwm" prefix="pwm" %>
<head>
    <title><pwm:Display key="APPLICATION-TITLE"/>
        <% // can't use <jsp:UseBean> because it will cause duplicate useBean error.
            final String userField = password.pwm.PwmSession.getUserInfoBean(request.getSession()).getUserID();
            if (userField != null && userField.length() > 0) {
                out.write(" - ");
                out.write(userField);
            }
        %>
    </title>
    <meta http-equiv="content-type" content="text/html;charset=utf-8"/>
    <meta name="Description" content="PWM Password Self Service"/>
    <meta name="X-Pwm-Instance" content="<%=password.pwm.PwmSession.getPwmSession(request).getContextManager().getInstanceID()%>>"/>
    <meta name="viewport" content="width=320, user-scalable=no"/>
    <link rel="icon" type="image/vnd.microsoft.icon" href="<%=request.getContextPath()%>/resources/<pwm:url url='favicon.ico'/>" />
    <link href="<%=request.getContextPath()%>/resources/<pwm:url url='pwmStyle.css'/>"
          rel="stylesheet" type="text/css" media="screen"/>
    <link media="only screen and (max-device-width: 480px)" <%-- iphone css --%>
          href="<%=request.getContextPath()%>/resources/<pwm:url url='pwmMobileStyle.css'/>" type="text/css" rel="stylesheet" />
    <link href="<%=request.getContextPath()%>/resources/dojo/dijit/themes/tundra/tundra.css" rel="stylesheet" type="text/css"/>
    <script type="text/javascript" src="<%=request.getContextPath()%>/resources/dojo/dojo/dojo.js"></script>
    <script type="text/javascript" src="<%=request.getContextPath()%>/resources/dojo/dijit/dijit.js"></script>
    <script type="text/javascript" src="<%=request.getContextPath()%>/resources/<pwm:url url='pwmHelper.js'/>"></script>
    <% if (password.pwm.PwmSession.getPwmSession(request).getContextManager().getConfig() != null) { %>
    <% final String googleTrackingCode =  password.pwm.PwmSession.getPwmSession(request).getContextManager().getConfig().readSettingAsString(password.pwm.config.PwmSetting.GOOGLE_ANAYLTICS_TRACKER); %>
    <% if (googleTrackingCode != null && googleTrackingCode.length() > 0) { %>
    <script type="text/javascript">
        var gaJsHost = (("https:" == document.location.protocol) ? "https://ssl." : "http://www.");
        document.write(unescape("%3Cscript src='" + gaJsHost + "google-analytics.com/ga.js' type='text/javascript'%3E%3C/script%3E"));
    </script>
    <script type="text/javascript">
        try{
            var pageTracker = _gat._getTracker("<%=googleTrackingCode%>");
            pageTracker._trackPageview();
        } catch(err) {}                                                                                                             
    </script>
    <% } %>
    <% } %>
    <script type="text/javascript">
        PWM_GLOBAL['pwmFormID']='<pwm:FormID/>';
        PWM_GLOBAL['setting-showHidePasswordFields']=<%=password.pwm.PwmSession.getPwmSession(session).getConfig().readSettingAsBoolean(password.pwm.config.PwmSetting.DISPLAY_SHOW_HIDE_PASSWORD_FIELDS)%>;
    </script>
</head>
