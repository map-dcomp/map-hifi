# Topology notes

## specifying hardware

The topology.ns file specifies a hardware name for each node.
This name is looked up in hardware-configuations.json to determine the hardware to use on a testbed.

The capacity numbers are used by MAP to determine limits, this does
not need to match the physical hardware. Specifing values that are
greater than the actual hardware aren't well tested, but specifying
less than the actual hardware has been done commonly.

The `maximumServiceContainers` parameter specifies how many docker
containers can be run on the specified hardware.


## Leaders

Each leader (DCOP, DNS, RLG) can be on a different node.
We have typically combined the DNS and RLG on the same node.
One can probably run all 3 on the same node, but memory may be an issue.
The leader nodes are typically a rohu (DCOMP) or d710 (Emulab).

The leader nodes typically do not have docker containers run on them
to avoid issues with CPU and memory load.


## number of neighbor nodes

MAP communications information across the network through Aggregate Programming (AP).
When designing topologies the thing to know about this is that each node sends data to all of it's neighbors every 0.5 seconds.
So if a node has 5 neighbors it needs to send the same information 5 times, once for each neighbor.
Having a large number of neighbors (more than 15) can cause high CPU utilization and cause delays in the communication traffic.

Due to this some of our larger topologies "full-scale*" have router
nodes that don't run container and are there to handle some of the AP
aggregation and reduce the amount of traffic that they need to send.

## Generating topologies and demand

Some of the scenarios are generated using python scripts and the demand is also generated using a combination of shell and python scripts.
These topologies have a `generate_topology.py` file for creating the network topology.

The demand generation scripts use a `demand-params.json` (or similarly named) input file.
There is documentation on this parameters file at the top of the script (../MAP-code/scenarios/generate_demand.py).

