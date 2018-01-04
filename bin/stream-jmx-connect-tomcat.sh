#! /bin/bash

# Exclude jars not necessary for running commands.
regex="(-(test|sources|javadoc)\.jar|jar.asc)$"
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

for file in "$SCRIPTPATH"/../tnt4j-stream-jmx-core-*.jar;
do
  if should_include_file "$file"; then
    if [ -z "$LIBPATH" ] ; then
        LIBPATH="$file"
    else
        LIBPATH="$LIBPATH":"$file"
    fi
  fi
done

LIBPATH="$LIBPATH:$SCRIPTPATH/../lib/*:$TOOLS_PATH"
TNT4JOPTS="-Dorg.slf4j.simpleLogger.defaultLogLevel=debug -Dcom.jkoolcloud.tnt4j.stream.jmx.agent.trace=true -Dtnt4j.dump.on.vm.shutdown=true -Dtnt4j.dump.on.exception=true -Dtnt4j.dump.provider.default=true"
TNT4JOPTS="$TNT4JOPTS -Dtnt4j.config=$SCRIPTPATH/../config/tnt4j.properties"

# NOTE! Double exclamation mark in bash has a special meaning (previous command)
# to pass arguments correctly you need escape both. I.E. "stream-jmx-connect.sh tomcat *:*\!\!13000"
AGENT_OPTIONS=$2

if ["$AGENT_OPTIONS" == ""]; then
    AGENT_OPTIONS="*:*!!10000"
fi

java $TNT4JOPTS -classpath "$LIBPATH" com.jkoolcloud.tnt4j.stream.jmx.SamplingAgent -connect -vm:$1 -ao:$AGENT_OPTIONS