/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2014 The PWM Project
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

import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.ContextManager;
import password.pwm.Permission;
import password.pwm.PwmApplication;
import password.pwm.PwmSession;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthMonitor;
import password.pwm.health.HealthRecord;
import password.pwm.health.HealthStatus;
import password.pwm.util.PwmLogger;
import password.pwm.util.stats.Statistic;
import password.pwm.ws.server.RestRequestBean;
import password.pwm.ws.server.RestResultBean;
import password.pwm.ws.server.RestServerHelper;
import password.pwm.ws.server.ServicePermissions;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

@Path("/health")
public class RestHealthServer {
    final private static PwmLogger LOGGER = PwmLogger.getLogger(RestHealthServer.class);


    public static class JsonOutput implements Serializable {
        public Date timestamp;
        public String overall;
        public List<HealthRecordBean> records;
    }

    public static class HealthRecordBean implements Serializable {
        public HealthStatus status;
        public String topic;
        public String detail;

        public static HealthRecordBean fromHealthRecord(HealthRecord healthRecord, Locale locale, final Configuration config) {
            final HealthRecordBean bean = new HealthRecordBean();
            bean.status = healthRecord.getStatus();
            bean.topic = healthRecord.getTopic(locale,config);
            bean.detail = healthRecord.getDetail(locale,config);
            return bean;
        }

        public static List<HealthRecordBean> fromHealthRecords(final List<HealthRecord> healthRecords, final Locale locale, final Configuration config) {
            final List<HealthRecordBean> beanList = new ArrayList<HealthRecordBean>();
            for (HealthRecord record : healthRecords) {
                if (record != null) {
                    beanList.add(fromHealthRecord(record, locale, config));
                }
            }
            return beanList;
        }
    }

    @Context
    HttpServletRequest request;

    @Context
    ServletContext context;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String doPwmHealthPlainGet(
            @QueryParam("refreshImmediate") final boolean requestImmediateParam
    ) {
        final ServicePermissions servicePermissions = figurePermissions();
        final RestRequestBean restRequestBean;
        try {
            restRequestBean = RestServerHelper.initializeRestRequest(request, servicePermissions, null);
        } catch (PwmUnrecoverableException e) {
            RestServerHelper.handleNonJsonErrorResult(e.getErrorInformation());
            return null;
        }

        try {
            processCheckImeddiateFlag(restRequestBean.getPwmApplication(), restRequestBean.getPwmSession(), requestImmediateParam);
            final String resultString = restRequestBean.getPwmApplication().getHealthMonitor().getMostSevereHealthStatus().toString() + "\n";
            if (restRequestBean.isExternal()) {
                restRequestBean.getPwmApplication().getStatisticsManager().incrementValue(Statistic.REST_HEALTH);
            }
            return resultString;
        } catch (Exception e) {
            final String errorMessage = "unexpected error executing web service: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMessage);
            RestServerHelper.handleNonJsonErrorResult(errorInformation);
            return null;
        }
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response doPwmHealthJsonGet(
            @QueryParam("refreshImmediate") final boolean requestImmediateParam
    ) {
        final ServicePermissions servicePermissions = figurePermissions();
        final RestRequestBean restRequestBean;
        try {
            restRequestBean = RestServerHelper.initializeRestRequest(request, servicePermissions, null);
        } catch (PwmUnrecoverableException e) {
            return RestResultBean.fromError(e.getErrorInformation()).asJsonResponse();
        }

        try {
            processCheckImeddiateFlag(restRequestBean.getPwmApplication(), restRequestBean.getPwmSession(), requestImmediateParam);
            final JsonOutput jsonOutput = processGetHealthCheckData(restRequestBean.getPwmApplication(),restRequestBean.getPwmSession().getSessionStateBean().getLocale());
            if (restRequestBean.isExternal()) {
                restRequestBean.getPwmApplication().getStatisticsManager().incrementValue(Statistic.REST_HEALTH);
            }
            final RestResultBean restResultBean = new RestResultBean();
            restResultBean.setData(jsonOutput);
            return restResultBean.asJsonResponse();
        } catch (PwmException e) {
            return RestResultBean.fromError(e.getErrorInformation(), restRequestBean).asJsonResponse();
        } catch (Exception e) {
            final String errorMessage = "unexpected error executing web service: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMessage);
            return RestResultBean.fromError(errorInformation, restRequestBean).asJsonResponse();
        }
    }

    private static JsonOutput processGetHealthCheckData(
            final PwmApplication pwmApplication,
            final Locale locale
    )
            throws ChaiUnavailableException, IOException, ServletException, PwmUnrecoverableException
    {
        final HealthMonitor healthMonitor = pwmApplication.getHealthMonitor();
        final List<HealthRecord> healthRecords = new ArrayList(healthMonitor.getHealthRecords(false));
        final List<HealthRecordBean> healthRecordBeans = HealthRecordBean.fromHealthRecords(healthRecords,locale,pwmApplication.getConfig());
        final JsonOutput returnMap = new JsonOutput();
        returnMap.timestamp = healthMonitor.getLastHealthCheckDate();
        returnMap.overall = healthMonitor.getMostSevereHealthStatus().toString();
        returnMap.records = healthRecordBeans;
        return returnMap;
    }

    private static void processCheckImeddiateFlag(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final boolean refreshImmediate
    )
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        boolean doRefresh = false;
        if (refreshImmediate) {
            if (pwmApplication.getApplicationMode() == PwmApplication.MODE.CONFIGURATION) {
                doRefresh = true;
            } else {
                if (pwmSession.getSessionStateBean().isAuthenticated()) {
                    try {
                        doRefresh = Permission.checkPermission(Permission.PWMADMIN, pwmSession, pwmApplication);
                    } catch (Exception e) {
                        /* nooop */
                    }
                }
            }
        }

        if (doRefresh) {
            final HealthMonitor healthMonitor = pwmApplication.getHealthMonitor();
            healthMonitor.getHealthRecords(true);
        }
    }

    private ServicePermissions figurePermissions() {
        ServicePermissions servicePermissions = ServicePermissions.ADMIN_OR_CONFIGMODE;
        try {
            final Configuration config = ContextManager.getContextManager(context).getPwmApplication().getConfig();
            if (config.readSettingAsBoolean(PwmSetting.PUBLIC_HEALTH_STATS_WEBSERVICES)) {
                servicePermissions = ServicePermissions.PUBLIC;
            }
        } catch (PwmUnrecoverableException e) {
            LOGGER.error("unable to read service permissions, defaulting to non-public; error: " + e.getMessage());
        }
        return servicePermissions;
    }
}