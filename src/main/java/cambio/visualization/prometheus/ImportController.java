package cambio.visualization.prometheus;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

import static org.springframework.util.FileSystemUtils.deleteRecursively;

@RestController
@RequestMapping("/api")
public class ImportController {

    @Value("${prometheus.data.dir:/prometheus}")
    private String prometheusDataDir;

    @Value("${prometheus.reload.url:http://localhost:9090/-/reload}")
    private String prometheusReloadUrl;

    @PostMapping("/import")
    public ResponseEntity<String> importOpenMetrics(@RequestParam("file") MultipartFile file) {
        try {
            // Save uploaded file to temp
            File tmp = File.createTempFile("openmetrics-", ".txt");
            file.transferTo(tmp);

            System.out.println("Stored Open Metrics File at: " + tmp.getAbsolutePath());

            System.out.println("Cleaning Prometheus Data");

            File tsdb = new File(prometheusDataDir);
            if (tsdb.isDirectory()) {
                for (File f : tsdb.listFiles()) {
                    if (f.isDirectory() && f.getName().matches("^[0-9A-Fa-f]{2}.*")) {
                        deleteRecursively(f.toPath());
                    }
                }
            }

            System.out.println("Generate New Data From Metrics File");

            // Run promtool
            ProcessBuilder pb = new ProcessBuilder(
                    "promtool", "tsdb", "create-blocks-from", "openmetrics",
                    tmp.getAbsolutePath(),
                    prometheusDataDir
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();

            System.out.println("Cleaning Metrics File");

            tmp.delete();

            if (exitCode != 0) {
                return ResponseEntity.status(500)
                        .body("Failed to import OpenMetrics data. Exit code: " + exitCode);
            }

            System.out.println("Trigger Prometheus reload");

            // Trigger Prometheus reload
            RestTemplate rest = new RestTemplate();
            ResponseEntity<String> reloadResp =
                    rest.postForEntity(prometheusReloadUrl, null, String.class);

            if (!reloadResp.getStatusCode().is2xxSuccessful()) {
                return ResponseEntity.status(500)
                        .body("Data imported but reload failed: " + reloadResp.getStatusCode());
            }

            return ResponseEntity.ok("OpenMetrics data imported and Prometheus reloaded successfully.");
        } catch (IOException | InterruptedException e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }
}
