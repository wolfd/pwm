<%@ page import="password.pwm.http.JspUtility" %>
<%@ page import="password.pwm.http.bean.ConfigGuideBean" %>
<%@ page import="password.pwm.http.servlet.ConfigGuideServlet" %>
<%@ page import="java.util.Map" %>
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

<!DOCTYPE html>

<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<% ConfigGuideBean configGuideBean = (ConfigGuideBean) PwmSession.getPwmSession(session).getSessionBean(ConfigGuideBean.class);%>
<% Map<String,String> DEFAULT_FORM = ConfigGuideServlet.defaultForm(configGuideBean.getStoredConfiguration().getTemplate()); %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<link href="<pwm:context/><pwm:url url='/public/resources/configStyle.css'/>" rel="stylesheet" type="text/css"/>
<div id="wrapper">
    <div id="header">
        <div id="header-center">
            <div id="header-page">
                <pwm:display key="Title_ConfigGuide" bundle="Config"/>
            </div>
            <div id="header-title">
                <pwm:display key="Title_ConfigGuide_ldap" bundle="Config"/>
            </div>
        </div>
    </div>
    <div id="centerbody">
        <form id="configForm">
            <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
            <br/>
            <div id="outline_ldap" class="setting_outline">
                <div class="setting_title">
                    Site URL
                </div>
                <div class="setting_body">
                    The URL to this application, as seen by users.  The value is used in email macros and other user facing communications.
                    Example: <b>http://www.example.com/password</b>.  The site URL must use a valid fully qualified hostname.  Do not use a network address.                    <br/><br/>
                    <div class="setting_item">
                        <div id="titlePane_<%=ConfigGuideServlet.PARAM_APP_SITEURL%>" style="padding-left: 5px; padding-top: 5px">
                            <b>Site URL</b>
                            <br/>
                            <span class="fa fa-chevron-circle-right"></span>
                            <input id="<%=ConfigGuideServlet.PARAM_APP_SITEURL%>" name="<%=ConfigGuideServlet.PARAM_APP_SITEURL%>" value="<%=configGuideBean.getFormData().get(ConfigGuideServlet.PARAM_APP_SITEURL)%>"/>
                            <pwm:script>
                            <script type="text/javascript">
                                PWM_GLOBAL['startupFunctions'].push(function(){
                                    require(["dijit/form/ValidationTextBox"],function(ValidationTextBox){
                                        new ValidationTextBox({
                                            name: '<%=ConfigGuideServlet.PARAM_APP_SITEURL%>',
                                            required: false,
                                            style: "width: 550px",
                                            value: '<%=configGuideBean.getFormData().get(ConfigGuideServlet.PARAM_APP_SITEURL)%>'
                                        }, "<%=ConfigGuideServlet.PARAM_APP_SITEURL%>");
                                    });
                                });
                            </script>
                            </pwm:script>
                        </div>
                    </div>
                </div>
            </div>
        </form>
        <br/>
        <div id="buttonbar">
            <button class="btn" id="button_previous">
                <pwm:if test="showIcons"><span class="btn-icon fa fa-backward"></span></pwm:if>
                <pwm:display key="Button_Previous" bundle="Config"/>
            </button>
            &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
            <button class="btn" id="button_next">
                <pwm:if test="showIcons"><span class="btn-icon fa fa-forward"></span></pwm:if>
                <pwm:display key="Button_Next"  bundle="Config"/>
            </button>
        </div>
    </div>
    <div class="push"></div>
</div>
<pwm:script>
<script type="text/javascript">
    function handleFormActivity() {
        PWM_GUIDE.updateForm();
    }

    PWM_GLOBAL['startupFunctions'].push(function(){

        PWM_MAIN.addEventHandler('button_next','click',function(){PWM_GUIDE.gotoStep('PASSWORD')});
        PWM_MAIN.addEventHandler('button_previous','click',function(){PWM_GUIDE.gotoStep('CR_STORAGE')});

        PWM_MAIN.addEventHandler('configForm','input',function(){handleFormActivity()});
    });

</script>
</pwm:script>
<% JspUtility.setFlag(pageContext, PwmRequest.Flag.HIDE_LOCALE); %>
<script type="text/javascript" src="<pwm:context/><pwm:url url="/public/resources/js/configguide.js"/>"></script>
<script type="text/javascript" src="<pwm:context/><pwm:url url="/public/resources/js/configmanager.js"/>"></script>
<script type="text/javascript" src="<pwm:context/><pwm:url url="/public/resources/js/admin.js"/>"></script>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
