FROM simple_webserver_base

# set command to run server
ENTRYPOINT ["java", "-jar", "simple-webserver-executable.jar"]
CMD ["--csv", "/app_metrics_data/processing_latency.csv", "--responseType", "large"]
