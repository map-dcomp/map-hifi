See https://www.mergetb.org/docs/ for documentation on the DCOMP testbed from ISI.
You should read through this to understand the concepts of the testbed. 
The topology files are created by the StageExperiment application in the MAP hi-fi repository.

hifi-repository: gitoLite@dsl-external.bbn.com:map-high-fidelity-testbed
Make sure to execute "./setup" after clone

Goto https://launch.mergetb.net/ and click "login".
Then use Sign Up to create an account.
Email BBN technical contact with your username and they will get ISI to authorize your account and attach it to the MAP project.
Once you are logged in make sure you upload a public SSH key to the portal. This is used to login to the testbed servers via ssh.
Get setup with Slack, you will receive an invite via email from an ISI team member.

Experiments contain XDCs. *This may change in the near future*
An XDC is a host that is used to create and monitor experiments.
It can be thought of as the bastion host for the experiment.

An experiment contains a materialization/realization. One can have
multiple realizations in the same experiment. However the standard
pattern for MAP is to have only a single realization per
experiment. The MAP scripts refer to the realization as the scenario.

Experiment and scenario names must start with a character and can only
contain letters and numbers. Underscores may now be allowed, but
haven't been tested much.

To help prevent disconnects to the mergetb infrastructure put the following in ~/.ssh/config 

    Host *.mergetb.io
      ServerAliveInterval 100
      ServerAliveCountMax 2

To connect to an XDC you
must go through a jump node (jumpa, jumpb,jumpc) like so:

    ssh -A -J jumpc.mergetb.io:2202 xdc-expid-projid
    
* xdc - name of xdc
* expid - name of experiment
* projid - name of project

To make it easy to connect to any XDC, put this in your ~/.ssh/config.
This works for all XDCs in the map project. If you have another project,
add it to the host mapping glob match.

    Host *-map
      ServerAliveInterval 100
      ServerAliveCountMax 2
      Hostname %h
      ProxyJump jumpc.mergetb.io:2202
      

To access nodes I suggest that you create 2 ssh keys, one with a password and one without.
The one with the password should be added to the DCOMP testbed as the key to use to connect to the server.
The one without can be added to your home directory on the DCOMP testbed and then add the following to ~/.ssh/config on the DCOMP testbed
    
    Host *
      IdentityFile ~/.ssh/dcomptb

You will need to add the public side of this key to
~/.ssh/authorized_keys. This allows you to connect to all of the experiment
nodes without a password, but requires a password to connect to the DCOMP
testbed.

We should always set routing to "Manual" for dcomp so that quagga is always used.


# permissions

See https://gitlab.com/mergetb/portal/services/-/blob/master/policy/base.yml for the
definition of what permissions mean.

# network configuration

172.30.0.0/16 is the control network, like emulab's 155.0.0.0/8.
This is always on `eth0`.


    1: lo: <LOOPBACK,UP,LOWER_UP> mtu 65536 qdisc noqueue state UNKNOWN group default qlen 1000
        link/loopback 00:00:00:00:00:00 brd 00:00:00:00:00:00
        inet 127.0.0.1/8 scope host lo
           valid_lft forever preferred_lft forever
        inet6 ::1/128 scope host 
           valid_lft forever preferred_lft forever
    2: eth0: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 qdisc mq state UP group default qlen 1000
        link/ether 00:08:a2:0d:e0:f8 brd ff:ff:ff:ff:ff:ff
        inet 172.30.0.10/16 brd 172.30.255.255 scope global dynamic eth0
           valid_lft 151sec preferred_lft 151sec
        inet6 fe80::208:a2ff:fe0d:e0f8/64 scope link 
           valid_lft forever preferred_lft forever
    3: eth1: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 qdisc mq state UP group default qlen 1000
        link/ether 00:08:a2:0d:e0:f9 brd ff:ff:ff:ff:ff:ff
        inet6 fe80::208:a2ff:fe0d:e0f9/64 scope link 
           valid_lft forever preferred_lft forever


# Workflow for a MAP scenario

1. Create the hi-fi files from scenario
  * use the stage.sh (or stage-all.sh) script in the scenario directory
  * note that experiment and scenario names must be alphanumeric and not start with a number
1. create DCOMP experiment (web interface or command line from existing XDC)
  * This won't have any network topology.
  * The experiment can be created from the command line as well and then the topology source can be specified.
1. create DCOMP xdc (web interface or command line from existing XDC)
1. Login xdc and install rsync
  * ssh -A -J jumpc.mergetb.io:2202 xdc-expid-projid
  * sudo apt install rsync xz-utils
1. copy MAP scenario to xdc (local to xdc through jump host)
  * scp -r -o 'ProxyJump jumpc.mergetb.io:2202' generated_* xdc-expid-projid:
    * good idea to tar and compress the data first
  * if ssh configured for automatic jump host
    * `rsync --progress -r generated_*/ xdc-expid-projid:dir/`
1. mergetb login username
  * *needs to be done manually*
1. xdc login username
  * *needs to be done manually*
1. execute MAP scenario (from xdc to experiment nodes)
  * `ssh -A -J jumpc.mergetb.io:2202 xdc-expid-projid`
  * cd generated_*
  * `run-scenario_dcomp.sh`
    * May need to run individual commands manually as DCOMP can be unstable
    * `project`, `experiment` and `scenario` are defined in `run-scenario_dcomp.sh`. You can copy the lines out of the script and paste them into your shell.
    * mergetb -p ${project} push ${experiment} dcomp-topology.py
    * mergetb -p ${project} realize --accept ${experiment} ${scenario}
    * mergetb -p ${project} materialize ${experiment} ${scenario}
    * mergetb -p ${project} wait ${experiment} ${scenario}
    * xdc attach ${project} ${experiment} ${scenario}
  * Node names:
    * $name.${scenario}.${experiment}.${project}
    
1. clean up when done to free up resources
    * xdc detach
    * mergetb -p ${project} dematerialize ${experiment} ${scenario}
    * mergetb -p ${project} wait ${experiment} ${scenario}
    * mergetb -p ${project} free ${experiment} ${scenario}
    * mergetb -p ${project} wait ${experiment} ${scenario}


# add users to project
mergetb project add map ${user}

There's also a web UI element.

# troubleshooting errors

The XDCs are containers that have memory and CPU limits. However
looking at `top` or `free` one will see all of the memory that the
host has, not the limit that the container has.  If a process dies
with "Killed", it's probably the case that the XDC hit the memory
limit and killed the process.
