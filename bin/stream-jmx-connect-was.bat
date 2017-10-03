@echo off
setlocal
set RUNDIR=%~dp0

rem ---- added -----
set WAS_HOME=C:\IBM\WebSphere\AppServer
#set WAS_HOME=C:\Program Files (x86)\IBM\WebSphere\AppServer
set JAVA_HOME=%WAS_HOME%\java
set WAS_PATH=%WAS_HOME%\runtimes\*;%WAS_HOME%\lib\webadmin\management.jar
set MYCLIENTSAS="-Dcom.ibm.CORBA.ConfigURL=file:%RUNDIR%..\config\sas.client.props"
set MYCLIENTSSL="-Dcom.ibm.SSL.ConfigURL=file:%RUNDIR%..\config\ssl.client.props"
rem ----------------


set TOOLS_PATH=%JAVA_HOME%\lib\tools.jar

rem --- changed ---
set LIBPATH=%RUNDIR%..\*;%RUNDIR%..\lib\*;%TOOLS_PATH%;%WAS_PATH%
rem ---------------

set TNT4JOPTS="-Dorg.slf4j.simpleLogger.defaultLogLevel=debug" "-Dcom.jkoolcloud.tnt4j.stream.jmx.agent.trace=true" "-Dtnt4j.dump.on.vm.shutdown=true" "-Dtnt4j.dump.on.exception=true" "-Dtnt4j.dump.provider.default=true" "-Dtnt4j.config=%RUNDIR%..\config\tnt4j.properties"
set TNT4JOPTS=%TNT4JOPTS% "-Dcom.jkoolcloud.tnt4j.stream.jmx.agent.forceObjectName=true" "-Dcom.jkoolcloud.tnt4j.stream.jmx.sampler.factory=com.jkoolcloud.tnt4j.stream.jmx.impl.WASSamplerFactory" "-Djava.naming.provider.url=corbaname:iiop:localhost:2809"

set AGENT_OPTIONS=%2
if "%AGENT_OPTIONS%"=="" set AGENT_OPTIONS=*:*!!10000

@echo on
rem --- changed ---
"%JAVA_HOME%\bin\java" %TNT4JOPTS% %MYCLIENTSAS% %MYCLIENTSSL% -classpath "%LIBPATH%" com.jkoolcloud.tnt4j.stream.jmx.SamplingAgent -connect -vm:service:jmx:iiop://localhost:2809/jndi/JMXConnector -ao:%AGENT_OPTIONS%
rem ---------------
