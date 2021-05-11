# Dockerfile for MAP base image

# Pull base image
FROM ubuntu:20.04


# Install Java
RUN apt-get update && \
    apt-get upgrade -y && \
    apt-get install -y  openjdk-11-jdk && \
    apt-get clean


# create mounted target folders for data shared with host and corresponding environment variables
RUN \
  mkdir "/app_metrics_data" && \
  mkdir "/instance_data" && \
  mkdir "/service_data"

ENV APP_METRICS_DATA_PATH /app_metrics_data
ENV INSTANCE_DATA_PATH /instance_data
ENV SERVICE_DATA_PATH /service_data


# Define working directory.
WORKDIR "/service"


