#! /bin/bash

RMI_URI="service:jmx:rmi:///jndi/rmi://$1/jmxrmi"
shift
./stream-jmx-connect.sh $RMI_URI $@