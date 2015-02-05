<%@ page import="password.pwm.http.servlet.ForgottenPasswordServlet" %>
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
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_ForgottenPassword"/>
    </jsp:include>
    <div id="centerbody">
        <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
        <p><pwm:display key="Display_RecoverPasswordChoices"/></p>
        <table class="noborder">
            <tr>
                <td>
                    <form action="<pwm:url url='ForgottenPassword'/>" method="post"
                          enctype="application/x-www-form-urlencoded" name="search">
                        <button class="btn" type="submit" name="submitBtn">
                            <pwm:if test="showIcons"><span class="btn-icon fa fa-unlock"></span></pwm:if>
                            <pwm:display key="Button_UnlockPassword"/>
                        </button>
                        <input type="hidden" name="choice" value="unlock"/>
                        <input type="hidden" name="processAction" value="<%=ForgottenPasswordServlet.ForgottenPasswordAction.actionChoice%>"/>
                        <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
                    </form>
                </td>
                <td>
                    <pwm:display key="Display_RecoverChoiceUnlock"/>
                </td>
            </tr>
            <tr>
                <td>
                    &nbsp;
                </td>
            </tr>
            <tr>
                <td>
                    <form action="<pwm:url url='ForgottenPassword'/>" method="post"
                          enctype="application/x-www-form-urlencoded" name="search">
                        <button class="btn" type="submit" name="submitBtn">
                            <pwm:if test="showIcons"><span class="btn-icon fa fa-key"></span></pwm:if>
                            <pwm:display key="Button_ChangePassword"/>
                        </button>
                        <input type="hidden" name="choice" value="resetPassword"/>
                        <input type="hidden" name="processAction" value="<%=ForgottenPasswordServlet.ForgottenPasswordAction.actionChoice%>"/>
                        <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
                    </form>
                </td>
                <td>
                    <pwm:display key="Display_RecoverChoiceReset"/>
                </td>
            </tr>
            <tr>
                <td>
                    &nbsp;
                </td>
            </tr>
            <tr>
                <td>
                    <%@ include file="/WEB-INF/jsp/fragment/button-cancel.jsp" %>
                </td>
                <td>
                    &nbsp;
                </td>
            </tr>
            <tr>
                <td>
                    &nbsp;
                </td>
            </tr>
        </table>
    </div>
    <div class="push"></div>
</div>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>

