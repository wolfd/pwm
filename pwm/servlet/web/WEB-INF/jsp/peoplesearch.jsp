<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2012 The PWM Project
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

<%@ page import="password.pwm.bean.PeopleSearchBean" %>
<%@ page import="password.pwm.servlet.PeopleSearchServlet" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final PwmSession pwmSession = PwmSession.getPwmSession(request); %>
<% final PeopleSearchBean peopleSearchBean = (PeopleSearchBean)pwmSession.getSessionBean(PeopleSearchBean.class); %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body onload="pwmPageLoadHandler();getObject('username').focus();" class="nihilo">
<div id="wrapper">
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_PeopleSearch"/>
    </jsp:include>
    <div id="centerbody">
        <form action="<pwm:url url='PeopleSearch'/>" method="post" enctype="application/x-www-form-urlencoded" name="search"
              onsubmit="handleFormSubmit('submitBtn');" onreset="handleFormClear();">
            <%@ include file="fragment/message.jsp" %>
            <p>&nbsp;</p>

            <p><pwm:Display key="Display_PeopleSearch"/></p>
            <input type="search" id="username" name="username" class="inputfield"
                   value="<%=peopleSearchBean.getSearchString()!=null?peopleSearchBean.getSearchString():""%>"/>
            <input type="submit" class="btn"
                   name="search"
                   value="<pwm:Display key="Button_Search"/>"
                   id="submitBtn"/>
            <input type="hidden"
                   name="processAction"
                   value="search"/>
            <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
            <% if (ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(password.pwm.config.PwmSetting.DISPLAY_CANCEL_BUTTON)) { %>
            <button style="visibility:hidden;" name="button" class="btn" id="button_cancel"
                    onclick="window.location='<%=request.getContextPath()%>/public/<pwm:url url='CommandServlet'/>?processAction=continue';return false">
                <pwm:Display key="Button_Cancel"/>
            </button>
            <script type="text/javascript">getObject('button_cancel').style.visibility = 'visible';</script>
            <% } %>
        </form>
        <br class="clear"/>
        <% final PeopleSearchServlet.PeopleSearchResults searchResults = peopleSearchBean.getSearchResults(); %>
        <% if (searchResults != null) { %>
        <div style="max-height: 400px; overflow: auto;">
            <table>
                <tr>
                    <% for (final String keyName : searchResults.getHeaders()) { %>
                    <td class="key" style="text-align: left; white-space: nowrap;">
                        <%=keyName%>
                    </td>
                    <% } %>
                </tr>
                <% for (final String userDN: searchResults.getResults().keySet()) { %>
                <tr>
                    <% for (final String attribute : searchResults.getAttributes()) { %>
                    <% final String value = searchResults.getResults().get(userDN).get(attribute); %>
                    <% final String userKey = PeopleSearchServlet.makeUserDetailKey(userDN,pwmSession); %>
                    <td id="userDN-<%=userDN%>">
                        <span onclick="loadDetails('<%=userKey%>');">
                        <%= value == null || value.length() < 1 ? "&nbsp;" : value %>
                        </span>
                    </td>
                    <% } %>
                </tr>
                <% } %>
            </table>
        </div>
        <div style="width:100%; text-align: center">
            <%=searchResults.isSizeExceeded()?"Search Results exceeded maximum search size.":""%>
        </div>
        <% } %>
    </div>
    <br class="clear"/>
</div>
<script>
    function loadDetails(userKey) {
        showWaitDialog();
        window.location="PeopleSearch?pwmFormID=<%=Helper.buildPwmFormID(pwmSession.getSessionStateBean())%>&processAction=detail&userKey=" + userKey;
    }
</script>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
