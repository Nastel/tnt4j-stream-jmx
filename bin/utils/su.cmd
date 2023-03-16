@echo off
setlocal

set RUNDIR=%~dp0
set LIBPATH=%LIBPATH%;%RUNDIR%..\..\*;%RUNDIR%..\..\lib\*

set MAINCLASS=com.jkoolcloud.tnt4j.utils.SecurityUtils

set JAVA_EXEC="java"
IF ["%JAVA_HOME%"] EQU [""] (
  echo "JAVA_HOME" env. variable is not defined!..
) else (
  echo Will use java from: "%JAVA_HOME%"
  set JAVA_EXEC="%JAVA_HOME%\bin\java"
)

@echo on
%JAVA_EXEC% -classpath "%LIBPATH%" %MAINCLASS% %*