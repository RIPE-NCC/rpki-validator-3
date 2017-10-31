#!/usr/bin/env bash
#
# The BSD License
#
# Copyright (c) 2010-2012 RIPE NCC
# All rights reserved.
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions are met:
#   - Redistributions of source code must retain the above copyright notice,
#     this list of conditions and the following disclaimer.
#   - Redistributions in binary form must reproduce the above copyright notice,
#     this list of conditions and the following disclaimer in the documentation
#     and/or other materials provided with the distribution.
#   - Neither the name of the RIPE NCC nor the names of its contributors may be
#     used to endorse or promote products derived from this software without
#     specific prior written permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
# AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
# IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
# ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
# LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
# CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
# SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
# INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
# CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
# ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
# POSSIBILITY OF SUCH DAMAGE.
#

EXECUTION_DIR=`dirname "$BASH_SOURCE"`
cd ${EXECUTION_DIR}

JAVA_CMD="/usr/bin/java"
APP_NAME="rpki-validator-3"
PID_FILE="/var/run/${APP_NAME}.pid"
CONFIG_FILE="/etc/${APP_NAME}.conf"
JAR="/usr/lib/${APP_NAME}.jar"

function parse_optional_config_line {
    local CONFIG_KEY=$1
    local VALUE=`grep "^$CONFIG_KEY" $CONFIG_FILE | sed 's/#.*//g' | awk -F "=" '{ print $2 }'`
    eval "$2=$VALUE"
}

function parse_config_line {
    local CONFIG_KEY=$1
    local VALUE=`grep "^$CONFIG_KEY" $CONFIG_FILE | sed 's/#.*//g' | awk -F "=" '{ print $2 }'`

    if [ -z $VALUE ]; then
        error_exit "Cannot find value for: $CONFIG_KEY in config-file: $CONFIG_FILE"
    fi
    eval "$2=$VALUE"
}

parse_config_line "ui.http.port" HTTP_PORT_VALUE
parse_config_line "rtr.port" RTR_PORT_VALUE

parse_config_line "locations.libdir" LIB_DIR
parse_config_line "locations.pidfile" PID_FILE

parse_config_line "jvm.memory.initial" JVM_XMS
parse_config_line "jvm.memory.maximum" JVM_XMX

CLASSPATH=:"$LIB_DIR/*"
MEM_OPTIONS="-Xms$JVM_XMS -Xmx$JVM_XMX"

${JAVA_CMD} ${MEM_OPTIONS} -Dapp.name=${APP_NAME} -Dconfig.file=${CONFIG_FILE} -jar ${JAR}

