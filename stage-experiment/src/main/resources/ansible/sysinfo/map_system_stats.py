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
    from pathlib import Path
    import psutil
    import time
    import csv

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

        
def is_docker(pid):
    try:
        with open('/proc/{}/cgroup'.format(pid)) as f:
            for line in f:
                line = line.strip()
                cid, subsys, name = line.split(':', 2)
                if re.search(r'docker', name):
                    return True
    except:
        # got an exception, assume the process went away and then we
        # don't really care much
        get_logger().debug("exception getting cgroup information: %s", sys.exc_info()[0])
        return False
    return False


def gather_stats(output_dir):
    now = int(time.time())
    fname = output_dir / '{}.csv'.format(now)
    with open(fname, 'w') as f:
        writer = csv.writer(f)
        writer.writerow(['pid', 'name', 'user', 'cpu_user', 'cpu_sys', 'memory_rss', 'memory_vms', 'docker', 'cmdline'])
        
        for process in psutil.process_iter():
            try:
                with process.oneshot():
                    cpu = process.cpu_times()
                    mem = process.memory_info()
                    docker = is_docker(process.pid)
                    cmdline = " ".join(process.cmdline())
                    writer.writerow([process.pid, process.name(), process.username(), cpu.user, cpu.system, mem.rss, mem.vms, docker, cmdline])
            except:
                # got an exception, assume the process went away and then we
                # don't really care much
                get_logger().debug("exception gathering processing information, skipping process: %s", sys.exc_info()[0])

        system_cpu = psutil.cpu_times()
        total_uptime = system_cpu.user + system_cpu.system + system_cpu.idle
        writer.writerow([-1, "system_total", "system_total", total_uptime, -1, psutil.virtual_memory().total, psutil.swap_memory().total, False, ""])        


def main_method(args):
    output_dir = Path(args.output)
    if not output_dir.exists():
        output_dir.mkdirs(parents=True, exist_ok=True)
        
    while True:
        gather_stats(output_dir)
        time.sleep(args.interval)


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
    parser.add_argument("--interval", dest="interval", help="Collection interval in seconds", type=int, default=10)
    parser.add_argument("--output", dest="output", help="Output directory", required=True)

    args = parser.parse_args(argv)

    setup_logging(default_path=args.logconfig)

    if args.debug:
        import pdb, traceback
        try:
            main_method(args)
        except:
            extype, value, tb = sys.exc_info()
            traceback.print_exc()
            pdb.post_mortem(tb)    
    else:
        main_method(args)
            
        
if __name__ == "__main__":
    sys.exit(main())
