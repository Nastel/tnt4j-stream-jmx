@echo off
rem ---- parameters expected ----
rem see readme for additional details
rem
rem 1. host:port (required)
rem 2. agent options for mbeans and interval (optional, if . set to default value)
rem 3. service identifier for the process being monitored  (optional, if . set to default value)
rem 4. program arguments (optional, if . set to default value)
rem -----------------------------

set RMI_URI="service:jmx:rmi:///jndi/rmi://%1/jmxrmi"
.\stream-jmx-connect.bat %RMI_URI% %2 %3 %4