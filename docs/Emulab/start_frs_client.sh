#BBN_LICENSE_START -- DO NOT MODIFY BETWEEN LICENSE_{START,END} Lines
# Copyright (c) <2017,2018,2019,2020,2021>, <Raytheon BBN Technologies>
# To be applied to the DCOMP/MAP Public Source Code Release dated 2018-04-19, with
# the exception of the dcop implementation identified below (see notes).
# 
# Dispersed Computing (DCOMP)
# Mission-oriented Adaptive Placement of Task and Data (MAP) 
# 
# All rights reserved.
# 
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions are met:
# 
# Redistributions of source code must retain the above copyright
# notice, this list of conditions and the following disclaimer.
# Redistributions in binary form must reproduce the above copyright
# notice, this list of conditions and the following disclaimer in the
# documentation and/or other materials provided with the distribution.
# 
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
# IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
# TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
# PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
# HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
# SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
# TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
# PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
# LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
# NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
# SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
#BBN_LICENSE_END
#!/bin/sh

debug() { ! "${log_debug-false}" || log "DEBUG: $*" >&2; }
log() { printf '%s\n' "$*"; }
warn() { log "WARNING: $*" >&2; }
error() { log "ERROR: $*" >&2; }
fatal() { error "$*"; exit 1; }
try() { "$@" || fatal "'$@' failed"; }

mydir=$(cd "$(dirname "$0")" && pwd -L) || fatal "Unable to determine script directory"

container_name=$(date +%Y-%m-%d_%H-%M-%S)_$$

cleanup() {
    log "Copying CSV files from ${container_name}"
    try mkdir -p /var/lib/map/client/csv_files/${container_name}
    try docker cp ${container_name}:/app_metrics_data/processing_latency.csv /var/lib/map/client/container_app_metrics_data/${container_name}
    
    log "Stopping container ${container_name}"
    try docker stop ${container_name}
}
trap 'cleanup' INT TERM EXIT

num_cores=$(cat /proc/cpuinfo | grep "core id" | wc -l)
num_containers=$(docker ps | wc -l)
client_limit_multiplier=20
max_num_containers=$(expr ${num_cores} '*' ${client_limit_multiplier})

if [ ${num_containers} -ge ${max_num_containers} ]; then
    error "Too many containers limit is ${max_num_containers}"
    exit 250
fi

log "Starting container ${container_name}"
docker run \
       --network=host \
       --name=${container_name} \
       nodea0:5000/v2/frc face-recognition.map.dcomp 7123 face_input 1.0 100 --ack --csv "/app_metrics_data/processing_latency.csv"


