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
    from pathlib import Path
    import subprocess
    import datetime
    import time

script_dir=(Path(__file__).parent).resolve()


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


class Base(object):
    def __str__(self):
        return str(self.__dict__)
    
    def __repr__(self):
        type_ = type(self)
        module = type_.__module__
        qualname = type_.__qualname__        
        return f"<{module}.{qualname} {str(self)}>"

    
def get_experiment_info():
    """
    Returns:
        str: project or None if not connected
        str: experiment
        str: materialization
    """
    result = subprocess.run(["xdc", "show", "tunnel"], universal_newlines=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    if result.returncode != 0:
        return None, None, None
    else:
        for line in result.stdout.split('\n'):
            match = re.match(r'^Materialization:\s+(\S+)\s+(\S+)\s+(\S+)', line)
            if match:
                project = match.group(1)
                experiment = match.group(2)
                materialization = match.group(3)
                return project, experiment, materialization
            else:
                print("No match '%s'", line)
    get_logger().error("Connected, but can't get materialization information")
    return None, None, None

    
def check_hosts():
    """
    Returns:
        list(str): hosts that are not accessible, None on an error checking
    """
    result = subprocess.run(["ansible", "-m", "ping", '-i', 'hosts.dcomp', '--one-line', 'all'], universal_newlines=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    bad_hosts = list()
    for line in result.stdout.split('\n'):
        match = re.match(r'^(?P<host>[^\.\s]+)\S*\s+\|\s+(?P<status>\S+)\s+', line)
        if match:
            host = match.group('host')
            status = match.group('status')
            if status != 'SUCCESS':
                bad_hosts.append(host)
    return bad_hosts


def reboot_node(node):
    """
    Arguments:
        node(str): name of node to reboot
    """
    result = subprocess.run(["xdc", "power", "cycle", node], universal_newlines=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    if result.returncode != 0:
        get_logger().error("Error rebooting node %s: %s", node, result.stdout)

        
def main_method(args):
    (project, experiment, materialization) = get_experiment_info()
    if project is None:
        get_logger().error("Not connected to a materialization")
        return -1

    get_logger().info("Connected to %s %s %s", project, experiment, materialization)

    # how many times the host needs to be "down" to consider it really down
    failure_limit = 3

    # how many failures after a reboot before we try another reboot
    reboot_limit = 5
        
    # host to counter
    bad_host_count = dict()
    nodes_rebooting = dict()
    while True:
        bad_hosts = check_hosts()
        if bad_hosts is not None:
            current_time = datetime.datetime.now()
            if len(bad_hosts) < 1:
                get_logger().info("%s: All hosts are fine", current_time.isoformat())
                bad_host_count = dict()
                nodes_rebooting = dict()
            else:                
                # increment counter for bad hosts
                for host in bad_hosts:
                    if host in nodes_rebooting:
                        nodes_rebooting[host] = nodes_rebooting[host] + 1
                    else:
                        bad_host_count[host] = bad_host_count.get(host, 0) + 1

                # determine which hosts to remove and which to reboot
                to_remove = list()
                for bad_host, count in bad_host_count.items():
                    if bad_host not in bad_hosts:
                        to_remove.append(bad_host)
                    elif count >= failure_limit:
                        get_logger().warn("%s: Restarting host %s.%s.%s.%s", current_time.isoformat(), host, materialization, experiment, project)
                        reboot_node(bad_host)
                        
                        # node is now rebooting, remove from bad_hosts and add to nodes_rebooting
                        to_remove.append(bad_host)
                        nodes_rebooting[bad_host] = 0

                # remove hosts that are now fine
                for remove in to_remove:
                    del bad_host_count[remove]

                # check nodes_rebooting
                reboot_remove = list()
                reboot_reset = list()
                for node, count in nodes_rebooting.items():
                    if node not in bad_hosts:
                        reboot_remove.append(node)
                    elif count > reboot_limit:
                        get_logger().warn("%s: Node %s hasn't rebooted in %d checks, rebooting again", current_time.isoformat(), node, count)
                        reboot_node(node)
                        reboot_reset.append(node)
                for node in reboot_remove:
                    del nodes_rebooting[node]
                for node in reboot_reset:
                    nodes_rebooting[node] = 0

        # give the user some information about the internal state
        if len(bad_host_count) > 0:
            get_logger().warn("%s: bad hosts: %s", current_time.isoformat(), bad_host_count)
            
        if len(nodes_rebooting) > 0:
            get_logger().warn("%s: Nodes rebooting: %s", current_time.isoformat(), nodes_rebooting)
            
        time.sleep(60)


def multiprocess_logging_handler(logging_queue, logconfig, running):
    import time
    setup_logging(default_path=logconfig)

    def process_queue():
        while not logging_queue.empty():
            try:
                record = logging_queue.get(timeout=1)
                logger = logging.getLogger(record.name)
                logger.handle(record)
            except (multiprocessing.Queue.Empty, multiprocessing.TimeoutError) as e:
                # timeout was hit, just return
                pass
        
    while running.value > 0:
        process_queue()

    # process any last log messages
    process_queue()

    
def main(argv=None):
    if argv is None:
        argv = sys.argv[1:]

    class ArgumentParserWithDefaults(argparse.ArgumentParser):
        '''
        From https://stackoverflow.com/questions/12151306/argparse-way-to-include-default-values-in-help
        '''
        def add_argument(self, *args, help=None, default=None, **kwargs):
            if help is not None:
                kwargs['help'] = help
            if default is not None and args[0] != '-h':
                kwargs['default'] = default
                if help is not None:
                    kwargs['help'] += ' (default: {})'.format(default)
            super().add_argument(*args, **kwargs)
        
    parser = ArgumentParserWithDefaults(formatter_class=argparse.RawTextHelpFormatter)
    parser.add_argument("-l", "--logconfig", dest="logconfig", help="logging configuration (default: logging.json)", default='logging.json')
    parser.add_argument("--debug", dest="debug", help="Enable interactive debugger on error", action='store_true')

    args = parser.parse_args(argv)

    if 'multiprocessing' in sys.modules:
        running = multiprocessing.Value('b', 1)
        logging_queue = multiprocessing.Queue()
        logging_listener = multiprocessing.Process(target=multiprocess_logging_handler, args=(logging_queue, args.logconfig,running,))
        logging_listener.start()

        h = logging.handlers.QueueHandler(logging_queue)
        root = logging.getLogger()
        root.addHandler(h)
        root.setLevel(logging.DEBUG)
    else:
        logging_listener = None
        setup_logging(default_path=args.logconfig)

    try:
        if args.debug:
            import pdb, traceback
            try:
                return main_method(args)
            except:
                extype, value, tb = sys.exc_info()
                traceback.print_exc()
                pdb.post_mortem(tb)    
        else:
            return main_method(args)
    finally:
        if logging_listener:
            running.value = 0
        
            
if __name__ == "__main__":
    sys.exit(main())
