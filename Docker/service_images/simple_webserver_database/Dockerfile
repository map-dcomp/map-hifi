FROM simple_webserver_base

# set command to run server
ENTRYPOINT ["java", "-Xmx1G", "-jar", "simple-webserver-executable.jar"]
CMD ["--csv", "/app_metrics_data/processing_latency.csv", "--responseType", "database"]
