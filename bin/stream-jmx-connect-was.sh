#! /bin/bash
if command -v realpath >/dev/null 2>&1; then
    SCRIPTPATH=`dirname $(realpath $0)`
else
    SCRIPTPATH=$( cd "$(dirname "$0")" ; pwd -P )
fi

### ---- WAS environment configuration ----
WAS_HOME="/opt/IBM/WebSphere/AppServer"
JAVA_HOME="$WAS_HOME/java"
WAS_PATH="$WAS_HOME/runtimes/*:$WAS_HOME/lib/webadmin/management.jar:$WAS_HOME/plugins/com.ibm.ws.runtime.jar"
MYCLIENTSAS="-Dcom.ibm.CORBA.ConfigURL=file:$SCRIPTPATH/../config/sas.client.props"
MYCLIENTSSL="-Dcom.ibm.SSL.ConfigURL=file:$SCRIPTPATH/../config/ssl.client.props"
### ----------------------------------------

TOOLS_PATH="$JAVA_HOME/lib/tools.jar"
### ---- appending libs path with WAS libs ----
LIBPATH="$SCRIPTPATH/../*:$SCRIPTPATH/../lib/*:$TOOLS_PATH:$WAS_PATH"
### -------------------------------------------

TNT4JOPTS="-Dorg.slf4j.simpleLogger.defaultLogLevel=debug -Dcom.jkoolcloud.tnt4j.stream.jmx.agent.trace=true -Dtnt4j.dump.on.vm.shutdown=true -Dtnt4j.dump.on.exception=true -Dtnt4j.dump.provider.default=true"
# tnt4j file
if [ -z "$TNT4J_PROPERTIES" ]; then
  TNT4J_PROPERTIES="$SCRIPTPATH/../config/tnt4j.properties"
fi
TNT4JOPTS="$TNT4JOPTS -Dtnt4j.config=$TNT4J_PROPERTIES"

### ---- adding WAS specific JMX sampler options ----
TNT4JOPTS="$TNT4JOPTS -Dcom.jkoolcloud.tnt4j.stream.jmx.agent.forceObjectName=true -Dcom.jkoolcloud.tnt4j.stream.jmx.sampler.factory=com.jkoolcloud.tnt4j.stream.jmx.impl.WASSamplerFactory"
### -------------------------------------------------

# NOTE: Double exclamation mark in bash has a special meaning (previous command)
# to pass arguments correctly you need escape both. I.E. "stream-jmx-connect.sh tomcat *:*\!\!13000"
AGENT_OPTIONS="*:*!!60000"
# jmx options from environment
if [ -z "$TNT4J_AGENT_OPTIONS" ]; then
    TNT4J_AGENT_OPTIONS="$AGENT_OPTIONS"
fi
# jmx options from command line overrides
if [ "x$2" != "x" ]; then
    TNT4J_AGENT_OPTIONS="$2"
fi

# AppServer identifies source
if [ -z "$TNT4J_APPSERVER" ]; then
    TNT4J_APPSERVER="Default"
fi
TNT4JOPTS="$TNT4JOPTS -Dsjmx.serviceId=$TNT4J_APPSERVER"

CONN_OPTIONS="-connect -vm:service:jmx:iiop://localhost:2809/jndi/JMXConnector -cp:java.naming.provider.url=corbaname:iiop:localhost:2809"
### --- Uncomment if WAS and IBM JVM requires connection authentication or getting naming related exceptions ---
### --- also do not forget to alter sas.client.props file to disable basic authentication ---
# CONN_OPTIONS="-connect -vm:service:jmx:iiop://localhost:2809/jndi/JMXConnector -ul:Admin -up:admin -cp:java.naming.factory.initial=com.ibm.websphere.naming.WsnInitialContextFactory -cp:java.naming.factory.url.pkgs=com.ibm.ws.naming -cp:java.naming.provider.url=corbaloc:iiop:localhost:2809/WsnAdminNameService"
### ------------------------------------------------------------------------------------------------------------

### --- using JAVA_HOME java and setting WAS specific options ---
"$JAVA_HOME/bin/java" $TNT4JOPTS $MYCLIENTSAS $MYCLIENTSSL -classpath "$LIBPATH" com.jkoolcloud.tnt4j.stream.jmx.SamplingAgent $CONN_OPTIONS -ao:$TNT4J_AGENT_OPTIONS
### -------------------------------------------------------------