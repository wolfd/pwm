# Integrating pwm with OpenLDAP on Debian Lenny #

## Introduction ##

Using pwm with OpenLDAP is not trivial, as there are tons of moving parts - especially if you want to use the Java Security Manager. This document tries to list the most important configuration steps needed to use pwm and OpenLDAP together on the following setup:

  * Debian Lenny
  * Tomcat 5.5.26 (from Debian repos)
  * OpenLDAP 2.4.11 (from Debian repos)
  * OpenJDK 1.6.0 (from Debian repos)
  * pwm 1.4.3

There may be some unintended omissions, so if you encounter _any_ trouble with these instructions, send mail to the pwm-general list to get help.

## Configuring OpenLDAP ##

OpenLDAP configuration is described in the [Administration Guide](AdminGuide#OpenLDAP_Integration.md).

## Configuring Tomcat ##

Here we use Tomcat 5.5.26 from Debian Lenny repositories. This version of Tomcat differs from the one available from Apache. For example, all the directories have been "Debianized" and Java Security Manager is turned on by default. Debian's Tomcat does not require many modifications besides configuring pwm-specific Java Security Manager rules (see below). You _may_ want to configure pwm/manager/admin webapps to listen only on specific interfaces, though. This is done by adding <webappname.xml> files to /etc/tomcat5.5/Catalina/localhost with contents like this:

```
<!-- Context configuration file for the PWM Web App - /etc/tomcat5.5/Catalina/localhost/pwm.xml -->
<Context path="/pwm" debug="5" privileged="false" allowLinking="true">

  <!-- See http://queuemetrics.com/faq.jsp#faq-055-intranet
  <Valve className="org.apache.catalina.valves.RemoteAddrValve"
         allow="10.74.22.*, 127.0.0.1"/>

</Context>
```


## Configuring Java Security Manager rules ##

Java Security Manager integration is described on [this page](JavaSecurityManagerIntegration.md).

## Configuring PWM ##

Last, but not least, we'll setup pwm. After downloading and unpacking pwm, descend to the pwm-1.4.3/servlet/web/WEB-INF directory and edit the file pwmServlet.properties. The file is thoroughly documented, so read the instructions carefully. There's not much if anything OpenLDAP-specific in the file, but here are a few important things you need to modify:

```
# This account needs to have write access to the subtree containing the user
# accounts. It's used in the initial phases of new user registration 
ldapProxyDN=cn=pwmadmin,dc=domain,dc=com
ldapProxyPassword=replace_with_your_password

# Root dn for user logins. This is separate from the ProxyDN used above, which
# is used to create the user into the directory initially. 
ldapContextlessLoginRoot=ou=Accounts,dc=domain,dc=com

# Make sure this matches your directory setup.
usernameSearchFilter=(&(objectClass=inetOrgPerson)(cn=%USERNAME%))

# Disable all edirectory-specific functionality
ldap.edirectory.enableNmas=false
ldap.edirectory.alwaysUseProxy=false
ldap.edirectory.readPasswordPolicies=false
ldap.edirectory.readChallengeSets=false
ldap.edirectory.storeNmasResponses=false

# Make sure this context matches your LDAP directory settings
newUser.createContext=ou=Accounts,dc=domain,dc=com

# Make sure this matches your LDAP directory settings. It might not, if you're
# identifying users with "uid" attribute in the posixAccount class
newUser.creationUniqueAttributes=cn
```

If you wish to disable some of the modules (e.g. "Activate User"), you need disable them in the pwmServlet.properties file. This does not, however, remove the links to these modules from the web interface. To do this, edit pwm-1.4.3/servlet/web/index.jsp file accordingly.

After making the modifications to pwm files, go to pwm-1.4.3/servlet directory and run

```
ant clean
ant makeWAR
```

This creates a deployable war file (pwm-1.4.3/servlet/build/pwm.war) which you can install to Tomcat either manually to /var/lib/tomcat5.5/webapps or by using the Tomcat Manager webapp.