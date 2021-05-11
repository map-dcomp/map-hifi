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

#if [ -n "$(command -v podman)" ]; then
  DOCKER=podman
#else
#  DOCKER=docker
#fi

# This file needs to be kept in sync with
# stage-experiment/src/main/resources/ansible/registry/run_docker_registry_map.sh
#
# The names of the images to load need to match.
saved_images_file=map_service_images.tar

log "Building hifi codebase"
cd "${mydir}"/../..
try ./gradlew --parallel build -x test -x spotbugsMain -x spotbugsTest -x checkstyleMain -x checkstyleTest


log "Creating MAP base container"
try cd "${mydir}"/map_base
try ${DOCKER} build -t map_base .

log "Creating FROM MAP base test image"
try cd "${mydir}"/map_base_test
try ${DOCKER} build -t map_base_test .

log
log
log


try cd "${mydir}/../../Face Recognition/Face_Recognition"

# copy latest jar files
try cp build/libs/FaceDetectionServer.jar "${mydir}"/face_recognition_server
try cp build/libs/ImageSendingClient.jar "${mydir}"/face_recognition_client

log "Creating Face Recognition client container"
try cd "${mydir}"/face_recognition_client
try ${DOCKER} build -t frc .

log "Creating Face Recognition server container"
try cd "${mydir}"/face_recognition_server
try ${DOCKER} build -t frs .

log
log
log


try cd "${mydir}/../../simulation-applications/simple-webserver/"

# copy latest jar file
try cp $(ls -rt build/libs/simple-webserver-*-executable.jar | tail -1) "${mydir}"/simple_webserver_base/simple-webserver-executable.jar

log "Creating simple-webserver containers"
try cd "${mydir}"/simple_webserver_base
try ${DOCKER} build -t simple_webserver_base .

try cd "${mydir}"/simple_webserver
try ${DOCKER} build -t simple_webserver .

try cd "${mydir}"/simple_webserver_large-response
try ${DOCKER} build -t simple_webserver_large-response .

try cd "${mydir}"/simple_webserver_small-response
try ${DOCKER} build -t simple_webserver_small-response .

try cd "${mydir}"/simple_webserver_database
try ${DOCKER} build -t simple_webserver_database .


log
log
log


try cd "${mydir}/../../simulation-applications/networked_file_store/networked-file-store-server/"

# copy latest jar file
try cp $(ls -rt build/libs/networked-file-store-*-executable.jar | tail -1) "${mydir}"/networked_file_store_server/networked-file-store-server.jar

log "Creating simple networked-file-store-server container"
try cd "${mydir}"/networked_file_store_server
try ${DOCKER} build -t networked_file_store_server .

log
log
log


try cd "${mydir}/../../simulation-applications/networked_file_store/networked-file-store-client-publisher/"

# copy latest jar file
try cp $(ls -rt build/libs/networked-file-store-client-publisher-*-executable.jar | tail -1) "${mydir}"/networked_file_store_client_publisher/networked-file-store-client-publisher.jar

log "Creating simple networked-file-store-client-publisher container"
try cd "${mydir}"/networked_file_store_client_publisher
try ${DOCKER} build -t networked_file_store_client_publisher .

log
log
log


try cd "${mydir}/../../simulation-applications/networked_file_store/networked-file-store-client-query/"

# copy latest jar file
try cp $(ls -rt build/libs/networked-file-store-client-query-*-executable.jar | tail -1) "${mydir}"/networked_file_store_client_query/networked-file-store-client-query.jar

log "Creating simple networked-file-store-client-query container"
try cd "${mydir}"/networked_file_store_client_query
try ${DOCKER} build -t networked_file_store_client_query .


log
log
log
log

try cd "${mydir}/../../simulation-applications/fake-load-server"

# copy latest jar file
try cp $(ls -rt build/libs/fake-load-server-*-executable.jar | tail -1) "${mydir}"/fake_load_server/fake-load-server.jar

log "Creating fake_load_server container"
try cd "${mydir}"/fake_load_server
try ${DOCKER} build -t fake_load_server .

log
log
log


try cd "${mydir}"
log Writing to ${saved_images_file}...

# this list needs to be kept in sync with stage-experiment/src/main/resources/ansible/registry/run_docker_registry_map.sh
try rm -f ${saved_images_file}
try ${mydir}/podman_save_multiple.py -o ${saved_images_file} \
	map_base map_base_test \
	frs frc \
	simple_webserver_base \
	simple_webserver \
	simple_webserver_large-response \
	simple_webserver_small-response \
	simple_webserver_database \
	networked_file_store_server networked_file_store_client_publisher networked_file_store_client_query \
        fake_load_server

log "${saved_images_file} to the registry system"
