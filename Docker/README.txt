Instructions for setting up Docker and a Docker Registry on Ubuntu 16.04

Scripts and files can be found in the "docker_fr_images", "docker_registry", "docker_fr_demo" directories.



---Install Docker and the Docker Registry---

Follow the instructions at https://docs.docker.com/engine/installation/linux/docker-ce/ubuntu/
or run ./install_docker.sh to install Docker.


To set up a Secure Docker Registry, follow the instructions at https://docs.docker.com/registry/:

1. Use openssl with the included openssl.cnf configuration file to generate certificate (client.cert) and key (client.key) files.
You can run the gen_cert.sh script to create the certificate using the openssl.cnf file.

2. Copy the certificate (client.cert) to /usr/local/share/ca-certificates/<Docker Registry IP Address>:5000.crt on systems that will be used to access the Docker registry.
Run sudo update-ca-certificates, and restart the Docker service to register the certificate as trusted.
The script trust_cert.sh contains commands to copy the certificate from the current directory to /usr/local/share/ca-certificates/<Docker Registry IP Address>:5000.crt (remember to replace the IP Address with the correct address for the DTR),
register the certificate as trusted, and restart the Docker service.

3. Replace the IP Address specified in the run_docker_registry_lxd.sh script with the IP Address of Docker Registry server.
Run the run_docker_registry_lxd.sh script to start/restart the registry server. The script contains the following command, which starts the server:
sudo docker run -d -p <Docker Registry IP Address>:5000:5000 -p <Docker Registry IP Address>:5001:5001 --restart=always --name registry_lxd \
             -v `pwd`/config2.yml:/etc/docker/registry/config.yml \
             -v `pwd`/client.cert:/etc/docker/registry/client.cert \
             -v `pwd`/client.key:/etc/docker/registry/client.key \
             registry:2

The command is configured to run the Docker Registry with the configuration file config2.yml, and client.cert and client.key (private key) for TLS.
You can change the configuration file that is used by changing config2.yml to reference a different file to be mapped to /etc/docker/registry/config.yml in the DTR container.
Refer to https://docs.docker.com/registry/configuration/ for more information on configuring the Docker Registry.



---Creating Docker Images---

The webpage at https://docs.docker.com/get-started/part2/ explains how to create a Docker image for an application. Here are is an overview of the steps involved.

1. Put the application's files into a directory.

2. Create a Dockerfile to specify how to containerize the application. The Dockerfile contains information including 
the location of the application's files (ADD)
a base image for the container such as dockerfile/Ubuntu (FROM)
port mappings from inside the container to outside the container (EXPOSE)
a command to start the application when the container starts (CMD or ENTRYPOINT)

Refer to https://docs.docker.com/engine/reference/builder/ for reference information on creating a Dockerfile.

3. Install the dockerfile/ubuntu image dependency
	docker build -t="dockerfile/ubuntu" github.com/dockerfile/ubuntu

4. To build the Docker container image
	cd into the directory containing the Dockerfile
	docker build -t [image name] .



---Pushing Docker images to the Docker Registry---

The push_image_to_docker_registry_lxd.sh script tags and pushes an image with the following commands
sudo docker tag $1 192.168.56.1:5000/v2/$1
sudo docker push 192.168.56.1:5000/v2/$1

Change IP addresses to that of the Docker Registry and run the script with the image name as a parameter:
push_image_to_docker_registry_lxd.sh [image name]



---Pulling Docker images from the Docker Registry---

To pull an image onto a machine with Docker installed and with the Docker Registry certificate trusted, run the following command:
sudo docker pull <Docker Registry IP Address>:5000/v2/[image name]



---Running Docker images in containers---


To run a docker image:
sudo docker run [image name]


-To run a docker image with a mapped port:
sudo docker run -p [external port]:[internal port] [image name]


-To run a docker image with a specified container label:
sudo docker run --name=[container label] [image name]


-To run a docker image in a container that is connected to a certain network:
sudo docker run --network=[network name] [image name]

Execute "sudo docker network ls" to list possible Docker network NAMES for [network name]
Refer to https://docs.docker.com/engine/userguide/networking/ for more information on container networking


-To run a docker image with limits on CPU and Memory:
sudo docker run --cpus=[cpu limit] --memory=[memory limit] [image name]

Refer to https://docs.docker.com/engine/admin/resource_constraints/ for more information on limiting container resources



-Examples: The following examples are taken from the two scripts in docker_fr_demo, which can each be run on a separate machine (after modification for your network and Docker registry setup) to pull the client and server from a Docker registry and start the client and server.

The following command runs the a face recongition server image that was pulled from a Docker registry at 192.168.56.1:5000/v2 with a container label "frs_1x".
To connect to the server, a client external to the container must use port 7124, which is mapped to server port 7123 inside of the container.
The command also limits the server to using a maximum of 1 CPU and 4 GB of memory.
	
	sudo docker run --cpus=1 --memory=4g -p 7124:7123 --name=frs_1x 192.168.56.1:5000/v2/frs


The following command runs the face recognition client image that was pulled from a Docker registry at 192.168.56.1:5000/v2 with a container label "frc_1".
The client conatiner must be connected to network such as "host" to be able to access IP addresses outside of the container and therefore enables the client to connect to the server.
The "192.168.56.1 7124 face_input 1.0 100 --ack" part of the command consists of command line parameters that pass into the container and to the client. 
	
	sudo docker run --network=host --name=frc_1 192.168.56.1:5000/v2/frc 192.168.56.1 7124 face_input 1.0 100 --ack



---Running Docker without sudo---

By default, docker must be run with sudo. To run docker without sudo, add the current user to the Docker group
	sudo groupadd docker			# create a docker group if it is not yet created
	sudo usermod -aG docker $USER		# add the current user to the Docker group
Then restart your system.

Refer to https://docs.docker.com/engine/installation/linux/linux-postinstall/ for more information.



---Monitoring Container Resource Usage---

Refer to https://crate.io/a/analyzing-docker-container-performance-native-tools/ for 3 ways to perform Docker container resource monitoring.

HTTP REST API method:
Use the com.bbn.map.TestingHarness.container_monitor.ContainerResourceMonitorTester utility to monitor container usage using the REST API.
To use the REST API from Java, the Docker daemon must be bound to a host:port socket in addition to being bound to its default unix:///var/run/docker.sock socket.
To do this, ensure that the docker daemon is not running using "service docker stop" and then run

	sudo dockerd -H unix:///var/run/docker.sock -H tcp://[host:port]

example: sudo dockerd -H unix:///var/run/docker.sock -H tcp://127.0.0.1:5010

These commands can also be found in the start_daemon_with_monitor_port.sh script.


Run the program as a jar using

	java -jar container_monitor.jar [host:port] [container ID 1] [NIC 1] [container ID 2] [NIC 2] ...

where [host:port] is a host and port that the Docker daemon is bound to for using its API
[container ID x] [NIC x] is a pair of parameters, the first of which is the container id or name to monitor, and the second of which is the name of the container's NIC to monitor






---Additional Resources---

Dockerfile: https://docs.docker.com/engine/reference/builder/
	CMD: https://docs.docker.com/engine/reference/builder/#cmd
	ENTRYPOINT: https://docs.docker.com/engine/reference/builder/#entrypoint
	CMD and ENTRYPOINT: https://docs.docker.com/engine/reference/builder/#understand-how-cmd-and-entrypoint-interact

Docker container networking: https://docs.docker.com/engine/userguide/networking/
Resource limits: https://docs.docker.com/engine/admin/resource_constraints/
Docker container resource monitoring REST API: https://docs.docker.com/engine/api/v1.21/



--- Container Directories ----
"container_data" is the directory that service containers can persist information to and will be stored outside the container.

"service_data" is the directory that allows all containers for a service to have service-wide shared storage space.
