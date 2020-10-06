# Dockerfile for Networked File Store Server

# Use map_base
FROM map_base

RUN apt-get install -y stress-ng cpulimit && \
    apt-get clean

# add service files to the working directory
ADD . .

# set command to run server
ENTRYPOINT ["java", "-jar", "fake-load-server.jar"]
