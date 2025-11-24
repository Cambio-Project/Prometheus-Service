# Stage 1: Build Spring Boot app
FROM maven:3.9.5-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Runtime with Java + Prometheus
FROM eclipse-temurin:21-jre

# Set Prometheus version
ARG PROM_VERSION=3.6.0
ARG PROM_ARCH=arm64   # set to amd64 for x86_64

# Install Prometheus + promtool
RUN if [ "${PROM_ARCH}" = "amd64" ]; then \
      FILE=prometheus-${PROM_VERSION}.linux-amd64.tar.gz; \
    else \
      FILE=prometheus-${PROM_VERSION}.linux-arm64.tar.gz; \
    fi && \
    curl -sSL "https://github.com/prometheus/prometheus/releases/download/v${PROM_VERSION}/${FILE}" \
      | tar -xz -C /opt && \
    mv /opt/prometheus-${PROM_VERSION}.linux-$( [ "${PROM_ARCH}" = "amd64" ] && echo "amd64" || echo "arm64" ) /opt/prometheus

# Symlinks for convenience
RUN ln -s /opt/prometheus/prometheus /usr/local/bin/prometheus && \
    ln -s /opt/prometheus/promtool /usr/local/bin/promtool

# Copy Spring Boot JAR
COPY --from=builder /app/target/*.jar /prometheus-service.jar

# Prometheus config dir
COPY prometheus.yml /etc/prometheus/prometheus.yml

# Expose ports
EXPOSE 9090 8080

# Entrypoint: run both Prometheus + Spring Boot
CMD sh -c "\
  prometheus \
    --config.file=/etc/prometheus/prometheus.yml \
    --storage.tsdb.path=/prometheus \
    --storage.tsdb.retention.time=3650d \
    --web.enable-lifecycle & \
  java -jar /prometheus-service.jar \
"
