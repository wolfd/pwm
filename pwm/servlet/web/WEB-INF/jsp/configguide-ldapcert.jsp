<%@ page import="password.pwm.http.bean.ConfigGuideBean" %>
<%@ page import="password.pwm.http.servlet.ConfigGuideServlet" %>
<%@ page import="password.pwm.util.SecureHelper" %>
<%@ page import="password.pwm.util.StringUtil" %>
<%@ page import="password.pwm.util.X509Utils" %>
<%@ page import="java.io.ByteArrayInputStream" %>
<%@ page import="java.security.cert.X509Certificate" %>
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
<% ConfigGuideBean configGuideBean = JspUtility.getPwmSession(pageContext).getSessionBean(ConfigGuideBean.class);%>
<% boolean enableNext = configGuideBean.isCertsTrustedbyKeystore() || configGuideBean.isUseConfiguredCerts() || configGuideBean.getLdapCertificates() == null; %>
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
                <pwm:display key="Title_ConfigGuide_ldapcert" bundle="Config"/>
            </div>
        </div>
    </div>
    <div id="centerbody">
        <% if (configGuideBean.getLdapCertificates() == null) { %>
        <div>
            <pwm:display key="Display_ConfigGuideNotSecureLDAP" bundle="Config"/>
        </div>
        <% } else { %>
        <form id="formData" data-dojo-type="dijit/form/Form">
            <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
            <br class="clear"/>
            <div id="outline_ldap-server" class="setting_outline">
                <div class="setting_title">
                    LDAP Server Certificates
                </div>
                <div class="setting_body">
                    The following are the LDAP server certificates read from the server at
                    <style></style><b><%=configGuideBean.getFormData().get(ConfigGuideServlet.PARAM_LDAP_HOST)%>:<%=configGuideBean.getFormData().get(ConfigGuideServlet.PARAM_LDAP_PORT)%></b>.
                    Please verify these certificates match your LDAP server.
                    <div>
                        <div id="titlePane_<%=ConfigGuideServlet.PARAM_LDAP_HOST%>" style="padding-left: 5px; padding-top: 5px">
                            <% int counter=0;for (X509Certificate certificate : configGuideBean.getLdapCertificates()) {%>
                            <% final String md5sum = SecureHelper.hash(new ByteArrayInputStream(certificate.getEncoded()), SecureHelper.HashAlgorithm.MD5); %>
                            <% final String sha1sum = SecureHelper.hash(new ByteArrayInputStream(certificate.getEncoded()), SecureHelper.HashAlgorithm.SHA1); %>
                            <table style="width:100%" id="table_certificate0">
                                <tr><td colspan="2" class="key" style="text-align: center">
                                    Certificate <%=counter%>&nbsp;<a style="font-size: smaller" href="#" id="button-showCert_<%=md5sum%>">(details)</a>
                                </td></tr>
                                <tr><td>Subject Name</td><td><div class="setting_table_value"><%=certificate.getSubjectX500Principal().getName()%></div></td></tr>
                                <tr><td>Issuer Name</td><td><div class="setting_table_value"><%=certificate.getIssuerX500Principal().getName()%></div></td></tr>
                                <% final String serialNum = X509Utils.hexSerial(certificate); %>
                                <tr><td>Serial Number</td><td><div class="setting_table_value"><%=serialNum%></div></td></tr>
                                <tr><td>Issue Date</td><td><div class="setting_table_value timestamp"><%=PwmConstants.DEFAULT_DATETIME_FORMAT.format(certificate.getNotBefore())%></div></td></tr>
                                <tr><td>Expire Date</td><td><div class="setting_table_value timestamp"><%=PwmConstants.DEFAULT_DATETIME_FORMAT.format(certificate.getNotAfter())%></div></td></tr>
                            </table>
                            <pwm:script>
                                <script type="text/javascript">
                                    PWM_GLOBAL['startupFunctions'].push(function(){
                                        PWM_MAIN.addEventHandler('button-showCert_<%=md5sum%>','click',function(){
                                            var body = '<pre style="white-space: pre-wrap; word-wrap: break-word">';
                                            body += 'md5sum: <%=md5sum%>\n';
                                            body += 'sha1sum: <%=sha1sum%>\n';
                                            body += '<%=StringUtil.escapeJS(certificate.toString())%>';
                                            body += '</pre>';
                                            PWM_MAIN.showDialog({
                                                title: "Certificate <%=counter%> Detail",
                                                text: body,
                                                showClose: true,
                                                showOk: false,
                                                dialogClass:'wide'
                                            });
                                        });
                                    });
                                </script>
                            </pwm:script>
                            <% counter++; } %>
                        </div>
                    </div>
                </div>
            </div>
            <br class="clear"/>
            <div id="outline_ldapcert-options" class="setting_outline">
                <div class="setting_title">Certificate Settings</div>
                <div class="setting_body">
                    <div style="padding-left: 5px; padding-top: 5px">
                        At least one of the following options must be selected to continue.
                    </div>
                    <br/>
                    <div id="titlePane_<%=ConfigGuideServlet.PARAM_LDAP_ADMIN_DN%>" style="padding-left: 5px; padding-top: 5px">
                        Certificate(s) are trusted by default Java keystore
                        <br/>
                        <span class="fa fa-chevron-circle-right"></span>
                        <button id="button_defaultTrustStore">Enabled</button> (Import/remove certificate manually into Java keystore to change)
                    </div>
                    <div id="titlePane_<%=ConfigGuideServlet.PARAM_LDAP_ADMIN_PW%>" style="padding-left: 5px; padding-top: 5px">
                        Use application to manage certificate(s) and automatically import certificates into configuration file
                        <br/>
                        <span class="fa fa-chevron-circle-right"></span>
                        <button id="button_useConfig">Enabled</button>
                    </div>
                </div>
                <pwm:script>
                <script type="text/javascript">
                    PWM_GLOBAL['startupFunctions'].push(function(){
                        require(["dijit/form/ToggleButton"],function(ToggleButton){
                            new ToggleButton({
                                id: 'button_defaultTrustStore',
                                iconClass:'dijitCheckBoxIcon',
                                showLabel: 'Enabled',
                                disabled: true,
                                checked: <%=configGuideBean.isCertsTrustedbyKeystore()%>
                            },'button_defaultTrustStore');

                            new ToggleButton({
                                id: 'button_useConfig',
                                iconClass:'dijitCheckBoxIcon',
                                showLabel: 'Enabled',
                                checked: <%=configGuideBean.isUseConfiguredCerts()%>,
                                onChange: function(){
                                    PWM_GUIDE.setUseConfiguredCerts(<%=!configGuideBean.isUseConfiguredCerts()%>);
                                }
                            },'button_useConfig');
                        });
                    });
                </script>
                </pwm:script>
            </div>
        </form>
        <% } %>
        <br/>
        <div class="buttonbar">
            <button class="btn" id="button_previous">
                <pwm:if test="showIcons"><span class="btn-icon fa fa-backward"></span></pwm:if>
                <pwm:display key="Button_Previous" bundle="Config"/>
            </button>
            &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
            <button class="btn" id="button_next" <%=enableNext?"":" disabled=\"disabled\""%>>
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
    });
</script>
</pwm:script>
<script type="text/javascript" src="<pwm:context/><pwm:url url="/public/resources/js/configguide.js"/>"></script>
<script type="text/javascript" src="<pwm:context/><pwm:url url="/public/resources/js/configmanager.js"/>"></script>
<% JspUtility.setFlag(pageContext, PwmRequest.Flag.HIDE_LOCALE);%>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
