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

cleanup() {
    debug "In cleanup"
}
trap 'cleanup' INT TERM EXIT

if [ $# -ne 2 ]; then
    fatal "Usage: $0 <emulab experiment> <base directory>"
fi

emulab_experiment=$1

basedir=$2
scenario_name=$(basename ${basedir})

demand_dir=${basedir}/demand
if [ ! -d "${demand_dir}" ]; then
    fatal "Cannot find ${demand_dir}"
fi

scenario_dir=${basedir}/scenario
if [ ! -d "${scenario_dir}" ]; then
    fatal "Cannot find ${scenario_dir}"
fi

container_names_dir=${basedir}/container_names
if [ ! -d "${container_names_dir}" ]; then
    fatal "Cannot find ${container_names_dir}"
fi

client_service_config=${basedir}/client-service-configuration.json
if [ ! -e "${client_service_config}" ]; then
    fatal "Cannot find ${client_service_config}"
fi

generated_dir=${basedir}/generated_$(date +%Y%m%d_%H%M%s)

agent_jar=$(ls -rt ../hifi-resmgr/build/libs/hifi-agent-*-executable.jar | tail -1)
dns_jar=$(ls -rt ../DnsServer/build/libs/dns-server-*-executable.jar | tail -1)
client_jar=$(ls -rt ../ClientDriver/build/libs/client-driver-*-executable.jar | tail -1)
client_pre_start_jar=$(ls -rt ../ClientPreStart/build/libs/client-pre-start-*-executable.jar | tail -1)
stage_experiment_jar=$(ls -rt build/libs/stage-experiment-*-executable.jar | tail -1)

log "Outputting to ${generated_dir}"

try java -jar ${stage_experiment_jar} \
     --agent-jar ${agent_jar} \
     --dns-jar ${dns_jar} \
     --client-jar ${client_jar} \
     --client-pre-start-jar ${client_pre_start_jar} \
     --emulab-experiment ${emulab_experiment} \
     --docker-registry-hostname nodeA0 \
     --dumpEnabled \
     --dumpDirectory /var/lib/map/agent \
     --client-service-config "${client_service_config}" \
     --container-names-directory "${container_names_dir}" \
     --scenario "${scenario_dir}" \
     --demand "${demand_dir}" \
     --output "${generated_dir}" \
     --scenario-name ${scenario_name} \
     --rlgAlgorithm BIN_PACKING \
     --dcopAlgorithm DISTRIBUTED_CONSTRAINT_DIFFUSION
