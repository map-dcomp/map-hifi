This repository contains code for executing the MAP system in
[Emulab](emulab.net/) or the [DCOMP
testbed](https://www.dcomptb.net/).

Run `./setup` to get your git repo setup with submodules.

To build use `./gradlew build -x`
  * you can skip the tests by adding `-x test` to the command


You will need to create the docker images for the test applications
and put that in an accessible location in your test environment.  
This can be done with either docker or podman. If podman is installed,
it will be used.
See the scripts in [Docker/service_images] for creating the images.

The deployment scripts assume that the file is in
/proj/a3/map/registry/map_service_images.tar on Emulab and in
/project/map/registry/map_service_images.tar on the DCOMP
testbed. These locations are specified in
[stage-experiment/src/main/resources/ansible/11-copy-registry-images_dcomp.yml]
and
[stage-experiment/src/main/resources/ansible/11-copy-registry-images_emulab.yml].


See [stage-experiment/docs/README.md] for information on staging an experiment.

See [docs/testbed-terminology.txt] for information on translating
Emulab terms to DCOMP testbed terms.
