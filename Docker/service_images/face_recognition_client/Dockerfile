# Dockerfile for Face Recognition Client

# Use map_base
FROM map_base



# add files to the working directory
ADD . .

# make the Face Recognition port visible outside of the container
# EXPOSE 7123

# set command to run client
ENTRYPOINT ["java", "-jar", "ImageSendingClient.jar"]
