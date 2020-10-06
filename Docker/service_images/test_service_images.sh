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
saved_images_file=map_service_images.tar

if [ -n "$(command -v podman)" ]; then
  DOCKER=podman
else
  DOCKER=docker
fi


# stop all running docker containers
${DOCKER} stop $(${DOCKER} ps -q)

log "Showing local Docker images..."
try ${DOCKER} images
printf "\n\n"

log "Loading image file ${saved_images_file}..."
try ${DOCKER} load -i ${saved_images_file}
printf "\n\n"

log "Showing local Docker images after loading image file..."
${DOCKER} images
printf "\n\n"


log "Press a key to start map_base..." && read a
${DOCKER} run map_base:latest &
sleep 5

log "Press a key to start map_base_test..." && read a
${DOCKER} run map_base_test:latest &
sleep 5

log "Press a key to start networked_file_store_server..." && read a
${DOCKER} run -p 59112:59112 networked_file_store_server:latest "test.db" &
sleep 5

log "Press a key to start networked_file_store_client_publisher..." && read a
${DOCKER} run --network "host" networked_file_store_client_publisher:latest "localhost" &

log "Press a key to start networked_file_store_client_query..." && read a
${DOCKER} run --network "host" networked_file_store_client_query:latest "localhost" &



log "Press a key to exit..." && read a
