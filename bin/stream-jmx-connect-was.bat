@echo off
setlocal

rem ---- parameters expected ----
rem see readme for additional details
rem
rem 1. process id or service (required)
rem 2. agent options for mbeans and interval (optional, if . set to default value)
rem 3. service identifier for the process being monitored  (optional, if . set to default value)
rem 4. program arguments (optional, if . set to default value)
rem -----------------------------

set RUNDIR=%~dp0

rem ---- WAS environment configuration ----
set WAS_HOME=C:\IBM\WebSphere\AppServer
rem set WAS_HOME=C:\Program Files (x86)\IBM\WebSphere\AppServer
set JAVA_HOME=%WAS_HOME%\java
set WAS_PATH=%WAS_HOME%\runtimes\*;%WAS_HOME%\lib\webadmin\management.jar;%WAS_HOME%\plugins\com.ibm.ws.runtime.jar
set MYCLIENTSAS="-Dcom.ibm.CORBA.ConfigURL=file:%RUNDIR%..\config\sas.client.props"
REM Fix slashes for file: URI
set MYCLIENTSAS=%MYCLIENTSAS:\=/%
set MYCLIENTSSL="-Dcom.ibm.SSL.ConfigURL=file:%RUNDIR%..\config\ssl.client.props"
REM Fix slashes for file: URI
set MYCLIENTSSL=%MYCLIENTSSL:\=/%
rem ----------------------------------------

rem ---- appending libs path with WAS libs ----
set LIBPATH=%RUNDIR%..\*;%RUNDIR%..\lib\*;%WAS_PATH%
rem -------------------------------------------

set JAVA_EXEC="java"
IF ["%JAVA_HOME%"] EQU [""] (
  echo "JAVA_HOME" env. variable is not defined!..
) else (
  echo Will use java from: "%JAVA_HOME%"
  set JAVA_EXEC="%JAVA_HOME%\bin\java"
)

for /f tokens^=2-5^ delims^=.+-_^" %%j in ('%JAVA_EXEC% -fullversion 2^>^&1') do set "jver=%%j%%k"
rem for early access versions replace "ea" part with "00" to get comparable number
set jver=%jver:ea=00%

set TNT4JOPTS=%TNT4JOPTS% "--add-exports=jdk.attach/sun.tools.attach=ALL-UNNAMED" "--add-opens=jdk.attach/sun.tools.attach=ALL-UNNAMED"
set TNT4JOPTS=%TNT4JOPTS% "-Dtnt4j.dump.on.vm.shutdown=false" "-Dtnt4j.dump.on.exception=true" "-Dtnt4j.dump.provider.default=true"
IF ["%TNT4J_PROPERTIES%"] EQU [""] set TNT4J_PROPERTIES="%RUNDIR%..\config\tnt4j.properties"
set TNT4JOPTS=%TNT4JOPTS% "-Dtnt4j.config=%TNT4J_PROPERTIES%"
IF ["%LOG4J_PROPERTIES%"] EQU [""] set LOG4J_PROPERTIES="%RUNDIR%..\config\log4j2.xml"
set TNT4JOPTS=%TNT4JOPTS% "-Dlog4j2.configurationFile=%LOG4J_PROPERTIES%"
rem ---- adding WAS specific JMX sampler options ----
set TNT4JOPTS=%TNT4JOPTS% "-Dcom.jkoolcloud.tnt4j.stream.jmx.agent.forceObjectName=true" "-Dcom.jkoolcloud.tnt4j.stream.jmx.agent.useObjectNameProperties=false" "-Dcom.jkoolcloud.tnt4j.stream.jmx.agent.addStatisticMetadata=false" "-Dcom.jkoolcloud.tnt4j.stream.jmx.sampler.factory=com.jkoolcloud.tnt4j.stream.jmx.impl.WASPMISamplerFactory" "-Dcom.jkoolcloud.tnt4j.stream.jmx.agent.excludeOnError=true"
rem -------------------------------------------------

rem ---- Agent sampler options ----
set AGENT_OPTIONS=*:*!!60000
if "%TNT4J_AGENT_OPTIONS%"=="" set TNT4J_AGENT_OPTIONS=%AGENT_OPTIONS%
if not "%2"=="" if not "%2"=="." set TNT4J_AGENT_OPTIONS=%2
shift /2
rem -------------------------------

rem ---- AppServer identifies source ----
if "%TNT4J_APPSERVER%"=="" set TNT4J_APPSERVER=Default
if not "%2"=="" if not "%2"=="." set TNT4J_APPSERVER=%2
shift /2
set TNT4JOPTS=%TNT4JOPTS% "-Dfile.encoding=UTF-8" "-Dsjmx.serviceId=%TNT4J_APPSERVER%"
rem -------------------------------------

rem ---- Agent arguments ----
rem use this when streaming to AutoPilot
rem if "%TNT4J_AGENT_ARGS%"=="" set TNT4J_AGENT_ARGS=-slp:compositeDelimiter=\
rem use this when streaming to jKool
if "%TNT4J_AGENT_ARGS%"=="" set TNT4J_AGENT_ARGS=-slp:compositeDelimiter=_
if not "%2"=="" if not "%2"=="." set TNT4J_AGENT_ARGS=%2 %3 %4 %5 %6 %7 %8 %9
rem -------------------------

set CONN_OPTIONS=-connect -vm:service:jmx:iiop://localhost:2809/jndi/JMXConnector -cp:java.naming.provider.url=corbaname:iiop:localhost:2809
rem --- Uncomment if WAS and IBM JVM requires connection authentication or getting naming related exceptions ---
rem --- also do not forget to alter sas.client.props file to disable basic authentication ---
rem set CONN_OPTIONS=-connect -vm:service:jmx:iiop://localhost:2809/jndi/JMXConnector -ul:Admin -up:admin -cp:java.naming.factory.initial=com.ibm.websphere.naming.WsnInitialContextFactory -cp:java.naming.factory.url.pkgs=com.ibm.ws.naming -cp:java.naming.provider.url=corbaloc:iiop:localhost:2809/WsnAdminNameService
rem ------------------------------------------------------------------------------------------------------------

@echo on
rem --- using JAVA_HOME java and setting WAS specific options ---
%JAVA_EXEC% %TNT4JOPTS% %MYCLIENTSAS% %MYCLIENTSSL% -classpath "%LIBPATH%" com.jkoolcloud.tnt4j.stream.jmx.SamplingAgent %CONN_OPTIONS% -ao:%TNT4J_AGENT_OPTIONS% %TNT4J_AGENT_ARGS%
rem -------------------------------------------------------------
