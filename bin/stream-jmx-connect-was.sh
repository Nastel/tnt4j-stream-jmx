#! /bin/bash
### ---- parameters expected ----
# see readme for additional details
#
# 1. process id or service (required)
# 2. agent options for mbeans and interval (optional, if . set to default value)
# 3. service identifier for the process being monitored  (optional, if . set to default value)
# 4. program arguments (optional, if . set to default value)
### -----------------------------

if command -v readlink >/dev/null 2>&1; then
    SCRIPTPATH=$(dirname $(readlink -m $BASH_SOURCE))
else
    SCRIPTPATH=$(cd "$(dirname "$BASH_SOURCE")" ; pwd -P)
fi

### ---- WAS environment configuration ----
WAS_HOME="/opt/IBM/WebSphere/AppServer"
JAVA_HOME="$WAS_HOME/java"
WAS_PATH="$WAS_HOME/runtimes/*:$WAS_HOME/lib/webadmin/management.jar:$WAS_HOME/plugins/com.ibm.ws.runtime.jar"
MYCLIENTSAS="-Dcom.ibm.CORBA.ConfigURL=file:$SCRIPTPATH/../config/sas.client.props"
MYCLIENTSSL="-Dcom.ibm.SSL.ConfigURL=file:$SCRIPTPATH/../config/ssl.client.props"
### ----------------------------------------

### ---- appending libs path with WAS libs ----
LIBPATH="$SCRIPTPATH/../*:$SCRIPTPATH/../lib/*:$WAS_PATH"
### -------------------------------------------

JAVA_EXEC="java"
if [[ "$JAVA_HOME" == "" ]]; then
  echo '"JAVA_HOME" env. variable is not defined!..'
else
  echo 'Will use java from:' "$JAVA_HOME"
  JAVA_EXEC="$JAVA_HOME/bin/java"
fi

jver=$("${JAVA_EXEC}" -version 2>&1 | head -n 1 | cut -d'"' -f2 | sed '/^1\./s///' | cut -d'.' -f1 | cut -d'-' -f1 | cut -d'+' -f1 | cut -d'_' -f1)

TNT4JOPTS="$TNT4JOPTS --add-exports=jdk.attach/sun.tools.attach=ALL-UNNAMED --add-opens=jdk.attach/sun.tools.attach=ALL-UNNAMED"
TNT4JOPTS="$TNT4JOPTS -Dtnt4j.dump.on.vm.shutdown=false -Dtnt4j.dump.on.exception=true -Dtnt4j.dump.provider.default=true"

### --- tnt4j file ----
if [[ -z "$TNT4J_PROPERTIES" ]]; then
  TNT4J_PROPERTIES="$SCRIPTPATH/../config/tnt4j.properties"
fi
TNT4JOPTS="$TNT4JOPTS -Dtnt4j.config=$TNT4J_PROPERTIES"
### -------------------
### --- log4j file ----
if [[ -z "$LOG4J_PROPERTIES" ]]; then
  LOG4J_PROPERTIES="$SCRIPTPATH/../config/log4j2.xml"
fi
TNT4JOPTS="$TNT4JOPTS -Dlog4j2.configurationFile=file:$LOG4J_PROPERTIES"
### -------------------

### ---- adding WAS specific JMX sampler options ----
TNT4JOPTS="$TNT4JOPTS -Dcom.jkoolcloud.tnt4j.stream.jmx.agent.forceObjectName=true -Dcom.jkoolcloud.tnt4j.stream.jmx.sampler.factory=com.jkoolcloud.tnt4j.stream.jmx.impl.WASSamplerFactory -Dcom.jkoolcloud.tnt4j.stream.jmx.agent.excludeOnError=true"
### -------------------------------------------------

# NOTE: Double exclamation mark in bash has a special meaning (previous command)
# to pass arguments correctly you need escape both, e.g.: "stream-jmx-connect.sh tomcat *:*\!\!13000"
### ---- Agent sampler options ----
AGENT_OPTIONS="*:*!!60000"
## jmx options from environment
if [[ -z "$TNT4J_AGENT_OPTIONS" ]]; then
    TNT4J_AGENT_OPTIONS="$AGENT_OPTIONS"
fi
## jmx options from command line overrides
if [[ "x$2" != "x" ]] && [[ "x$2" != "x." ]]; then
    TNT4J_AGENT_OPTIONS="$2"
fi
### -------------------------------

### ---- AppServer identifies source ----
if [[ -z "$TNT4J_APPSERVER" ]]; then
    TNT4J_APPSERVER="Default"
fi
if [[ "x$3" != "x" ]] && [[ "x$3" != "x." ]]; then
    TNT4J_APPSERVER="$3"
fi
TNT4JOPTS="$TNT4JOPTS -Dfile.encoding=UTF-8 -Dsjmx.serviceId=$TNT4J_APPSERVER"
### -------------------------------------

### ---- Agent arguments ----
if [[ -z "$TNT4J_AGENT_ARGS" ]]; then
### use this when streaming to AutoPilot
#    TNT4J_AGENT_ARGS="-slp:compositeDelimiter=\\"
### use this when streaming to jKool
    TNT4J_AGENT_ARGS="-slp:compositeDelimiter=_"
fi
if [[ "x$4" != "x" ]] && [[ "x$4" != "x." ]]; then
    TNT4J_AGENT_ARGS="$4"
fi
### -------------------------

CONN_OPTIONS="-connect -vm:service:jmx:iiop://localhost:2809/jndi/JMXConnector -cp:java.naming.provider.url=corbaname:iiop:localhost:2809"
### --- Uncomment if WAS and IBM JVM requires connection authentication or getting naming related exceptions ---
### --- also do not forget to alter sas.client.props file to disable basic authentication ---
# CONN_OPTIONS="-connect -vm:service:jmx:iiop://localhost:2809/jndi/JMXConnector -ul:Admin -up:admin -cp:java.naming.factory.initial=com.ibm.websphere.naming.WsnInitialContextFactory -cp:java.naming.factory.url.pkgs=com.ibm.ws.naming -cp:java.naming.provider.url=corbaloc:iiop:localhost:2809/WsnAdminNameService"
### ------------------------------------------------------------------------------------------------------------

### --- using JAVA_HOME java and setting WAS specific options ---
$JAVA_EXEC $TNT4JOPTS $MYCLIENTSAS $MYCLIENTSSL -classpath "$LIBPATH" com.jkoolcloud.tnt4j.stream.jmx.SamplingAgent $CONN_OPTIONS -ao:$TNT4J_AGENT_OPTIONS $TNT4J_AGENT_ARGS
### -------------------------------------------------------------