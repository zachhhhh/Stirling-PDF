package stirling.software.common.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import stirling.software.common.model.ApplicationProperties;
import stirling.software.common.tenant.TenantContextSupplier;
import stirling.software.common.tenant.TenantContextSupplier.TenantDescriptor;

/**
 * Service for managing temporary files in Stirling-PDF. Provides methods for creating, tracking,
 * and cleaning up temporary files. When multi-tenancy is enabled, files are partitioned by tenant
 * to prevent cross-tenant disclosure.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TempFileManager {

    private static final String DEFAULT_PREFIX = "stirling-pdf-";

    private final TempFileRegistry registry;
    private final ApplicationProperties applicationProperties;
    private TenantContextSupplier tenantContextSupplier;

    @Autowired(required = false)
    void setTenantContextSupplier(TenantContextSupplier tenantContextSupplier) {
        this.tenantContextSupplier = tenantContextSupplier;
    }

    /**
     * Create a temporary file with the configured prefix. The file is automatically registered with
     * the registry and stored under a tenant-specific directory when multi-tenancy is active.
     */
    public File createTempFile(String suffix) throws IOException {
        Path baseDir = resolveTenantAwareBase();
        Path tempFilePath = Files.createTempFile(baseDir, prefix(), suffix);
        return registry.register(tempFilePath.toFile());
    }

    /** Create a temporary directory under the tenant-scoped root. */
    public Path createTempDirectory() throws IOException {
        Path baseDir = resolveTenantAwareBase();
        Path tempDirPath = Files.createTempDirectory(baseDir, prefix());
        return registry.registerDirectory(tempDirPath);
    }

    /** Convert an uploaded file into a tenant-scoped temp file. */
    public File convertMultipartFileToFile(MultipartFile multipartFile) throws IOException {
        if (multipartFile == null) {
            throw new IllegalArgumentException("Multipart file cannot be null");
        }
        if (multipartFile.isEmpty()) {
            throw new IllegalArgumentException("Multipart file cannot be empty");
        }

        File tempFile = createTempFile(determineSuffix(multipartFile.getOriginalFilename()));
        try (InputStream inputStream = multipartFile.getInputStream();
                OutputStream outputStream =
                        Files.newOutputStream(tempFile.toPath(), StandardOpenOption.WRITE)) {
            inputStream.transferTo(outputStream);
        }
        return tempFile;
    }

    public boolean deleteTempFile(File file) {
        if (file != null && file.exists()) {
            boolean deleted = file.delete();
            if (deleted) {
                registry.unregister(file);
                log.debug("Deleted temp file: {}", file.getAbsolutePath());
            } else {
                log.warn("Failed to delete temp file: {}", file.getAbsolutePath());
            }
            return deleted;
        }
        return false;
    }

    public boolean deleteTempFile(Path path) {
        if (path != null) {
            try {
                boolean deleted = Files.deleteIfExists(path);
                if (deleted) {
                    registry.unregister(path);
                    log.debug("Deleted temp file: {}", path);
                } else {
                    log.debug("Temp file already deleted or does not exist: {}", path);
                }
                return deleted;
            } catch (IOException e) {
                log.warn("Failed to delete temp file: {}", path, e);
                return false;
            }
        }
        return false;
    }

    public void deleteTempDirectory(Path directory) {
        if (directory != null && Files.isDirectory(directory)) {
            try {
                GeneralUtils.deleteDirectory(directory);
                log.debug("Deleted temp directory: {}", directory);
            } catch (IOException e) {
                log.warn("Failed to delete temp directory: {}", directory, e);
            }
        }
    }

    public File register(File file) {
        if (file != null && file.exists()) {
            return registry.register(file);
        }
        return file;
    }

    public int cleanupOldTempFiles(long maxAgeMillis) {
        int deletedCount = 0;
        Set<Path> oldFiles = registry.getFilesOlderThan(maxAgeMillis);
        for (Path file : oldFiles) {
            if (deleteTempFile(file)) {
                deletedCount++;
            }
        }
        if (deletedCount > 0) {
            log.info("Cleaned up {} old temporary files", deletedCount);
        }
        return deletedCount;
    }

    public long getMaxAgeMillis() {
        long maxAgeHours =
                applicationProperties.getSystem().getTempFileManagement().getMaxAgeHours();
        return Duration.ofHours(maxAgeHours).toMillis();
    }

    public String generateTempFileName(String type, String extension) {
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        return prefix() + type + "-" + uuid + "." + extension;
    }

    public Path registerLibreOfficeTempDir() throws IOException {
        ApplicationProperties.TempFileManagement tempFiles =
                applicationProperties.getSystem().getTempFileManagement();

        String libreOfficeTempDir = tempFiles.getLibreofficeDir();
        if (StringUtils.hasText(libreOfficeTempDir)) {
            Path configured = Path.of(libreOfficeTempDir);
            Files.createDirectories(configured);
            return registry.registerDirectory(configured);
        }

        Path baseDir = resolveTenantAwareBase();
        Path loTempDir = baseDir.resolve("libreoffice");
        Files.createDirectories(loTempDir);
        return registry.registerDirectory(loTempDir);
    }

    private Path resolveTenantAwareBase() throws IOException {
        ApplicationProperties.TempFileManagement tempFiles =
                applicationProperties.getSystem().getTempFileManagement();
        Path baseDir = resolveBaseDirectory(tempFiles);
        String tenantSegment = currentTenantSegment();
        if (tenantSegment != null) {
            baseDir = baseDir.resolve("tenants").resolve(tenantSegment);
        }
        Files.createDirectories(baseDir);
        return baseDir;
    }

    private Path resolveBaseDirectory(ApplicationProperties.TempFileManagement tempFiles)
            throws IOException {
        String customTempDirectory = tempFiles.getBaseTmpDir();
        if (StringUtils.hasText(customTempDirectory)) {
            Path tempDir = Path.of(customTempDirectory);
            Files.createDirectories(tempDir);
            return tempDir;
        }
        Path systemTemp = Path.of(System.getProperty("java.io.tmpdir"), sanitizeSegment(prefix()));
        Files.createDirectories(systemTemp);
        return systemTemp;
    }

    private String currentTenantSegment() {
        if (tenantContextSupplier == null) {
            return null;
        }
        return tenantContextSupplier
                .currentTenant()
                .map(TenantDescriptor::slug)
                .filter(StringUtils::hasText)
                .map(this::sanitizeSegment)
                .orElse(null);
    }

    private String sanitizeSegment(String input) {
        String normalized = input.replaceAll("[^a-zA-Z0-9-_]", "-");
        if (normalized.length() > 80) {
            normalized = normalized.substring(0, 80);
        }
        return normalized.isBlank() ? "default" : normalized;
    }

    private String determineSuffix(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            return ".tmp";
        }
        int idx = originalFilename.lastIndexOf('.');
        if (idx == -1 || idx == originalFilename.length() - 1) {
            return ".tmp";
        }
        String suffix = originalFilename.substring(idx);
        return suffix.length() > 12 ? suffix.substring(0, 12) : suffix;
    }

    private String prefix() {
        String configuredPrefix =
                applicationProperties.getSystem().getTempFileManagement().getPrefix();
        if (!StringUtils.hasText(configuredPrefix)) {
            return DEFAULT_PREFIX;
        }
        return configuredPrefix;
    }
}
