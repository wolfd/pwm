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

package password.pwm.wordlist;

import password.pwm.PwmConstants;
import password.pwm.PwmService;
import password.pwm.PwmSession;


public interface Wordlist extends PwmService {

    static final String KEY_STATUS = "STATUS";
    static final String KEY_LASTLINE = "LASTLINE";
    static final String KEY_VERSION = "VERSION";
    static final String KEY_CHECKSUM = "CHECKSUM";
    static final String KEY_ELAPSEDSECONDS = "RUNTIME";
    static final String KEY_SIZE = "SIZE";

    // string used as db version checksum, if different then value in db, db will be cleared.
    static final String VALUE_VERSION = "pwm-wordlist-db-10-a5" + "_" + PwmConstants.BUILD_NUMBER + "_" + PwmConstants.PWM_VERSION;

    public boolean containsWord(final PwmSession pwmSession, final String word);

    int size();
}
