/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2010 The PWM Project
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

package password.pwm.servlet;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiProvider;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import password.pwm.Helper;
import password.pwm.PwmConstants;
import password.pwm.PwmSession;
import password.pwm.Validator;
import password.pwm.bean.ConfigManagerBean;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredConfiguration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.util.PwmLogger;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

public class ConfigManagerServlet extends TopServlet {

    private static final PwmLogger LOGGER = PwmLogger.getLogger(ConfigManagerServlet.class);

    private static final int MAX_INPUT_LENGTH = 1024 * 10;
    private static final int MIN_SESSION_INACTIVITY_TIMER = 30 * 60; // 30 minutes

    protected void processRequest(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ServletException, IOException, ChaiUnavailableException, PwmException
    {
        final String processActionParam = Validator.readStringFromRequest(req, PwmConstants.PARAM_ACTION_REQUEST, MAX_INPUT_LENGTH);
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final ConfigManagerBean configManagerBean = pwmSession.getConfigManagerBean();

        //clear any errors in the session's state bean
        pwmSession.getSessionStateBean().setSessionError(null);

        if (req.getSession().getMaxInactiveInterval() < MIN_SESSION_INACTIVITY_TIMER) {
            req.getSession().setMaxInactiveInterval(MIN_SESSION_INACTIVITY_TIMER);
        }

        if (configManagerBean.getConfiguration() == null) {
            configManagerBean.setConfiguration(StoredConfiguration.getDefaultConfiguration());
        }

        if (processActionParam.length() > 0) {
            Validator.validatePwmFormID(req);
            if ("readSetting".equalsIgnoreCase(processActionParam)) {
                this.readSetting(req,resp);
                return;
            } else if ("writeSetting".equalsIgnoreCase(processActionParam)) {
                this.writeSetting(req);
                return;
            } else if ("generateXml".equalsIgnoreCase(processActionParam)) {
                if (doGenerateXml(req,resp)) {
                    return;
                }
            } else if ("testLdapConnect".equalsIgnoreCase(processActionParam)) {
                doTestLdapConnect(req);
            } else if ("resetConfig".equalsIgnoreCase(processActionParam)) {
                configManagerBean.setConfiguration(StoredConfiguration.getDefaultConfiguration());
                configManagerBean.setEditorMode(false);
                pwmSession.getSessionStateBean().setSessionError(new ErrorInformation(PwmError.CONFIG_RESET_SUCCESS));
                LOGGER.debug(pwmSession,"configuration reset");
            } else if ("switchToActionMode".equalsIgnoreCase(processActionParam)) {
                configManagerBean.setEditorMode(false);
                LOGGER.debug(pwmSession,"switching to action mode");
            } else if ("switchToEditMode".equalsIgnoreCase(processActionParam)) {
                configManagerBean.setEditorMode(true);
                LOGGER.debug(pwmSession,"switching to edit mode");
            }
        }

        forwardToJSP(req, resp);
    }

    private void readSetting(
            final HttpServletRequest req,
            final HttpServletResponse resp
    ) throws IOException, PwmException {
        final ConfigManagerBean configManagerBean = PwmSession.getPwmSession(req).getConfigManagerBean();
        final StoredConfiguration storedConfig = configManagerBean.getConfiguration();

        final String bodyString = Helper.readRequestBody(req, MAX_INPUT_LENGTH);
        final JSONObject srcMap = (JSONObject) JSONValue.parse(bodyString);
        final Map<String,Object> returnMap = new HashMap<String,Object>();

        if (srcMap != null) {
            final String key = String.valueOf(srcMap.get("key"));
            final PwmSetting theSetting = PwmSetting.forKey(key);
            final Object returnValue;
            if (srcMap.containsKey("locale")) {
                final String locale = String.valueOf(srcMap.get("locale"));
                returnValue = storedConfig.readLocalizedStringSetting(theSetting).get(locale);
                returnMap.put("locale",locale);
            } else {
                switch (theSetting.getSyntax()) {
                    case STRING_ARRAY:
                    {
                        final List<String> values = storedConfig.readStringArraySetting(theSetting);
                        final Map<String,String> outputMap = new TreeMap<String,String>();
                        for (int i = 0 ; i < values.size() ; i++) {
                            outputMap.put(String.valueOf(i), values.get(i));
                        }
                        returnValue = outputMap;
                    }
                    break;

                    case LOCALIZED_STRING_ARRAY:
                    {
                        final Map<String,List<String>> values = storedConfig.readLocalizedStringArraySetting(theSetting);
                        final Map<String,Map<String,String>> outputMap = new TreeMap<String,Map<String,String>>();
                        for (final String localeKey : values.keySet()) {
                            final List<String> loopValues = values.get(localeKey);
                            final Map<String,String> loopMap = new TreeMap<String,String>();
                            for (int i = 0 ; i < loopValues.size() ; i++) {
                                loopMap.put(String.valueOf(i), loopValues.get(i));
                            }
                            outputMap.put(localeKey, loopMap);
                        }
                        returnValue = outputMap;
                    }
                    break;

                    case LOCALIZED_STRING:
                    case LOCALIZED_TEXT_AREA:
                        returnValue = new TreeMap<String,String>(storedConfig.readLocalizedStringSetting(theSetting));
                        break;

                    default:
                        returnValue = storedConfig.readSetting(theSetting);
                }
            }

            returnMap.put("key", theSetting.getKey());
            returnMap.put("value", returnValue);
            final String outputString = JSONObject.toJSONString(returnMap);
            resp.setContentType("application/json;charset=utf-8");
            resp.getWriter().print(outputString);
        }
    }

    private void writeSetting(
            final HttpServletRequest req
    ) throws IOException, PwmException {
        Validator.validatePwmFormID(req);
        final ConfigManagerBean configManagerBean = PwmSession.getPwmSession(req).getConfigManagerBean();
        final StoredConfiguration storedConfig= configManagerBean.getConfiguration();

        final String bodyString = Helper.readRequestBody(req, MAX_INPUT_LENGTH);

        final JSONObject srcMap = (JSONObject) JSONValue.parse(bodyString);

        if (srcMap != null) {
            final String key = String.valueOf(srcMap.get("key"));
            final String value = String.valueOf(srcMap.get("value"));
            final PwmSetting setting = PwmSetting.forKey(key);

            switch (setting.getSyntax()) {
                case STRING_ARRAY:
                {
                    final JSONObject inputMap = (JSONObject) JSONValue.parse(value);
                    final Map<String,String> outputMap = new TreeMap<String,String>();
                    for (final Object keyObject: inputMap.keySet()) {
                        outputMap.put(String.valueOf(keyObject),String.valueOf(inputMap.get(keyObject)));
                    }
                    storedConfig.writeStringArraySetting(setting,new ArrayList<String>(outputMap.values()));
                }
                break;

                case LOCALIZED_STRING:
                case LOCALIZED_TEXT_AREA:
                {
                    final JSONObject inputMap = (JSONObject) JSONValue.parse(value);
                    final Map<String,String> outputMap = new TreeMap<String,String>();
                    for (final Object keyObject: inputMap.keySet()) {
                        outputMap.put(String.valueOf(keyObject),String.valueOf(inputMap.get(keyObject)));
                    }
                    storedConfig.writeLocalizedSetting(setting,outputMap);
                }
                break;

                case LOCALIZED_STRING_ARRAY:
                {
                    final JSONObject inputMap = (JSONObject) JSONValue.parse(value);
                    final Map<String,List<String>> outputMap = new TreeMap<String,List<String>>();
                    for (final Object localeKeyObject: inputMap.keySet()) {
                        final JSONObject localeMap = (JSONObject)inputMap.get(localeKeyObject);

                        final TreeMap<String,String> sortedMap = new TreeMap<String,String>();
                        for (final Object iterationKey : localeMap.keySet()) {
                            sortedMap.put(iterationKey.toString(), localeMap.get(iterationKey).toString());
                        }

                        final List<String> loopList = new ArrayList<String>();
                        for (final String loopValue : sortedMap.values()) loopList.add(loopValue);

                        outputMap.put(localeKeyObject.toString(),loopList);
                    }
                    storedConfig.writeLocalizedStringArraySetting(setting,outputMap);
                }
                break;

                default:
                    storedConfig.writeSetting(setting, value);
            }
        }
    }

    private boolean doGenerateXml(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException, PwmException
    {
        final ConfigManagerBean configManagerBean = PwmSession.getPwmSession(req).getConfigManagerBean();
        final StoredConfiguration configuration = configManagerBean.getConfiguration();

        final String errorString = configuration.checkValuesForErrors();
        if (errorString != null) {
            PwmSession.getPwmSession(req).getSessionStateBean().setSessionError(new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR,errorString,errorString));
            return false;
        }

        final String output = configuration.toXml();
        resp.setHeader("content-disposition", "attachment;filename=PwmConfiguration.xml");
        resp.setContentType("text/xml;charset=utf-8");
        resp.getWriter().print(output);
        return true;
    }

    private void doTestLdapConnect(
            final HttpServletRequest req
    )
            throws IOException, ServletException, PwmException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);

        if (pwmSession.getConfig() != null) {
            final String errorString = "Test functionality is only available on unconfigured server";
            PwmSession.getPwmSession(req).getSessionStateBean().setSessionError(new ErrorInformation(PwmError.CONFIG_LDAP_FAILIRE,errorString,errorString));
            return;
        }


        final ConfigManagerBean configManagerBean = pwmSession.getConfigManagerBean();
        final StoredConfiguration storedConfiguration = configManagerBean.getConfiguration();

        {
            final String errorString = storedConfiguration.checkValuesForErrors();
            if (errorString != null) {
                PwmSession.getPwmSession(req).getSessionStateBean().setSessionError(new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR,errorString,errorString));
                return;
            }
        }

        final Configuration config = new Configuration(storedConfiguration);

        ChaiProvider chaiProvider = null;
        try {
            chaiProvider = Helper.createChaiProvider(
                    config,
                    config.readSettingAsString(PwmSetting.LDAP_PROXY_USER_DN),
                    config.readSettingAsString(PwmSetting.LDAP_PROXY_USER_PASSWORD),
                    PwmConstants.DEFAULT_LDAP_IDLE_TIMEOUT_MS);
            chaiProvider.getDirectoryVendor();
            PwmSession.getPwmSession(req).getSessionStateBean().setSessionError(new ErrorInformation(PwmError.CONFIG_LDAP_SUCCESS));
        } catch (Exception e) {
            final String errorString = "error connecting to ldap server: " + e.getMessage();
            PwmSession.getPwmSession(req).getSessionStateBean().setSessionError(new ErrorInformation(PwmError.CONFIG_LDAP_FAILIRE,errorString,errorString));
        } finally {
            if (chaiProvider != null) {
                try {
                    chaiProvider.close();
                } catch (Exception e) {
                    // don't care.
                }
            }
        }
    }

    static void forwardToJSP(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException
    {
        final ServletContext servletContext = req.getSession().getServletContext();
        final ConfigManagerBean configManagerBean = PwmSession.getPwmSession(req).getConfigManagerBean();
        if (configManagerBean.isEditorMode()) {
            servletContext.getRequestDispatcher('/' + PwmConstants.URL_JSP_CONFIG_MANAGER_EDITOR).forward(req, resp);
        } else {
            servletContext.getRequestDispatcher('/' + PwmConstants.URL_JSP_CONFIG_MANAGER).forward(req, resp);
        }
    }

}
