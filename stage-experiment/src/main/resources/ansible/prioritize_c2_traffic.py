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
    import logging
    import logging.config
    import json
    from pathlib import Path
    import map_common
    import subprocess

script_dir=Path(__file__).parent.absolute()

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
    try:
        path = Path(default_path)
        value = os.getenv(env_key, None)
        if value:
            path = Path(value)
        if path.exists():
            with open(path, 'r') as f:
                config = json.load(f)
            logging.config.dictConfig(config)
        else:
            logging.basicConfig(level=default_level)
    except:
        print(f"Error configuring logging, using default configuration with level {default_level}")
        logging.basicConfig(level=default_level)


def shape_interface(ifce):
    tc = '/sbin/tc'

    # clear previous rules
    cmd_result = subprocess.run([tc, 'qdisc', 'del', 'dev', ifce, 'root'])
    # don't check the return code, if there is no shaping setup the command will return an error

    # setup 3 priority bands
    cmd_result = subprocess.run([tc, 'qdisc', 'add', 'dev', ifce, 'root', 'handle', '1:', 'prio'])
    cmd_result.check_returncode()
    
    # assign sfq to each band
    cmd_result = subprocess.run([tc, 'qdisc', 'add', 'dev', ifce, 'parent', '1:1', 'handle', '10:', 'sfq'])
    cmd_result.check_returncode()
    
    cmd_result = subprocess.run([tc, 'qdisc', 'add', 'dev', ifce, 'parent', '1:2', 'handle', '20:', 'sfq'])
    cmd_result.check_returncode()

    cmd_result = subprocess.run([tc, 'qdisc', 'add', 'dev', ifce, 'parent', '1:3', 'handle', '30:', 'sfq'])
    cmd_result.check_returncode()
    
    # prio here refers to the filter priority
    # high priority filter to assign port 53 traffic to priority band 1 (class 10:)
    for port in [53, 50042]:
        cmd_result = subprocess.run([tc, 'filter', 'add', 'dev', ifce, 'protocol', 'ip', 'parent', '1:0', 'prio', '1', 'u32', 'match', 'ip', 'sport', str(port), '0xffff', 'flowid', '10:'])
        cmd_result.check_returncode()
        
        cmd_result = subprocess.run([tc, 'filter', 'add', 'dev', ifce, 'protocol', 'ip', 'parent', '1:0', 'prio', '1', 'u32', 'match', 'ip', 'dport', str(port), '0xffff', 'flowid', '10:'])
        cmd_result.check_returncode()

    # low priority filter to assign everything else to priority band 2 (class 20:)
    cmd_result = subprocess.run([tc, 'filter', 'add', 'dev', ifce, 'protocol', 'ip', 'parent', '1:0', 'prio', '2', 'matchall', 'flowid', '20:'])
    cmd_result.check_returncode()
    
    
def main_method(args):
    control_networks = map_common.load_excluded_subnets()
    interfaces = map_common.get_all_interfaces()
    experiment_interfaces = map_common.get_experiment_interfaces(interfaces, control_networks)
    
    for (ifce, ip, subnet) in experiment_interfaces:
        shape_interface(ifce)



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

    setup_logging(default_path=args.logconfig)
    if 'multiprocessing' in sys.modules:
        # requires the multiprocessing-logging module - see https://github.com/jruere/multiprocessing-logging
        import multiprocessing_logging
        multiprocessing_logging.install_mp_handler()

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
        
            
if __name__ == "__main__":
    sys.exit(main())
