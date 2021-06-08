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

rem --- When connecting application instance having basic JMX implementation, e.g. ordinary Java app, Tomcat, Kafka
set MODULE_SET=core
rem --- Uncomment when intended to collect monitored VMs (e.g. Kafka, Solr) from ZooKeeper registry
rem set MODULE_SET=core zk
rem --- Uncomment when connecting some J2EE implementing application instance JMX
rem set MODULE_SET=core j2ee
rem --- Uncomment when connecting IBM Websphere Application Server (WAS) instance JMX
rem set MODULE_SET=core j2ee was
rem --- Uncomment when connecting IBM Websphere Liberty Server instance JMX
rem set MODULE_SET=core j2ee liberty

rem LIBPATH initialization for stream-jmx modules
for %%a in (%MODULE_SET%) do (
	call :search_lib_path %%a
)
rem echo %LIBPATH%

rem --- Additional libraries for WebLogic ---
rem set WL_HOME=C:\Oracle\Middleware\Oracle_Home
rem set WL_CLINET_LIBS=%WL_HOME%\wlserver\server\lib\wlclient.jar;%WL_HOME%\wlserver\server\lib\wljmxclient.jar;%WL_HOME%\wlserver\server\lib\javax.javaee-api.jar
rem set TOOLS_PATH=%TOOLS_PATH%;%WL_CLINET_LIBS%
rem -----------------------------------------

set LIBPATH=%LIBPATH%;%RUNDIR%..\lib\*

for /f tokens^=2-5^ delims^=.+-_^" %%j in ('%JAVA_HOME%\bin\java -fullversion 2^>^&1') do set "jver=%%j%%k"
rem for early access versions replace "ea" part with "00" to get comparable number
set jver=%jver:ea=00%

IF %jver% GTR 18 set TNT4JOPTS="--add-exports=jdk.attach/sun.tools.attach=ALL-UNNAMED" "--add-opens=jdk.attach/sun.tools.attach=ALL-UNNAMED"
IF %jver% LEQ 18 set LIBPATH=%LIBPATH%;%JAVA_HOME%\lib\tools.jar

set TNT4JOPTS=%TNT4JOPTS% "-Dtnt4j.dump.on.vm.shutdown=true" "-Dtnt4j.dump.on.exception=true" "-Dtnt4j.dump.provider.default=true"
IF ["%TNT4J_PROPERTIES%"] EQU [""] set TNT4J_PROPERTIES="%RUNDIR%..\config\tnt4j.properties"
set TNT4JOPTS=%TNT4JOPTS% "-Dtnt4j.config=%TNT4J_PROPERTIES%"
IF ["%LOG4J_PROPERTIES%"] EQU [""] set LOG4J_PROPERTIES="%RUNDIR%..\config\log4j.properties"
set TNT4JOPTS=%TNT4JOPTS% "-Dlog4j.configuration=file:///%LOG4J_PROPERTIES%"

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

IF ["%JAVA_HOME%"] EQU [""] (
  echo "JAVA_HOME" env. variable is not defined!..
) else (
  echo Will use java from: "%JAVA_HOME%"
)

@echo on
"%JAVA_HOME%\bin\java" %TNT4JOPTS% -classpath "%LIBPATH%" com.jkoolcloud.tnt4j.stream.jmx.SamplingAgent -connect -vm:%1 -ao:%TNT4J_AGENT_OPTIONS% %TNT4J_AGENT_ARGS%
@echo off
goto :eof

:concat_lib_path
IF ["%LIBPATH%"] EQU [""] (
  set "LIBPATH=%1"
) ELSE (
  set "LIBPATH=%LIBPATH%;%1"
)
EXIT /B 0
:search_lib_path
for /f "delims=" %%a in ('
    dir /a-d /b /s %RUNDIR%..\tnt4j-stream-jmx-%1*.jar ^| find /i /v "-sources" ^| find /i /v "-javadoc" ^| find /i /v "-test" ^| find /i /v "-all"
') do call :concat_lib_path %%~fa
EXIT /B 0
:eof