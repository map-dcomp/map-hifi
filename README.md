This repository contains code for executing the MAP system in
[Emulab](emulab.net/) or the [DCOMP
testbed](https://www.dcomptb.net/) as well as a lo-fi simulator.

The documentation for the lo-fi simulator can be found at (MAP-code/README.md).
This simulator is meant to be run on a single system for algorithm testing.

Run `./setup` to get your git repo setup with submodules.

To build use `./gradlew assemble`


You will need to create the docker images for the test applications
and put that in an accessible location in your test environment.  
This can be done with either docker or podman. If podman is installed,
it will be used.
See the scripts in [Docker/service_images] for creating the images.

The deployment scripts assume that the file is in
`/proj/a3/map/registry/map_service_images.tar` on Emulab and in
`/project/map/registry/map_service_images.tar` on the DCOMP
testbed. These locations are specified in
(stage-experiment/src/main/resources/ansible/11-copy-registry-images_dcomp.yml)
and
(stage-experiment/src/main/resources/ansible/11-copy-registry-images_emulab.yml).

See (stage-experiment/docs/README.md) for information on staging an experiment.

See (docs/testbed-terminology.txt) for information on translating
Emulab terms to DCOMP testbed terms.

Notes on using the DCOMP testbed are at (docs/dcomp-notes.md)

See (docs/topology-considerations.md) for some tips on creating and modifying topologies to run.

A modified version of `iftop` is included in `stage-experiment/iftop`. 
This adds an output mode that is easy to parse by the MAP code.

See (MAP-code/scenarios) for scenarios that have been used in the
past.  In most of the scenario directories you will find a shell
script named `stage.sh`. This script is used to create the files that
are used to run the simulation in the testbed.  A typical execution of
this script is `./stage.sh --experiment large --output run01` This
will create files to execute the simulation in the experiment with the
name `large` and the project `map` on DCOMP and `a3` on Emulab.
Additional options can be specified, these can be found by executing
`java -jar
stage-experiment/build/libs/stage-experiment-{version}-executable.jar
--help`.

Once the experiment finishes the resulting output can be used to
create various tables and charts for analysis.  These can be created
by executing `MAP-code/src/MAP-ChartGeneration/scripts/process-hifi.sh
{directory containing result tar file}`.  This will create a `charts`
directory that contains all of the tables and graphs that are used for
analysis.
