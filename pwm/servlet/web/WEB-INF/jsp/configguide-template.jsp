<%@ page import="password.pwm.http.bean.ConfigGuideBean" %>
<%@ page import="password.pwm.http.servlet.ConfigGuideServlet" %>
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
<% ConfigGuideBean configGuideBean = JspUtility.getPwmSession(pageContext).getSessionBean(ConfigGuideBean.class);%>
<% PwmSetting.Template selectedTemplate = configGuideBean.getSelectedTemplate(); %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <div id="header">
        <div id="header-center">
            <div id="header-page">
                <pwm:display key="Title_ConfigGuide" bundle="Config"/>
            </div>
            <div id="header-title">
                <pwm:display key="Title_ConfigGuide_template" bundle="Config"/>
            </div>
        </div>
    </div>
    <div id="centerbody">
        <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
        <pwm:display key="Display_ConfigGuideSelectTemplate" bundle="Config"/>
        <br/>
        <form id="configForm">
            <select id="<%=ConfigGuideServlet.PARAM_TEMPLATE_NAME%>" name="<%=ConfigGuideServlet.PARAM_TEMPLATE_NAME%>" style="width:300px">
                <% if (selectedTemplate == null) { %>
                <option value="NOTSELECTED" selected="selected">-- Please select a template --</option>
                <% } %>
                <% for (final PwmSetting.Template loopTemplate : PwmSetting.Template.values()) { %>
                <% boolean selected = loopTemplate.equals(selectedTemplate); %>
                <option value="<%=loopTemplate.toString()%>"<% if (selected) { %> selected="selected"<% } %>>
                    <%=loopTemplate.getLabel(JspUtility.locale(request))%>
                </option>
                <% } %>
            </select>
        </form>
        <br/>

        <div class="buttonbar">
            <button class="btn" id="button_previous">
                <pwm:if test="showIcons"><span class="btn-icon fa fa-backward"></span></pwm:if>
                <pwm:display key="Button_Previous" bundle="Config"/>
            </button>
            &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
            <button class="btn" id="button_next">
                <pwm:if test="showIcons"><span class="btn-icon fa fa-forward"></span></pwm:if>
                <pwm:display key="Button_Next" bundle="Config"/>
            </button>
        </div>
    </div>
    <div class="push"></div>
</div>
<pwm:script>
    <script type="text/javascript">
        function formHandler() {
            var startTemplate = '<%=selectedTemplate == null ? "" : selectedTemplate.toString()%>';
            var newTemplate = getSelectedValue();
            if (startTemplate && startTemplate.length > 0 && startTemplate != newTemplate) {
                PWM_MAIN.showConfirmDialog({
                    text:'Changing the template will cause existing guide settings to be cleared.  Are you sure you wish to continue?',
                    okAction:function(){
                        PWM_GUIDE.updateForm();
                        updateNextButton();
                    },
                    cancelAction:function(){
                        PWM_MAIN.goto('/private/config/ConfigGuide');
                    }
                });
            } else {
                PWM_GUIDE.updateForm();
                updateNextButton();
            }
        }

        function getSelectedValue() {
            var selectedIndex = PWM_MAIN.getObject('<%=ConfigGuideServlet.PARAM_TEMPLATE_NAME%>').selectedIndex;
            var newTemplate = PWM_MAIN.getObject('<%=ConfigGuideServlet.PARAM_TEMPLATE_NAME%>').options[selectedIndex];
            return newTemplate.value;
        }

        function updateNextButton() {
            var newTemplate = getSelectedValue();
            var notSelected = newTemplate == 'NOTSELECTED';
            PWM_MAIN.getObject('button_next').disabled = notSelected;
        }

        PWM_GLOBAL['startupFunctions'].push(function(){
            PWM_MAIN.addEventHandler('button_previous','click',function(){PWM_GUIDE.gotoStep('PREVIOUS')});
            PWM_MAIN.addEventHandler('button_next','click',function(){PWM_GUIDE.gotoStep('NEXT')});
            PWM_MAIN.addEventHandler('<%=ConfigGuideServlet.PARAM_TEMPLATE_NAME%>','change',function(){formHandler()});
            updateNextButton();
        });
    </script>
</pwm:script>
<pwm:script-ref url="/public/resources/js/configguide.js"/>
<pwm:script-ref url="/public/resources/js/configmanager.js"/>
<pwm:script-ref url="/public/resources/js/admin.js"/>
<% JspUtility.setFlag(pageContext, PwmRequest.Flag.HIDE_LOCALE);%>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
