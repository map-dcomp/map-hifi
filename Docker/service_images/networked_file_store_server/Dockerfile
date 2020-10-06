# Dockerfile for Networked File Store Server

# Use map_base
FROM map_base




# add service files to the working directory
ADD . .

# make the web port visible outside of the container
EXPOSE 59122

# set command to run server
ENTRYPOINT ["java", "-jar", "networked-file-store-server.jar"]
