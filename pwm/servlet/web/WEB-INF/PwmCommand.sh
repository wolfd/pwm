#!/bin/sh
# PwmCommand.sh
#
# This script can be used to execute the PWM command line tool.
# It must be run from within the pwm/WEB-INF directory.
#
# Krowten's fault

if [ -z "$JAVA_HOME" ]; then
echo "JAVA_HOME variable must be set to a valid Java JDK or JRE"
exit 1
fi

CLASSPATH=$(for i in lib/*.jar ; do echo -n $i: ; done).:classes

$JAVA_HOME/jre/bin/java -cp $CLASSPATH password.pwm.util.MainClass $1 $2 $3 $4 $5 $6 $7 $8 $9
