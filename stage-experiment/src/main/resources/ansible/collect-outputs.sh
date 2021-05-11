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

scenario_destdir=/var/lib/map/scenario-outputs

name=$(hostname -s)
destdir=${scenario_destdir}/${name}

# clean out any previous results
try rm -fr "${destdir}"

try mkdir -p "${destdir}"
if [ -d /var/lib/map/client ]; then
    try mkdir -p "${destdir}"/client
    rsync --link-dest=/var/lib/map/client /var/lib/map/client/*.log "${destdir}"/client || warn "No client logs to copy on ${name}"
    rsync --link-dest=/var/lib/map/client /var/lib/map/client/system-stats.csv "${destdir}"/client || warn "No client logs to copy on ${name}"
    rsync -r --link-dest=/var/lib/map/client/container_data/ /var/lib/map/client/container_data/ "${destdir}"/client/container_data/ || warn "No client container_data to copy on ${name}"
fi

if [ -d /var/lib/map/dns ]; then
    try mkdir -p "${destdir}"/dns
    rsync -r --link-dest=/var/lib/map/dns/logs/ /var/lib/map/dns/logs/* "${destdir}"/dns || warn "No DNS logs to copy on ${name}"
fi

if [ -d /var/lib/map/agent ]; then
    try mkdir -p "${destdir}"/agent
    try rsync -r --exclude map-agent.jar --link-dest=/var/lib/map/agent/ /var/lib/map/agent/ "${destdir}"/agent
fi

if [ -d /var/lib/map/sim-driver ]; then
    try mkdir -p "${destdir}"/sim-driver
    try rsync -r --exclude sim-driver.jar --link-dest=/var/lib/map/sim-driver/ /var/lib/map/sim-driver/ "${destdir}"/sim-driver
fi

if [ -d /var/lib/map/background-traffic ]; then
    try mkdir -p "${destdir}"/background-traffic
    try rsync -r --exclude background-traffic-driver.jar --link-dest=/var/lib/map/background-traffic/ /var/lib/map/background-traffic/ "${destdir}"/background-traffic
fi

if [ -d /var/lib/map/system_stats ]; then
    try mkdir -p "${destdir}"/system_stats
    try rsync -r --exclude system_stats-driver.jar --link-dest=/var/lib/map/system_stats/ /var/lib/map/system_stats/ "${destdir}"/system_stats
fi

cd "${destdir}"/.. && tar -c ${name} | xz -T0 > ${scenario_destdir}/results.tar.xz || fatal "Error creating final file"
