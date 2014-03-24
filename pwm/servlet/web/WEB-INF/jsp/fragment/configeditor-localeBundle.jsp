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

<%@ page import="org.apache.commons.lang.StringEscapeUtils" %>
<%@ page import="password.pwm.ContextManager" %>
<%@ page import="password.pwm.PwmConstants" %>
<%@ page import="password.pwm.bean.ConfigEditorCookie" %>
<%@ page import="password.pwm.servlet.ConfigEditorServlet" %>
<%@ page import="java.util.Collections" %>
<%@ page import="java.util.Locale" %>
<%@ page import="java.util.ResourceBundle" %>
<%@ page import="java.util.TreeSet" %>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final ConfigEditorCookie cookie = ConfigEditorServlet.readConfigEditorCookie(request, response); %>
<% final PwmConstants.EDITABLE_LOCALE_BUNDLES bundleName = cookie.getLocaleBundle(); %>
<% final ResourceBundle bundle = ResourceBundle.getBundle(bundleName.getTheClass().getName()); %>
<script type="text/javascript">
    var LOAD_TRACKER = new Array();
</script>
<div>
    <pwm:Display key="Display_ConfigEditorLocales" bundle="Config" value1="<%=PwmConstants.PWM_URL_HOME%>"/>
</div>
<% for (final String key : new TreeSet<String>(Collections.list(bundle.getKeys()))) { %>
<% final boolean isDefault = password.pwm.PwmSession.getPwmSession(session).getConfigManagerBean().getConfiguration().readLocaleBundleMap(bundleName.getTheClass().getName(),key).isEmpty();%>
<% if (!isDefault) { %>
<script type="text/javascript">
    PWM_GLOBAL['startupFunctions'].push(function(){
        LOAD_TRACKER.push('<%=key%>');
    });
</script>
<% } %>
<div id="titlePane_<%=key%>" style="margin-top:0; padding-top:0; border-top:0">
    <div class="message message-info" style="width: 580px; font-weight: bolder; font-family: Trebuchet MS,sans-serif">
        <% if (isDefault) { %>
        <button id="loadButton-localeBundle-<%=bundleName%>-<%=key%>">Edit Text</button>
        <script type="text/javascript">
            PWM_GLOBAL['startupFunctions'].push(function(){
                require(["dijit/form/Button"],function(){
                    new dijit.form.Button({
                        onClick: function(){doLazyLoad('<%=key%>');this.destroy()}
                    },'loadButton-localeBundle-<%=bundleName%>-<%=key%>');
                });
            });
        </script>
        <% } %>
        <label id="label_<%=key%>" for="value_<%=key%>"><%=key%></label>
        <div class="icon_button fa fa-undo" title="Reset" id="resetButton-localeBundle-<%=bundleName%>-<%=key%>" onclick="handleResetClick('localeBundle-<%=bundleName%>-<%=key%>')"
             style="visibility:hidden; vertical-align:bottom; float: right; cursor: pointer"></div>

    </div>
    <div class="message message-info" style="width: 580px; background: white;">
        <table id="table_<%=key%>" style="border-width:0" width="500">
            <% if (isDefault) { %>
            <% for(final Locale loopLocale : ContextManager.getPwmApplication(session).getConfig().getKnownLocales()) { %>
            <tr style="border-width:0">
                <td style="border-width:0">
                    <%= "".equals(loopLocale.toString()) ? "Default" : loopLocale.getDisplayName(loopLocale) %>
                </td>
                <td style="border-width:0; width: 100%; color: #808080; margin: 2px">
                    <%= StringEscapeUtils.escapeHtml(ResourceBundle.getBundle(bundleName.getTheClass().getName(),loopLocale).getString(key)) %>
                </td>
            </tr>
            <% } %>
            <% } else { %>
            <tr style="border-width:0">
                <td style="border-width:0">
                    <pwm:Display key="Display_PleaseWait"/>
                </td>
            </tr>
            <% } %>
        </table>
    </div>
</div>
<br/>
<% } %>
<script type="text/javascript">
    function doLazyLoad(key) {
        var waitMsg = PWM_MAIN.getObject('waitMsg');
        if (waitMsg != null) {
            waitMsg.innerHTML = 'Loading display values.... ' + LOAD_TRACKER.length + " remaining.";
        }

        var settingKey = 'localeBundle-' + '<%=bundleName%>' + '-' + key;
        LocaleTableHandler.initLocaleTable('table_' + key, settingKey, '.*', 'LOCALIZED_TEXT_AREA');
        if (LOAD_TRACKER.length > 0) {
            setTimeout(function(){
                doLazyLoad(LOAD_TRACKER.pop());
            },100); // time between element reads
        } else {
            PWM_MAIN.closeWaitDialog();
        }
    }

    PWM_GLOBAL['startupFunctions'].push(function(){
        if (LOAD_TRACKER.length > 0) {
            PWM_MAIN.showWaitDialog(PWM_MAIN.showString('Display_PleaseWait'),'<div id="waitMsg">Loading custom display values.......</div>',function(){
                LOAD_TRACKER.reverse();
                doLazyLoad(LOAD_TRACKER.pop());
            });
        }
    });
</script>

