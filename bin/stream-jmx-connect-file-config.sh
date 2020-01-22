#! /bin/bash
### ---- parameters expected ----
# see readme for additional details
#
# 1. configuration file (required)
# 2. program arguments (optional, if . set to default value)
### -----------------------------

# Exclude jars not necessary for running commands.
regex="(-(test|sources|javadoc|all)\.jar|jar.asc)$"
should_include_file() {
  file=$1
  if [ -z "$(echo "$file" | egrep "$regex")" ] ; then
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

TOOLS_PATH="$JAVA_HOME/lib/tools.jar"

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
      if [ -z "$LIBPATH" ] ; then
          LIBPATH="$file"
      else
          LIBPATH="$LIBPATH":"$file"
      fi
    fi
  done
done
echo "$LIBPATH"


LIBPATH="$LIBPATH:$SCRIPTPATH/../lib/*:$TOOLS_PATH"
TNT4JOPTS="-Dtnt4j.dump.on.vm.shutdown=true -Dtnt4j.dump.on.exception=true -Dtnt4j.dump.provider.default=true"

### --- tnt4j file ----
if [ -z "$TNT4J_PROPERTIES" ]; then
  TNT4J_PROPERTIES="$SCRIPTPATH/../config/tnt4j.properties"
fi
TNT4JOPTS="$TNT4JOPTS -Dtnt4j.config=$TNT4J_PROPERTIES"
### -------------------
### --- log4j file ----
if [ -z "$LOG4J_PROPERTIES" ]; then
  LOG4J_PROPERTIES="$SCRIPTPATH/../config/log4j.properties"
fi
TNT4JOPTS="$TNT4JOPTS -Dlog4j.configuration=file:$LOG4J_PROPERTIES"
### -------------------

### -------------------------------

### ---- AppServer identifies source ----
if [ -z "$TNT4J_APPSERVER" ]; then
    TNT4J_APPSERVER="Default"
fi
if [ "x$3" != "x" ] && [ "x$3" != "x." ]; then
    TNT4J_APPSERVER="$3"
fi
TNT4JOPTS="$TNT4JOPTS -Dfile.encoding=UTF-8 -Dsjmx.serviceId=$TNT4J_APPSERVER"
### -------------------------------------

### ---- Agent arguments ----
if [ -z "$TNT4J_AGENT_ARGS" ]; then
### use this when streaming to AutoPilot
#    TNT4J_AGENT_ARGS="-slp:compositeDelimiter=\\"
### use this when streaming to jKool
    TNT4J_AGENT_ARGS="-slp:compositeDelimiter=_"
fi
if [ "x$4" != "x" ] && [ "x$4" != "x." ]; then
    TNT4J_AGENT_ARGS="$4"
fi
### -------------------------

if [ "$JAVA_HOME" == "" ]; then
  echo '"JAVA_HOME" env. variable is not defined!..'
else
  echo 'Will use java from: "$JAVA_HOME"'
fi

"$JAVA_HOME/bin/java" $TNT4JOPTS -classpath "$LIBPATH" com.jkoolcloud.tnt4j.stream.jmx.SamplingAgent -connect -f:$1 $TNT4J_AGENT_ARGS