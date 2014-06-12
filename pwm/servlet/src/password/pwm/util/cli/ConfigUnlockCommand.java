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

package password.pwm.util.cli;

import password.pwm.PwmConstants;
import password.pwm.config.ConfigurationReader;
import password.pwm.config.StoredConfiguration;

import java.io.File;

public class ConfigUnlockCommand extends AbstractCliCommand {
    public void doCommand()
            throws Exception
    {
        final ConfigurationReader configurationReader = new ConfigurationReader(new File(PwmConstants.DEFAULT_CONFIG_FILE_FILENAME));
        final StoredConfiguration storedConfiguration = configurationReader.getStoredConfiguration();
        storedConfiguration.writeConfigProperty(
                StoredConfiguration.ConfigProperty.PROPERTY_KEY_CONFIG_IS_EDITABLE,Boolean.toString(true));
        configurationReader.saveConfiguration(storedConfiguration, cliEnvironment.getPwmApplication());
        out("success");
    }

    @Override
    public CliParameters getCliParameters()
    {
        CliParameters cliParameters = new CliParameters();
        cliParameters.commandName = "ConfigUnlock";
        cliParameters.description = "Unlock a configuration, allows config to be edited without LDAP authentication.";
        cliParameters.needsPwmApplication = true;
        cliParameters.readOnly = true;
        return cliParameters;
    }
}
