/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2011 The PWM Project
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

/**
 * PasswordManagement.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis 1.4 Apr 22, 2006 (06:55:48 PDT) WSDL2Java emitter.
 */

package password.pwm.ws.client.novell.pwdmgt;

public interface PasswordManagement extends java.rmi.Remote {
    public password.pwm.ws.client.novell.pwdmgt.ForgotPasswordConfWSBean processForgotConf(password.pwm.ws.client.novell.pwdmgt.ProcessForgotConfRequest bodyIn) throws java.rmi.RemoteException;
    public password.pwm.ws.client.novell.pwdmgt.ForgotPasswordWSBean processUser(password.pwm.ws.client.novell.pwdmgt.ProcessUserRequest bodyIn) throws java.rmi.RemoteException;
    public password.pwm.ws.client.novell.pwdmgt.ForgotPasswordWSBean processChaRes(password.pwm.ws.client.novell.pwdmgt.ProcessChaResRequest bodyIn) throws java.rmi.RemoteException;
    public password.pwm.ws.client.novell.pwdmgt.ForgotPasswordWSBean processChgPwd(password.pwm.ws.client.novell.pwdmgt.ProcessChgPwdRequest bodyIn) throws java.rmi.RemoteException;
}
