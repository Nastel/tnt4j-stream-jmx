@echo off

set RMI_URI="service:jmx:rmi:///jndi/rmi://%1/jmxrmi"
.\stream-jmx-connect.bat %RMI_URI% %2 %3 %4