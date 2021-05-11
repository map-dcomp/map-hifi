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
    import time
    from pathlib import Path
    import shutil
    import tarfile
    import stat
    import getpass

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
        logging.basicConfig(level=default_level,
                            format='%(asctime)s %(name)-12s %(levelname)-8s %(message)s',
                            datefmt='%m-%d %H:%M',)

def check_client_running(hostname):
    result = subprocess.run(['ssh',
                                 '-oStrictHostKeyChecking=no',
                                 '-oUserKnownHostsFile=/dev/null',
                                 hostname,
                                 'systemctl', '--no-ask-password', 'is-active', 'map-client.service'],
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if re.match(r'\bactive', result.stdout.decode()):
        return True
    else:
        return False

def main(argv=None):
    if argv is None:
        argv = sys.argv[1:]

    parser = argparse.ArgumentParser()
    parser.add_argument("-l", "--logconfig", dest="logconfig", help="logging configuration (default: logging.json)", default='logging.json')
    parser.add_argument("-c", "--client", dest="clients", action="append", help="Client to gather information from, can be specified multiple times", default=[])

    args = parser.parse_args(argv)

    setup_logging(default_path=args.logconfig)

    get_logger().info("Waiting for clients to finish")
    runningClients = True
    while runningClients:
        # start at False and flip to True if there is a client running
        runningClients = False
        for client in args.clients:
            running = check_client_running(client)
            if running:
                get_logger().info("Client {} is still running".format(client))
                runningClients = True
                
        if runningClients:
            # poll all clients every 30 seconds waiting for them to finish
            time.sleep(30)
    
    get_logger().info("All clients have finished")
        
if __name__ == "__main__":
    sys.exit(main())
    
