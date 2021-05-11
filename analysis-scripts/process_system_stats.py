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

"""
Create a csv file per node that reports the memory and cpu usage as percentages over time.
"""


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
    import matplotlib
    import numpy as np
    import matplotlib.pyplot as plt
    import pandas as pd
    from pathlib import Path
    import csv
    from collections import namedtuple

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


class Base(object):
    def __str__(self):
        return str(self.__dict__)
    
    def __repr__(self):
        type_ = type(self)
        module = type_.__module__
        qualname = type_.__qualname__        
        return f"<{module}.{qualname} {str(self)}>"

class ResourceUsage:
    def __init__(self):
        # seconds CPU has been in user or system
        self.cpu_time = 0
        
        # bytes resident set size
        self.memory_rss = 0
        # bytes virtual memory size
        self.memory_vms = 0
        
    def add_usage(self, row):
        self.cpu_time = self.cpu_time + float(row['cpu_user'])
        self.cpu_time = self.cpu_time + float(row['cpu_sys'])
        self.memory_rss = self.memory_rss + float(row['memory_rss'])
        self.memory_vms = self.memory_vms + float(row['memory_vms'])

        
ResourcePercent = namedtuple('ResourcePercent', ['cpu', 'memory'])


def is_application(row):
    if row['docker'] == 'True':
        # could filter out docker itself and the registry if we want to 
        # this would be done based on the name and cmdline columns
        return True
    

def is_map(row):
    if is_application(row):
        return False
    if row['user'] == 'map':
        return True
    if row['name'] == 'java' and row['cmdline'] is not None:
        if re.match(r'map-dns\.jar', row['cmdline']):
            return True
    return False


def process_file(datafile):
    application_usage = ResourceUsage()
    map_usage = ResourceUsage()
    other_usage = ResourceUsage()

    total_uptime = None
    total_memory_physical = None

    with open(datafile) as f:
        reader = csv.DictReader(f)
        for row in reader:
            if row['name'] == 'system_total' and row['user'] == 'system_total':
                # special row with system totals
                total_uptime = float(row['cpu_user'])
                total_memory_physical = float(row['memory_rss'])
            else:
                if is_application(row):
                    application_usage.add_usage(row)
                elif is_map(row):
                    map_usage.add_usage(row)
                else:
                    other_usage.add_usage(row)
    
    if total_uptime is None or total_memory_physical is None:
        #raise ValueError("Missing totals")
        print("Missing totals row in ", datafile, "skipping")
        return None, None, None
    
    map_percent = ResourcePercent(cpu = (map_usage.cpu_time / total_uptime), memory = (map_usage.memory_rss / total_memory_physical))
    application_percent = ResourcePercent(cpu = (application_usage.cpu_time / total_uptime), memory = (application_usage.memory_rss / total_memory_physical))
    other_percent = ResourcePercent(cpu = (other_usage.cpu_time / total_uptime), memory = (other_usage.memory_rss / total_memory_physical))

    return map_percent, application_percent, other_percent


def process_node(node_dir):
    stats_dir = node_dir / 'system_stats'

    df_rows = list()
    for datafile in stats_dir.glob("*.csv"):
        timestamp = int(datafile.stem)
        (map_percent, application_percent, other_percent) = process_file(datafile)
        if map_percent is not None:
            dict1 = dict()
            dict1['timestamp'] = timestamp
            dict1['map_cpu_percent'] = map_percent.cpu
            dict1['map_memory_percent'] = map_percent.memory
            dict1['application_cpu_percent'] = application_percent.cpu
            dict1['application_memory_percent'] = application_percent.memory
            dict1['other_cpu_percent'] = other_percent.cpu
            dict1['other_memory_percent'] = other_percent.memory
            df_rows.append(dict1)

    df = pd.DataFrame(df_rows)
    df.set_index(['timestamp'], inplace=True)
    
    return df


def main_method(args):
    sim_output = Path(args.sim_output)
    if not sim_output.exists():
        get_logger().error("%s does not exist", sim_output)
        return -1

    stats_output_dir = Path(args.stats_output)
    stats_output_dir.mkdir(parents=True, exist_ok=True)
    
    for node_dir in sim_output.iterdir():
        if not node_dir.is_dir():
            continue
        if node_dir.name == "inputs":
            continue
        df = process_node(node_dir)
        node_name = node_dir.name
        output_file = stats_output_dir / f"{node_name}.csv"
        df.to_csv(output_file)
    


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
    parser.add_argument("--sim-output", dest="sim_output", help="Output directory from the simulation", required=True)
    parser.add_argument("--stats-output", dest="stats_output", help="Directory to write the stats summaries to", required=True)

    args = parser.parse_args(argv)

    setup_logging(default_path=args.logconfig)

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


base_output = Path('test-scenario/output/scratch.nodeType/')


# In[3]:


node_dir = base_output / 'B'



# In[46]:


df


# In[48]:


df.describe()


# In[49]:





# In[51]:



