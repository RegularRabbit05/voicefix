FROM ghcr.io/graalvm/graalvm-community:latest
RUN mkdir -p /opt/app
COPY japp.jar /opt/app/
CMD ["java", \
     "-XX:+UseContainerSupport", \
     "-XX:+UseSerialGC", \
     "-Xss512k", \
     "-XX:CICompilerCount=2", \
     "-XX:MaxMetaspaceSize=128m", \
     "-jar", "/opt/app/japp.jar"]
