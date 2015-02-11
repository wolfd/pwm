<%@ page import="password.pwm.error.PwmException" %>
<%@ page import="password.pwm.http.servlet.NewUserServlet" %>
<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2015 The PWM Project
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

<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<%
    long refreshSeconds = 10;
    long checkIntervalSeconds = 5;
    try {
        final PwmRequest pwmRequest = PwmRequest.forRequest(request, response);
        refreshSeconds = 30 * NewUserServlet.getNewUserProfile(pwmRequest).readSettingAsLong(PwmSetting.NEWUSER_MINIMUM_WAIT_TIME);
        checkIntervalSeconds = Long.parseLong(pwmRequest.getConfig().readAppProperty(AppProperty.CLIENT_AJAX_PW_WAIT_CHECK_SECONDS));
    } catch (PwmException e) {
        /* noop */
    }
%>
<meta http-equiv="refresh" content="<%=refreshSeconds%>;url=NewUser?processAction=complete&pwmFormID=<pwm:FormID/>">
<div id="wrapper">
    <% JspUtility.setFlag(pageContext, PwmRequest.Flag.HIDE_HEADER_BUTTONS); %>
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_PleaseWait"/>
    </jsp:include>
    <div id="centerbody">
        <p><pwm:display key="Display_PleaseWaitNewUser"/></p>
        <%@ include file="fragment/message.jsp" %>

        <div style="width:400px; margin-left: auto; margin-right: auto; padding-top: 70px">
            <div data-dojo-type="dijit/ProgressBar" style="width:400px" data-dojo-id="createProgressBar" id="createProgressBar" data-dojo-props="maximum:100"></div>
        </div>
        <div style="text-align: center; width: 100%; padding-top: 50px">
            <%--
            <div>Elapsed Time: <span id="elapsedSeconds"></span></div>
            <div>Estimated Time Remaining: <span id="estimatedRemainingSeconds"></span></div>
            --%>
        </div>
        <br/>

    </div>
    <div class="push"></div>
</div>
<pwm:script>
<script type="text/javascript">
    PWM_GLOBAL['startupFunctions'].push(function(){
        PWM_GLOBAL['idle_suspendTimeout'] = true;
        require(["dojo/parser", "dijit/ProgressBar","dojo/ready"], function(parser,registry){
            parser.parse();
            PWM_NEWUSER.refreshCreateStatus(<%=checkIntervalSeconds * 1000%>);
        });
    });
</script>
</pwm:script>
<script type="text/javascript" src="<pwm:context/><pwm:url url='/public/resources/js/newuser.js'/>"></script>
<% JspUtility.setFlag(pageContext, PwmRequest.Flag.HIDE_FOOTER_TEXT); %>
<%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>
