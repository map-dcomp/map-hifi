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
Save multiple images to a single file and remove "localhost" prefix from the image names.

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
    import shutil
    import tarfile
    import io
    import tempfile
    import subprocess

script_dir=os.path.abspath(os.path.dirname(__file__))

localhost_prefix="localhost/"

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

def remove_prefix(prefix, text):
    """
    Arguments:
        prefix (str): the prefix to look for
        text (str): the string to check
    Returns:
        str: text with the prefix removed
    """
    if text.startswith(prefix):
        return text[len(prefix):]
    return text


def merge_configs(configs, image_manifest):
    """
    Arguments:
        configs (list): the combined configs (modified)
        image_manifest (list): the manifest from the image
    """
    for config in image_manifest:
        if 'RepoTags' in config:
            repo_tags = config['RepoTags']
            new_repo_tags = list()
            for tag in repo_tags:
                tag = remove_prefix(localhost_prefix, tag)
                new_repo_tags.append(tag)
            config['RepoTags'] = new_repo_tags
        configs.append(config)
    

def merge_repositories(repositories, image_repository):
    """
    Arguments:
        repositories (dict): the combined repositories (modified)
        image_repository (dict): the repository from the image
    """
    
    for name, data in image_repository.items():
        name = remove_prefix(localhost_prefix, name)
        if name in repositories:
            raise RuntimeError(f"The repository name '{name}' has already been seen")
        repositories[name] = data


def podman_save(podman_output, image_name):
    """
    Arguments:
        podman_output (Path): where to write
        image_name (str): name of the image to write
    """
    output = subprocess.check_output(["podman", "save", "-o", podman_output.as_posix(), image_name])
    if len(output) > 0:
        get_logger().info(output)

        
def process_image(temp_dir, output_tar, repositories, configs, image_name):
    """
    Arguments:
        temp_dir (pathlib.Path): temporary directory to use
        output_tar (tarfile.TarFile): combined result (modified)
        repositories (dict): the combined repositories (modified)
        configs (list): the combined configs (modified)
        image_name (str): name of the image to export and add to output_tar
    """

    get_logger().info("Processing %s", image_name)
    
    podman_output = temp_dir / f"{image_name}.tar"
    podman_save(podman_output, image_name)

    if not podman_output.exists():
        raise RuntimeError(f"Saving of {image_name} failed")
    
    with tarfile.open(podman_output) as tf:
        for member in tf:
            if 'repositories' == member.name:
                image_repository = json.load(tf.extractfile(member))
                merge_repositories(repositories, image_repository)
            elif 'manifest.json' == member.name:
                image_manifest = json.load(tf.extractfile(member))
                merge_configs(configs, image_manifest)
            else:
                # check if it's in the output and add if it isn't
                try:
                    output_tar.getmember(member.name)
                    exists = True
                except KeyError:
                    exists = False

                if not exists:
                    output_tar.addfile(member, tf.extractfile(member))

    podman_output.unlink()


def write_json_to_tarfile(output_tar, name, obj):
    """
    Arguments:
        output_tar (tarfile.TarFile): where to write
        name (str): the name of the file to create in the tar file
        obj : the object to write out as JSON
    """
    json_str = json.dumps(obj)
    get_logger().debug("Wrote JSON for %s '%s'", name, json_str)
    json_str_encoded = json_str.encode("utf-8")
    t = tarfile.TarInfo(name)
    t.size = len(json_str_encoded)
    output_tar.addfile(t, fileobj=io.BytesIO(json_str_encoded))
                    
                
def main_method(args):
    
    with tempfile.TemporaryDirectory() as temp_dir_name:
        temp_dir = Path(temp_dir_name)
        
        with tarfile.open(args.output, mode='w') as output_tar:
            configs = list()
            repositories = dict()
            for image in args.images:
                process_image(temp_dir, output_tar, repositories, configs, image)

            write_json_to_tarfile(output_tar, 'repositories', repositories)
            write_json_to_tarfile(output_tar, 'manifest.json', configs)

            
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
    parser.add_argument("-o", "--output", dest="output", help="Location of multiple saved images", required=True)
    parser.add_argument(metavar="IMAGE", dest="images", nargs="+")

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
