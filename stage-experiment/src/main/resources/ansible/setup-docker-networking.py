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
#!/usr/bin/env python3

import warnings
with warnings.catch_warnings():
    import re
    import sys
    import argparse
    import os
    import os.path
    import logging
    import logging.config
    import json
    import subprocess
    import map_common

script_dir=os.path.abspath(os.path.dirname(__file__))

def get_logger():
    return logging.getLogger(__name__)

def setup_logging(
    default_path='logging.json',
    default_level=logging.INFO,
    env_key='LOG_CFG'
):
    """
    Setup logging configuration
    """
    path = default_path
    value = os.getenv(env_key, None)
    if value:
        path = value
    if os.path.exists(path):
        with open(path, 'r') as f:
            config = json.load(f)
        logging.config.dictConfig(config)
    else:
        logging.basicConfig(level=default_level)
    
def find_region_interface(region_ip):
    '''
    @return (region_ifce, region_subnet)
    '''

    interfaces = map_common.get_all_interfaces()
    get_logger().debug("Found interfaces {0}".format(interfaces))
    region_ifce = None
    region_subnet = None
    for (ifce, ip, subnet) in interfaces:
        if ip == region_ip:
            region_ifce = ifce
            region_subnet = subnet

    if region_ifce is None or region_subnet is None:
        raise RuntimeError("Unable to find regional interface for {0} in {1}".format(region_ip, interfaces))

    return (region_ifce, region_subnet)

def get_routes_to_replace(region_ifce):
    '''
    @return [(destination, gateway)]
    '''

    cmd_result = subprocess.run(["ip", "route", "show"], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    cmd_result.check_returncode()

    output = cmd_result.stdout.decode("utf-8")

    routes = []
    for line in output.split('\n'):
        get_logger().debug("Parsing line {0}".format(line))

        match = re.search(r'dev\s+(\S+)', line)
        if match:
            interface = match.group(1)
        else:
            # skip routes that don't specify an interface
            continue

        if interface != region_ifce:
            # only routes with the region interface matter
            continue
        
        match = re.match(r'^(\S+)\s+', line)
        if match:
            destination = match.group(1)
        else:
            get_logger().warning("Can't find destination address in %s", line)
            continue

        match = re.search(r'via\s+(\S+)', line)
        if match:
            gateway = match.group(1)
        else:
            # if there isn't a gateway we don't care
            continue

        if gateway == '0.0.0.0' and gateway == 'default':
            # don't mess with the default route
            continue
            
        routes.append((destination, gateway))

    return routes


def start_docker_network(region_ip, region_subnet):
    cmd_result = subprocess.run(['docker', 'network', 'create', '--driver', 'bridge', '--subnet', '{0}/{1}'.format(region_ip, region_subnet), '--gateway', region_ip, '--opt', 'com.docker.network.bridge.name=docker1', 'shared_nw'])
    cmd_result.check_returncode()

def setup_bridge(region_ifce):
    cmd_result = subprocess.run(["brctl", "addif", "docker1", region_ifce])
    cmd_result.check_returncode()

def add_routes(routes_to_add):
    for (destination, gateway) in routes_to_add:
        args = ['ip', 'route', 'add', destination]
        if gateway is not None:
            args.extend(['via', gateway])
        cmd_result = subprocess.run(args)
        cmd_result.check_returncode()

def delete_region_ip(region_ip, region_subnet, region_ifce):
    cmd_result = subprocess.run(['ip', 'addr', 'del', '{0}/{1}'.format(region_ip, region_subnet), 'dev', region_ifce])
    cmd_result.check_returncode()


def cleanup_broken_docker():
    cmd_result = subprocess.run(["ip", "addr", "show", "docker1"], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    output = cmd_result.stdout.decode("utf-8")
    if re.search(r'NO-CARRIER', output, re.MULTILINE):
        subprocess.run(["docker", "network", "rm", "shared_nw"])
        
def main(argv=None):
    if argv is None:
        argv = sys.argv[1:]

    parser = argparse.ArgumentParser()
    parser.add_argument("-l", "--logconfig", dest="logconfig", help="logging configuration (default: logging.json)", default='logging.json')
    parser.add_argument("-i", "--ip", dest="primary_ip", help="The primary IP address for the node (required)", required=True)

    args = parser.parse_args(argv)

    setup_logging(default_path=args.logconfig)
    
    region_ip = args.primary_ip

    cleanup_broken_docker()
    
    (region_ifce, region_subnet) = find_region_interface(region_ip)
    get_logger().info("Found region interface {0} subnet {1}".format(region_ifce, region_subnet))
    if re.match(r'^docker\d+$', region_ifce):
        get_logger().info("Already have a docker interface ({}) for the region, assuming networking is setup".format(region_ifce))
        return 0 
    
    routes_to_add = get_routes_to_replace(region_ifce)
    get_logger().debug("Routes to add: {0}".format(routes_to_add))

    delete_region_ip(region_ip, region_subnet, region_ifce)
    
    start_docker_network(region_ip, region_subnet)

    setup_bridge(region_ifce)

    add_routes(routes_to_add)
    
    return 0
        
if __name__ == "__main__":
    sys.exit(main())
