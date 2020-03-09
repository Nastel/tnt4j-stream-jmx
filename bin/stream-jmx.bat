@echo off
setlocal

rem ---- parameters expected ----
rem see readme for additional details
rem
rem 1. service identifier for the process being monitored  (optional)
rem -----------------------------

set RUNDIR=%~dp0
set LIBPATH=%RUNDIR%..\*;%RUNDIR%..\lib\*
set TNT4JOPTS="-Dtnt4j.dump.on.vm.shutdown=true" "-Dtnt4j.dump.on.exception=true" "-Dtnt4j.dump.provider.default=true"
IF ["%TNT4J_PROPERTIES%"] EQU [""] set TNT4J_PROPERTIES="%RUNDIR%..\config\tnt4j.properties"
set TNT4JOPTS=%TNT4JOPTS% "-Dtnt4j.config=%TNT4J_PROPERTIES%"
IF ["%LOG4J_PROPERTIES%"] EQU [""] set LOG4J_PROPERTIES="%RUNDIR%..\config\log4j.properties"
set TNT4JOPTS=%TNT4JOPTS% "-Dlog4j.configuration=file:///%LOG4J_PROPERTIES%"

rem ---- AppServer identifies source ----
if "%TNT4J_APPSERVER%"=="" set TNT4J_APPSERVER=Default
if not "%1"=="" if not "%1"=="." set TNT4J_APPSERVER=%1
set TNT4JOPTS=%TNT4JOPTS% "-Dfile.encoding=UTF-8 -Dsjmx.serviceId=%TNT4J_APPSERVER%"
rem --- stream log file name ---
rem TNT4JOPTS="%TNT4JOPTS% -Dtnt4j.stream.log.filename=%RUNDIR%..\logs\tnt4j-stream-jmx.log"
rem --- streamed activities log file name ---
rem TNT4JOPTS="%TNT4JOPTS% -Dtnt4j.activities.log.filename=%RUNDIR%..\logs\tnt4j-stream-jmx_samples.log"
rem -------------------------------------

IF ["%JAVA_HOME%"] EQU [""] (
  echo "JAVA_HOME" env. variable is not defined!..
) else (
  echo Will use java from: "%JAVA_HOME%"
)

@echo on
"%JAVA_HOME%\bin\java" %TNT4JOPTS% -classpath "%LIBPATH%" com.jkoolcloud.tnt4j.stream.jmx.SamplingAgent "*:*" "" 10000 60000