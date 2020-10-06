These are scenarios that are known to work in Emulab.
There is a readme in each directory to explain what the scenario does.

An example call to running StageExperiment is shown in run-generic.sh.
This assumes that the base directory matches the structure of 15node_1-container.

The file client-service-configuration.json in this directory should be used
for all experiments in Emulab.

The file service-configurations.template.json should be used as a template
for all scenarios in Emulab.  On will want to choose which services to run
in the experiment and where the default instances are.
