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
hn=`hostname -s`
cache_registry_id=$1
cache_registry_name=map_registry-cache_$cache_registry_id
remote_url=$2

echo "Starting cache registry with name $cache_registry_name and remote url $remote_url"

# stop and remove previous instance of registry
docker stop $cache_registry_name
docker rm $cache_registry_name

# create a directory for the registry and copy the certificate and key
mkdir $cache_registry_name
cp ../client.cert $cache_registry_name
cp ../client.key $cache_registry_name

# create a cache registry config file
cp ../config2.yml $cache_registry_name/config2-cache.yml
echo "proxy:\n  remoteurl: $remote_url" >> $cache_registry_name/config2-cache.yml

# pull and start registry
docker pull registry:2
docker run -d --name $cache_registry_name -p 5000:5000 -p 5001:5001 \
             -v `pwd`/$cache_registry_name/config2-cache.yml:/etc/docker/registry/config.yml \
             -v `pwd`/$cache_registry_name/client.cert:/etc/docker/registry/client.cert \
             -v `pwd`/$cache_registry_name/client.key:/etc/docker/registry/client.key \
             registry:2 &

sleep 5   # wait enough time for registry to start
