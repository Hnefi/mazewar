#!/bin/bash
# client.sh
ECE419_HOME=/cad2/ece419s/
JAVA_HOME=${ECE419_HOME}/java/jdk1.6.0/

# arguments to GameServer
# $1 = port # of where I'm listening

${JAVA_HOME}/bin/java GameServer $1
