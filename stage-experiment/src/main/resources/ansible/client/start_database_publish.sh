#BBN_LICENSE_START -- DO NOT MODIFY BETWEEN LICENSE_{START,END} Lines
# Copyright (c) <2017,2018,2019,2020>, <Raytheon BBN Technologies>
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
    # TODO: ticket 
    #log "Copying CSV files from ${container_name}"
    #output_folder="${container_name}_$(date +%Y-%m-%d_%H-%M-%S)"
    #try mkdir -p /var/lib/map/client/container_app_metrics_data/${output_folder}
    #try docker cp ${container_name}:/app_metrics_data/processing_latency.csv /var/lib/map/client/container_app_metrics_data/${output_folder}
    
    log "Stopping container ${container_name}"
    try docker stop ${container_name}
}
trap 'cleanup' INT TERM EXIT

if [ -z "${SERVICE_HOSTNAME}" ]; then
    SERVICE_HOSTNAME=database-publish.map.dcomp
fi
if [ -z "${REGISTRY_HOSTNAME}" ]; then
    REGISTRY_HOSTNAME=nodeA0
fi

log "Connecting to ${SERVICE_HOSTNAME}"
log "Using registry at ${REGISTRY_HOSTNAME}"

num_cores=$(cat /proc/cpuinfo | grep "core id" | wc -l)
num_containers=$(docker ps | wc -l)
client_limit_multiplier=20
max_num_containers=$(expr ${num_cores} '*' ${client_limit_multiplier})

if [ ${num_containers} -ge ${max_num_containers} ]; then
    error "Too many containers limit is ${max_num_containers}"
    exit 250
fi




shared_base_dir="/var/lib/map/client"
shared_container_dir="${shared_base_dir}/container_data/database-publish/${container_name}"
shared_service_dir="${shared_base_dir}/service_data/database-publish"

try mkdir -p "${shared_container_dir}/app_metrics_data"
try mkdir -p "${shared_container_dir}/instance_data"
try mkdir -p "${shared_service_dir}"


log "Starting container ${container_name}"
try docker run \
       --network=host \
       --name=${container_name} \
       --mount type=bind,source="${shared_container_dir}/app_metrics_data",target="/app_metrics_data" \
       --mount type=bind,source="${shared_container_dir}/instance_data",target="/instance_data" \
       --mount type=bind,source="${shared_service_dir}",target="/service_data" \
       ${REGISTRY_HOSTNAME}:5000/v2/networked_file_store_client_publisher ${SERVICE_HOSTNAME}
