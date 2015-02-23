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
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_UpdateProfile"/>
    </jsp:include>
    <div id="centerbody">
        <p><pwm:display key="Display_UpdateProfile"/></p>
        <%@ include file="fragment/message.jsp" %>
        <br/>
        <form action="<pwm:url url='UpdateProfile'/>" method="post" name="updateProfileForm" enctype="application/x-www-form-urlencoded"
              class="pwm-form" id="updateProfileForm">

            <jsp:include page="fragment/form.jsp"/>

            <div class="buttonbar">
                <button id="submitBtn" type="submit" class="btn" name="button">
                    <pwm:if test="showIcons"><span class="btn-icon fa fa-forward"></span></pwm:if>
                    <pwm:display key="Button_Update"/>
                </button>
                <%@ include file="/WEB-INF/jsp/fragment/button-reset.jsp" %>
                <input type="hidden" name="processAction" value="updateProfile"/>
                <%@ include file="/WEB-INF/jsp/fragment/button-cancel.jsp" %>
                <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
            </div>
        </form>
    </div>
    <div class="push"></div>
</div>
<pwm:script>
<script type="text/javascript">
    PWM_GLOBAL['startupFunctions'].push(function(){
        PWM_MAIN.addEventHandler('updateProfileForm','input',function(){PWM_UPDATE.validateForm()});
    });
</script>
</pwm:script>
<pwm:script-ref url="/public/resources/js/updateprofile.js"/>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>

