#! /bin/bash
### ---- parameters expected ----
# see readme for additional details
#
# 1. process id or service (required)
# 2. agent options for mbeans and interval (optional, if . set to default value)
# 3. service identifier for the process being monitored  (optional, if . set to default value)
# 4. program arguments (optional, if . set to default value)
### -----------------------------

# Exclude jars not necessary for running commands.
regex="(-(test|sources|javadoc|all)\.jar|jar.asc)$"
should_include_file() {
  file=$1
  if [[ -z "$(echo "$file" | egrep "$regex")" ]] ; then
    return 0
  else
    return 1
  fi
}

if command -v realpath >/dev/null 2>&1; then
    SCRIPTPATH=`dirname $(realpath $0)`
else
    SCRIPTPATH=$( cd "$(dirname "$0")" ; pwd -P )
fi

### --- When connecting application instance having basic JMX implementation, e.g. ordinary Java app, Tomcat, Kafka
MODULE_SET=("core")
### --- Uncomment when intended to collect monitored VMs (e.g. Kafka, Solr) from ZooKeeper registry
# MODULE_SET=("core" "zk")
### --- Uncomment when connecting some J2EE implementing application instance JMX
# MODULE_SET=("core" "j2ee")
### --- Uncomment when connecting IBM Websphere Application Server (WAS) instance JMX
# MODULE_SET=("core" "j2ee" "was")
### --- Uncomment when connecting IBM Websphere Liberty Server instance JMX
# MODULE_SET=("core" "j2ee" "liberty")

for module in "${MODULE_SET[@]}"
do
  for file in "$SCRIPTPATH"/../tnt4j-stream-jmx-"$module"*.jar;
  do
    if should_include_file "$file"; then
      if [[ -z "$LIBPATH" ]] ; then
          LIBPATH="$file"
      else
          LIBPATH="$LIBPATH":"$file"
      fi
    fi
  done
done
echo "$LIBPATH"

### --- Additional libraries for WebLogic ---
# WL_HOME="/opt/Oracle/Middleware/Oracle_Home"
# WL_CLINET_LIBS="$WL_HOME/wlserver/server/lib/wlclient.jar:$WL_HOME/wlserver/server/lib/wljmxclient.jar:$WL_HOME/wlserver/server/lib/javax.javaee-api.jar"
# TOOLS_PATH="$TOOLS_PATH:$WL_CLINET_LIBS"
### -----------------------------------------

LIBPATH="$LIBPATH:$SCRIPTPATH/../lib/*"

jver=$("$JAVA_HOME/bin/java" -fullversion 2>&1 | sed -n ';s/.* version "\(.*\)\.\(.*\)\..*"/\1\2/p;')

if [[ $jver -gt 18 ]]; then
  TNT4JOPTS="--add-exports=jdk.attach/sun.tools.attach=ALL-UNNAMED --add-opens=jdk.attach/sun.tools.attach=ALL-UNNAMED"
else
  TOOLS_PATH="$JAVA_HOME/lib/tools.jar"
  LIBPATH="$LIBPATH:$TOOLS_PATH"
fi

TNT4JOPTS="$TNT4JOPTS -Dtnt4j.dump.on.vm.shutdown=true -Dtnt4j.dump.on.exception=true -Dtnt4j.dump.provider.default=true"

### --- tnt4j file ----
if [[ -z "$TNT4J_PROPERTIES" ]]; then
  TNT4J_PROPERTIES="$SCRIPTPATH/../config/tnt4j.properties"
fi
TNT4JOPTS="$TNT4JOPTS -Dtnt4j.config=$TNT4J_PROPERTIES"
### -------------------
### --- log4j file ----
if [[ -z "$LOG4J_PROPERTIES" ]]; then
  LOG4J_PROPERTIES="$SCRIPTPATH/../config/log4j.properties"
fi
TNT4JOPTS="$TNT4JOPTS -Dlog4j.configuration=file:$LOG4J_PROPERTIES"
### -------------------

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

if [[ "$JAVA_HOME" == "" ]]; then
  echo '"JAVA_HOME" env. variable is not defined!..'
else
  echo 'Will use java from:' "$JAVA_HOME"
fi

"$JAVA_HOME/bin/java" $TNT4JOPTS -classpath "$LIBPATH" com.jkoolcloud.tnt4j.stream.jmx.SamplingAgent -connect -vm:$1  -ao:$TNT4J_AGENT_OPTIONS $TNT4J_AGENT_ARGS