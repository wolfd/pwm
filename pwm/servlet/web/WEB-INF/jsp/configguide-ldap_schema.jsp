<%@ page import="password.pwm.http.bean.ConfigGuideBean" %>
<%@ page import="password.pwm.http.servlet.ConfigGuideServlet" %>
<%@ page import="password.pwm.ldap.schema.SchemaOperationResult" %>
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
<%
    final PwmRequest pwmRequest = PwmRequest.forRequest(request, response);
    ConfigGuideBean configGuideBean = JspUtility.getPwmSession(pageContext).getSessionBean(ConfigGuideBean.class);
    boolean existingSchemaGood = false;
    String schemaActivityLog = "";
    try {
        SchemaOperationResult schemaManager = ConfigGuideServlet.extendSchema(configGuideBean,false);
        existingSchemaGood = schemaManager.isSuccess();
        schemaActivityLog = schemaManager.getOperationLog();
    } catch (Exception e) {
        schemaActivityLog = "unable to check schema: " + e.getMessage();
    }
%>
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
                <pwm:display key="Title_ConfigGuide_ldap_schema" bundle="Config"/>
            </div>
        </div>
    </div>
    <div id="centerbody">
        <form id="formData" data-dojo-type="dijit/form/Form">
            <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
            <br class="clear"/>
            <div id="outline_ldap-server" class="setting_outline">
                <div class="setting_title">
                    LDAP Schema Status
                </div>
                <div class="setting_body">
                    <p>
                    <pwm:display key="Display_ConfigGuideLdapSchema" bundle="Config"/>
                    </p>
                    <div>
                        <div id="titlePane_<%=ConfigGuideServlet.PARAM_LDAP_HOST%>" style="padding-left: 5px; padding-top: 5px">
                            <table style="width:100%">
                                <tr><td>Schema Detail</td></tr>
                                <tr><td class="setting_table_value"><pre><%=schemaActivityLog%></pre></td></tr>
                            </table>
                        </div>
                    </div>
                </div>
            </div>
            <br class="clear"/>
            <div id="outline_ldapcert-options" class="setting_outline">
                <div class="setting_title">LDAP Schema Extension</div>
                <div class="setting_body">
                    <p>
                        <% if (existingSchemaGood) { %>
                        Schema extension is complete.
                        <% } else { %>
                        <pwm:display key="Display_ConfigGuideLdapSchema2" bundle="Config"/>
                        <% } %>
                    </p>
                    <button class="btn" type="button" id="button-extendSchema" <%=existingSchemaGood?"disabled=\"disabled\"":""%>>
                        <pwm:if test="showIcons"><span class="btn-icon fa fa-bolt"></span></pwm:if>
                        Extend Schema
                    </button>
                </div>
            </div>
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
        PWM_GLOBAL['startupFunctions'].push(function(){
            require(["dojo/parser","dijit/TitlePane","dijit/form/Form","dijit/form/ValidationTextBox","dijit/form/NumberSpinner","dijit/form/CheckBox"],function(dojoParser){
                dojoParser.parse();
            });

            PWM_MAIN.addEventHandler('button_next','click',function(){PWM_GUIDE.gotoStep('NEXT')});
            PWM_MAIN.addEventHandler('button_previous','click',function(){PWM_GUIDE.gotoStep('PREVIOUS')});
            PWM_MAIN.addEventHandler('button-extendSchema','click',function(){PWM_GUIDE.extendSchema()});
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
