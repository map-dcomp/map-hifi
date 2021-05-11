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
import subprocess
import re
import ipaddress


def get_all_interfaces():
    """
    Find all interfaces that have an IPv4 address.
    
    @return [(interface, ip, subnet)]
    """
    cmd_result = subprocess.run(["ip", "addr", "show"], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    cmd_result.check_returncode()
    
    output = cmd_result.stdout.decode("utf-8")

    interface = None
    ip = None
    subnet = None

    result = []
    
    for line in output.split('\n'):
        interface_match = re.match(r'^\d+:\s+(\S+):\s+', line)
        address_match = re.match(r'\s+inet\s+(\d+\.\d+.\d+\.\d+)/(\d+)\s+', line)
        if interface_match:
            if interface is not None:
                if ip is not None and subnet is not None:
                    entry = (interface, ip, subnet)
                    result.append(entry)
            interface = interface_match.group(1).split('@')[0]
            ip = None
            subnet = None
        elif address_match:
            ip = address_match.group(1)
            subnet = address_match.group(2)

    if interface is not None:
        if ip is not None and subnet is not None:
            entry = (interface, ip, subnet)
            result.append(entry)
            
    return result


def load_excluded_subnets():
    """
    Load the excluded subnets.
    """
    
    control_networks = list()
    with open('/etc/map/testbed-control-subnets.txt') as f:
        for line in f:
            subnet = ipaddress.ip_network(line.strip())
            control_networks.append(subnet)

    return control_networks

def in_control_network(ip, control_networks):
    """
    Check if ip is in the same subnet as one of the excluded networks.
    """
    
    ifce_addr = ipaddress.ip_address(ip)
    return any(ifce_addr in control for control in control_networks)


def get_experiment_interfaces(interfaces, ignored_networks):
    """
    Get the list of interfaces that are connected to the expeirment network"
    """
    
    local_subnet = ipaddress.ip_network('127.0.0.0/8')
    ignore_docker_interface = 'docker0' # always there
    return ( ifce for ifce in interfaces if not in_control_network(ifce[1], ignored_networks)
             and ifce[0] != ignore_docker_interface
             and not ipaddress.ip_address(ifce[1]) in local_subnet )


