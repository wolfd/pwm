<%@ page import="password.pwm.http.bean.NewUserBean" %>

<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2014 The PWM Project
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

<%
    final NewUserBean newUserBean = PwmSession.getPwmSession(session).getNewUserBean();
    String destination = newUserBean.getTokenDisplayText();
%>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_NewUser"/>
    </jsp:include>
    <div id="centerbody">
        <% if (newUserBean.getVerificationPhase() == NewUserBean.NewUserVerificationPhase.EMAIL) { %>
        <p><pwm:display key="Display_RecoverEnterCode" value1="<%=destination%>"/></p>
        <% } else if (newUserBean.getVerificationPhase() == NewUserBean.NewUserVerificationPhase.SMS) { %>
        <p><pwm:display key="Display_RecoverEnterCodeSMS" value1="<%=destination%>"/></p>
        <% } %>
        <form action="<pwm:url url='NewUser'/>" method="post"
              enctype="application/x-www-form-urlencoded" name="search" class="pwm-form">
            <%@ include file="fragment/message.jsp" %>
            <h2><label for="<%=PwmConstants.PARAM_TOKEN%>"><pwm:display key="Field_Code"/></label></h2>
            <textarea id="<%=PwmConstants.PARAM_TOKEN%>" name="<%=PwmConstants.PARAM_TOKEN%>" class="tokenInput"></textarea>
            <div id="buttonbar">
                <button type="submit" class="btn" name="search" id="submitBtn">
                    <pwm:if test="showIcons"><span class="btn-icon fa fa-check"></span></pwm:if>
                    <pwm:display key="Button_CheckCode"/>
                </button>
                <%@ include file="/WEB-INF/jsp/fragment/button-reset.jsp" %>
                <input type="hidden" id="processAction" name="processAction" value="enterCode"/>
                <input type="hidden" id="pwmFormID" name="pwmFormID" value="<pwm:FormID/>"/>
            </div>
        </form>
        <div style="text-align: center">
            <form action="<pwm:url url='/public/NewUser' addContext="true"/>" method="post"
                  enctype="application/x-www-form-urlencoded">
                <input type="hidden" name="processAction" value="reset"/>
                <button type="submit" name="button" class="btn" id="button_reset">
                    <pwm:if test="showIcons"><span class="btn-icon fa fa-times"></span></pwm:if>
                    <pwm:display key="Button_Cancel"/>
                </button>
                <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
            </form>
        </div>
    </div>
    <div class="push"></div>
</div>
<pwm:script>
<script type="text/javascript">
    PWM_GLOBAL['startupFunctions'].push(function(){
        PWM_MAIN.getObject('<%=PwmConstants.PARAM_TOKEN%>').focus();
    });
</script>
</pwm:script>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>

