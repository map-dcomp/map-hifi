# Dockerfile for Face Recognition Server

# Use map_base
FROM map_base



# add service files to the working directory
ADD . .

# make the Face Recognition port visible outside of the container
EXPOSE 7123

# create an output folder for the server that will persist after each instance stops
RUN \
  mkdir /instance_data/face_output

# set command to run server
ENTRYPOINT ["java", "-jar", "FaceDetectionServer.jar"]
CMD ["7123", "/instance_data/face_output", "--ack", "--csv", "/app_metrics_data/processing_latency.csv"]
