/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2012 The PWM Project
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package password.pwm.ws.server.rest;

import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.cr.*;
import com.novell.ldapchai.cr.bean.ChallengeBean;
import com.novell.ldapchai.exception.ChaiValidationException;
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.Permission;
import password.pwm.PwmPasswordPolicy;
import password.pwm.config.PwmSetting;
import password.pwm.error.*;
import password.pwm.i18n.Message;
import password.pwm.util.Helper;
import password.pwm.util.operations.CrUtility;
import password.pwm.util.stats.Statistic;
import password.pwm.ws.server.RestRequestBean;
import password.pwm.ws.server.RestResultBean;
import password.pwm.ws.server.RestServerHelper;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;

@Path("/challenges")
public class RestChallengesServer {
    public static class JsonChallengesData implements Serializable {
        public String username;
        public List<ChallengeBean> challenges;
        public List<ChallengeBean> helpdeskChallenges;
        public int minimumRandoms;

        public ChaiResponseSet toResponseSet(final Locale locale, final String csIdentifier)
                throws PwmOperationalException
        {
            try {
                return ChaiCrFactory.newChaiResponseSet(this.challenges,this.helpdeskChallenges,locale,this.minimumRandoms,csIdentifier);
            } catch (ChaiValidationException e) {
                throw new PwmOperationalException(PwmError.CONFIG_FORMAT_ERROR,e.getMessage());
            }
        }
    }

    @Context
    HttpServletRequest request;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public javax.ws.rs.core.Response doHtmlRedirect() throws URISyntaxException {
        final URI uri = javax.ws.rs.core.UriBuilder.fromUri("../rest.jsp?forwardedFromRestServer=true").build();
        return javax.ws.rs.core.Response.temporaryRedirect(uri).build();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public String doFormGetChallengeData(
            @QueryParam("answers") final boolean answers,
            @QueryParam("helpdesk") final boolean helpdesk,
            @QueryParam("username") final String username
    )
            throws PwmUnrecoverableException
    {
        final RestRequestBean restRequestBean;
        try {
            restRequestBean = RestServerHelper.initializeRestRequest(request, true, username);
        } catch (PwmUnrecoverableException e) {
            return RestServerHelper.outputJsonErrorResult(e.getErrorInformation(), request);
        }

        try {
            if (answers && !restRequestBean.getPwmApplication().getConfig().readSettingAsBoolean(PwmSetting.ENABLE_WEBSERVICES_READANSWERS)) {
                throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_SERVICE_NOT_AVAILABLE,"retrieval of answers is not permitted"));
            }

            final ChaiProvider actorProvider = restRequestBean.getPwmSession().getSessionManager().getChaiProvider();
            final String userDN = restRequestBean.getUserDN() != null ? restRequestBean.getUserDN() : restRequestBean.getPwmSession().getUserInfoBean().getUserDN();
            final ChaiUser chaiUser = ChaiFactory.createChaiUser(userDN, actorProvider);
            final ResponseSet responseSet = CrUtility.readUserResponseSet(restRequestBean.getPwmSession(), restRequestBean.getPwmApplication(), chaiUser);
            final JsonChallengesData jsonData = new JsonChallengesData();
            jsonData.username = chaiUser.getEntryDN();
            jsonData.challenges = responseSet.asChallengeBeans(answers);
            jsonData.minimumRandoms = responseSet.getChallengeSet().getMinRandomRequired();
            if (helpdesk) {
                jsonData.helpdeskChallenges = responseSet.asHelpdeskChallengeBeans(answers);
            }

            if (!restRequestBean.isExternal()) {
                restRequestBean.getPwmApplication().getStatisticsManager().incrementValue(Statistic.REST_CHALLENGES);
            }

            RestResultBean resultBean = new RestResultBean();
            resultBean.setData(jsonData);
            return resultBean.toJson();
        } catch (PwmException e) {
            return RestServerHelper.outputJsonErrorResult(e.getErrorInformation(), request);
        } catch (Exception e) {
            final String errorMsg = "unexpected error building json response: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
            return RestServerHelper.outputJsonErrorResult(errorInformation, request);
        }
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public String doSetChallengeDataJson(
            final JsonChallengesData jsonInput
    )
    {
        final RestRequestBean restRequestBean;
        try {
            restRequestBean = RestServerHelper.initializeRestRequest(request, true, jsonInput.username);
        } catch (PwmUnrecoverableException e) {
            return RestServerHelper.outputJsonErrorResult(e.getErrorInformation(), request);
        }

        try {
            if (!Permission.checkPermission(Permission.SETUP_RESPONSE, restRequestBean.getPwmSession(), restRequestBean.getPwmApplication())) {
                throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_UNAUTHORIZED,"actor does not have required permission"));
            }

            final ChaiUser chaiUser;
            final String userGUID;
            final String csIdentifer;
            if (restRequestBean.getUserDN() == null) {
                chaiUser = restRequestBean.getPwmSession().getSessionManager().getActor();
                userGUID = restRequestBean.getPwmSession().getUserInfoBean().getUserGuid();
                csIdentifer = restRequestBean.getPwmSession().getUserInfoBean().getChallengeSet().getIdentifier();
            } else {
                final String userDN = restRequestBean.getUserDN();
                final ChaiProvider actorProvider = restRequestBean.getPwmSession().getSessionManager().getChaiProvider();
                chaiUser = ChaiFactory.createChaiUser(userDN, actorProvider);
                userGUID = Helper.readLdapGuidValue(restRequestBean.getPwmApplication(),userDN);
                final ChallengeSet challengeSet = CrUtility.readUserChallengeSet(
                        restRequestBean.getPwmSession(),
                        restRequestBean.getPwmApplication().getConfig(),
                        chaiUser,
                        PwmPasswordPolicy.defaultPolicy(),
                        request.getLocale());
                csIdentifer = challengeSet.getIdentifier();
            }

            final ChaiResponseSet chaiResponseSet = jsonInput.toResponseSet(request.getLocale(), csIdentifer);
            CrUtility.writeResponses(
                    restRequestBean.getPwmSession(),
                    restRequestBean.getPwmApplication(),
                    chaiUser,
                    userGUID,
                    chaiResponseSet
            );

            final String successMsg = Message.SUCCESS_SETUP_RESPONSES.getLocalizedMessage(request.getLocale(),restRequestBean.getPwmApplication().getConfig());
            RestResultBean resultBean = new RestResultBean();
            resultBean.setError(false);
            resultBean.setSuccessMessage(successMsg);
            return resultBean.toJson();
        } catch (PwmException e) {
            return RestServerHelper.outputJsonErrorResult(e.getErrorInformation(), request);
        } catch (Exception e) {
            final String errorMsg = "unexpected error reading json input: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
            return RestServerHelper.outputJsonErrorResult(errorInformation, request);
        }
    }

    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    public String doDeleteChallengeData(
            @QueryParam("username") final String username
    )
    {
        final RestRequestBean restRequestBean;
        try {
            restRequestBean = RestServerHelper.initializeRestRequest(request, true, username);
        } catch (PwmUnrecoverableException e) {
            return RestServerHelper.outputJsonErrorResult(e.getErrorInformation(), request);
        }

        try {
            if (!Permission.checkPermission(Permission.SETUP_RESPONSE, restRequestBean.getPwmSession(), restRequestBean.getPwmApplication())) {
                throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_UNAUTHORIZED,"actor does not have required permission"));
            }

            final ChaiUser chaiUser;
            final String userGUID;
            if (restRequestBean.getUserDN() == null) {
                chaiUser = restRequestBean.getPwmSession().getSessionManager().getActor();
                userGUID = restRequestBean.getPwmSession().getUserInfoBean().getUserGuid();
            } else {
                final String userDN = restRequestBean.getUserDN();
                final ChaiProvider actorProvider = restRequestBean.getPwmSession().getSessionManager().getChaiProvider();
                chaiUser = ChaiFactory.createChaiUser(userDN, actorProvider);
                userGUID = Helper.readLdapGuidValue(restRequestBean.getPwmApplication(),userDN);
            }

            CrUtility.clearResponses(
                    restRequestBean.getPwmSession(),
                    restRequestBean.getPwmApplication(),
                    chaiUser,
                    userGUID
            );
            final String successMsg = Message.SUCCESS_CLEAR_RESPONSES.getLocalizedMessage(request.getLocale(),restRequestBean.getPwmApplication().getConfig());
            RestResultBean resultBean = new RestResultBean();
            resultBean.setError(false);
            resultBean.setSuccessMessage(successMsg);
            return resultBean.toJson();
        } catch (PwmException e) {
            return RestServerHelper.outputJsonErrorResult(e.getErrorInformation(), request);
        } catch (Exception e) {
            final String errorMsg = "unexpected error delete responses: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
            return RestServerHelper.outputJsonErrorResult(errorInformation, request);
        }
    }
}
