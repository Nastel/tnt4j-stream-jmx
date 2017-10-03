#! /bin/bash
if command -v realpath >/dev/null 2>&1; then
    SCRIPTPATH=`dirname $(realpath $0)`
else
    SCRIPTPATH=$( cd "$(dirname "$0")" ; pwd -P )
fi

WAS_HOME=/opt/IBM/WebSphere/AppServer
JAVA_HOME=$WAS_HOME/java
WAS_PATH=$WAS_HOME/runtimes/*;$WAS_HOME/lib/webadmin/management.jar
MYCLIENTSAS="-Dcom.ibm.CORBA.ConfigURL=file:$SCRIPTPATH/../config/sas.client.props"
MYCLIENTSSL="-Dcom.ibm.SSL.ConfigURL=file:$SCRIPTPATH/../config/ssl.client.props"

TOOLS_PATH="$JAVA_HOME/lib/tools.jar"

LIBPATH="$SCRIPTPATH/../*:$SCRIPTPATH/../lib/*:$TOOLS_PATH:$WAS_PATH"

TNT4JOPTS="-Dorg.slf4j.simpleLogger.defaultLogLevel=debug -Dcom.jkoolcloud.tnt4j.stream.jmx.agent.trace=true -Dtnt4j.dump.on.vm.shutdown=true -Dtnt4j.dump.on.exception=true -Dtnt4j.dump.provider.default=true"
TNT4JOPTS="$TNT4JOPTS -Dtnt4j.config=$SCRIPTPATH/../config/tnt4j.properties"
TNT4JOPTS="$TNT4JOPTS -Dcom.jkoolcloud.tnt4j.stream.jmx.agent.forceObjectName=true -Dcom.jkoolcloud.tnt4j.stream.jmx.sampler.factory=com.jkoolcloud.tnt4j.stream.jmx.impl.WASSamplerFactory -Djava.naming.provider.url=corbaname:iiop:localhost:2809"

# NOTE! Double exclamation mark in bash has a special meaning (previous command)
# to pass arguments correctly you need escape both. I.E. "stream-jmx-connect.sh tomcat *:*\!\!13000"
AGENT_OPTIONS=$2

if ["$AGENT_OPTIONS" == ""]; then
    AGENT_OPTIONS="*:*!!10000"
fi

"$JAVA_HOME/bin/java" $TNT4JOPTS $MYCLIENTSAS $MYCLIENTSSL -classpath "$LIBPATH" com.jkoolcloud.tnt4j.stream.jmx.SamplingAgent -connect -vm:service:jmx:iiop://localhost:2809/jndi/JMXConnector -ao:$AGENT_OPTIONS