/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
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

package password.pwm.config.value;

import password.pwm.config.StoredValue;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.JsonUtil;
import password.pwm.util.SecureHelper;

import java.io.Serializable;
import java.util.Locale;

public abstract class AbstractValue implements StoredValue {
    public String toString() {
        return toDebugString(false, null);
    }

    public String toDebugString(boolean prettyFormat, Locale locale) {
        return prettyFormat
                ? JsonUtil.serialize((Serializable)this.toNativeObject(), JsonUtil.Flag.PrettyPrint)
                : JsonUtil.serialize((Serializable)this.toNativeObject());
    }

    public boolean requiresStoredUpdate()
    {
        return false;
    }

    @Override
    public int currentSyntaxVersion()
    {
        return 0;
    }

    @Override
    public String valueHash() throws PwmUnrecoverableException {
        return SecureHelper.hash(JsonUtil.serialize((Serializable)this.toNativeObject()));
    }
}
