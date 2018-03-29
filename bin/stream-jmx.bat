@echo off
setlocal

rem ---- parameters expected ----
rem see readme for additional details
rem
rem 1. service identifier for the process being monitored  (optional)
rem -----------------------------

set RUNDIR=%~dp0
set LIBPATH=%RUNDIR%..\*;%RUNDIR%..\lib\*
set TNT4JOPTS="-Dorg.slf4j.simpleLogger.defaultLogLevel=debug" "-Dcom.jkoolcloud.tnt4j.stream.jmx.agent.trace=true" "-Dtnt4j.dump.on.vm.shutdown=true" "-Dtnt4j.dump.on.exception=true" "-Dtnt4j.dump.provider.default=true"
IF ["%TNT4J_PROPERTIES%"] EQU [""] set "TNT4J_PROPERTIES=%RUNDIR%..\config\tnt4j.properties"
set TNT4JOPTS=%TNT4JOPTS% "-Dtnt4j.config=%TNT4J_PROPERTIES%"

rem ---- AppServer identifies source ----
if "%TNT4J_APPSERVER%"=="" set TNT4J_APPSERVER=Default
if not "%1"=="" if not "%1"=="." set TNT4J_APPSERVER=%1
set TNT4JOPTS=%TNT4JOPTS% "-Dsjmx.serviceId=%TNT4J_APPSERVER%"
rem -------------------------------------

@echo on
java %TNT4JOPTS% -classpath "%LIBPATH%" com.jkoolcloud.tnt4j.stream.jmx.SamplingAgent "*:*" "" 10000 60000