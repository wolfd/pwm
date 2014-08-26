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

package password.pwm.util;

import org.apache.commons.lang3.StringEscapeUtils;

public abstract class StringUtil {
    public static String escapeJS(final String input) {
        return StringEscapeUtils.escapeEcmaScript(input);
    }

    public static String escapeHtml(final String input)
    {
        return StringEscapeUtils.escapeHtml4(input);
    }

    public static String escapeCsv(final String input)
    {
        return StringEscapeUtils.escapeCsv(input);
    }

    public static String escapeJava(final String input)
    {
        return StringEscapeUtils.escapeJava(input);
    }

    public static String escapeXml(final String input)
    {
        return StringEscapeUtils.escapeXml11(input);
    }
}
