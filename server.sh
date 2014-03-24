#!/bin/bash
# client.sh
ECE419_HOME=/cad2/ece419s/
JAVA_HOME=${ECE419_HOME}/java/jdk1.6.0/

# Arguments which run the dns lookup server
# $1 = port # of where I'm listening
# $2 = random seed for the session

${JAVA_HOME}/bin/java LookupServer $1 $2
