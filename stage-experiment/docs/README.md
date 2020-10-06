The `StageExperiment` tool is used to create files to be pushed out to emulab using ansible.

All filenames must match the case of the node name in topoolgy.ns.

See `run.sh` that creates the ansible files to run 2-ncp-2-client in the emulab experiment jps as part of the a3 project. See the help documentation on `StageExperiment` for more information on the parameters. Note that you will need to modify the docker registry node.

The container names directory contains a file for each NCP.  The file
contains an IP address per line.  This specifies the IP addresses for the
containers. Blank lines are ignored.  This directory is not required if
there are no IP addresses specified in the toplogy file.  When this is the
case `StageExperiment` will automatically assign IP addresses (this is the
preferred method of operation).

Once the files are created the experiment should be run in Emulab by either
copying the files to a node in the topology and running
`execute-scenario.sh` directly or by running `run-scenario_emulab.sh`.  The
second script will copy the files up to a node in the topology and then run
`execute-scenario.sh` from there.

For running on the DCOMP testbed copy the generated directory to the XDC
and execute `run-scenario_dcomp.sh`.

There is an option to the run scenario scripts, "--auto-swap", that will
swap out the Emulab experiment once the scenario has finished.


To run an experiment in batch mode (only on emulab)
  1. copy the ansible directory to a folder in the project directory on emulab
  2. modify toplogy.ns to have `tb-set-node-startcmd $nodeA0 "<dir>/run-scenario-batch_emulab.sh"` just before `$ns run` at the bottom of the script.
    * replace `<dir>` with the path to where you put the files
  3. Modify the experiment to have the new NS file and check the box for batch execution
  4. Queue the job to run, you will be emailed when it starts and stops

See docs/simulation_runner.md in the lo-fi repository for information about
how the agent configuration is specified.

Dynamic routing
==============

If you want to enable dynamic routing for testing link failures, set the `rtProto` value in `topology.ns` to `Manual`.

Starting clients
===============

The file `client-service-configuration.json` specifies how clients start up for each service.

External process
----------------

When starting a client with `EXTERNAL_PROCESS` the common pattern is to create a shell script.
There are some examples in `src/main/resources/ansible/client`.
The process will be called with the following environment variables
* SERVICE_HOSTNAME - the hostname that should be used to find the container running the service
* REGISTRY_HOSTNAME - the hostname of the docker registry

The REGISTRY_HOSTNAME variable is also available to the pre-start scripts
