FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY target/*.jar app.jar

EXPOSE 8080

# Container-aware heap sizing: the JVM computes the max heap from the container's memory
# limit (docker-compose mem_limit) rather than assuming it owns all of the host's RAM.
# Without this a Raspberry Pi + Postgres can exceed physical RAM and hit the OOM killer.
# Override JAVA_OPTS at runtime to tune for the target hardware.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=60.0 -XX:InitialRAMPercentage=30.0"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
