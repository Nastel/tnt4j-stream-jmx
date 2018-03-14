#! /bin/bash
### ---- parameters expected ----
# see readme for additional details
#
# 1. host:port (required)
# 2. agent options for mbeans and interval (optional, if . set to default value)
# 3. service identifier for the process being monitored  (optional, if . set to default value)
# 4. program arguments (optional, if . set to default value)
# -----------------------------

RMI_URI="service:jmx:rmi:///jndi/rmi://$1/jmxrmi"
shift
./stream-jmx-connect.sh $RMI_URI $@