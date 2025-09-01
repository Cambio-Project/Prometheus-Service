Endpoint:
api/import
Expects OpenMetrics file as Parameter "file"

Local Build:
podman build -t prom-with-import .

Run:
podman run -p 9090:9090 -p 8080:8080 prom-with-import
