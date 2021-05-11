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
#!/usr/bin/env python3

# Write /etc/pimd.conf to listen on all interfaces in the experiment network
# and use the specified rendezvous point.
#
# ignores the control networks and the docker0 interface

import warnings
with warnings.catch_warnings():
    import sys
    import argparse
    import os
    import os.path
    import logging
    import logging.config
    import json
    import map_common
    import ipaddress
    
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

        
def create_config_file(rendezvous_point, experiment_interfaces):
    """
    Get text for the config file.

    :param rendezvous_point: the single rendezvous point for PIM
    :param experiment_interfaces: the tuples of experiment interfaces from get_experiment_interfaces
    :return: the text of the config file
    """
    config_file = '''
# Bigger value means  "higher" priority
bsr-candidate priority 5

# Smaller value means "higher" priority
rp-candidate time 30 priority 20

# Candidate for being RP of complete IPv4 multicast range
group-prefix 224.0.0.0 masklen 4

# Static rendez-vous point
rp-address {rendezvous_point} 224.0.0.0/4

# Switch to shortest-path tree after first packet, but only after 100 sec.
spt-threshold packets 0 interval 100

'''.format(rendezvous_point=rendezvous_point)

    for (ifce, addr, masklen) in experiment_interfaces:
        config_file = config_file + "phyint " + ifce + " enable\n"

    return config_file

    
def main(argv=None):
    if argv is None:
        argv = sys.argv[1:]

    parser = argparse.ArgumentParser()
    parser.add_argument("-l", "--logconfig", dest="logconfig", help="logging configuration (default: logging.json)", default='logging.json')
    parser.add_argument("-r", "--rendezvous-point", dest="rendezvous", help="The IP address of the rendezvous point", required=True)

    args = parser.parse_args(argv)

    setup_logging(default_path=args.logconfig)

    control_networks = map_common.load_excluded_subnets()
    interfaces = map_common.get_all_interfaces()
    experiment_interfaces = map_common.get_experiment_interfaces(interfaces, control_networks)

    with open("/etc/pimd.conf", "w") as f:
        text = create_config_file(args.rendezvous, experiment_interfaces)
        f.write(text)
    
        
if __name__ == "__main__":
    sys.exit(main())


