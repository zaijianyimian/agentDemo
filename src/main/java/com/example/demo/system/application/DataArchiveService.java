package com.example.demo.system.application;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Java 业务数据归档服务。
 *
 * <p>归档内容仅包含 Java 持有的关系型数据库数据以及业务文件目录。Agent Memory、Embedding、
 * Qdrant 等数据由 Python Agent Engine 独立管理，不再进入 Java 备份。</p>
 */
@Slf4j
@Service
public class DataArchiveService {

    private static final String BACKUP_JSON = "backup.json";
    private static final String FILES_PREFIX = "files/";
    private static final List<String> MANAGED_DIRS = List.of("data", "generated");
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    public DataArchiveService(DataSource dataSource, ObjectMapper objectMapper) {
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
    }

    /**
     * 将 Java 业务数据库和托管文件目录写入 ZIP。
     *
     * @param outputStream ZIP 输出流。
     */
    public void writeArchiveTo(OutputStream outputStream) {
        try (ZipOutputStream zipOutputStream =
                     new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
            writeDatabasePayload(zipOutputStream);
            int fileCount = appendManagedDirectories(zipOutputStream);
            zipOutputStream.finish();
            log.info("Java 业务数据导出完成: files={}", fileCount);
        } catch (IOException | SQLException error) {
            throw new IllegalStateException("导出 Java 业务数据失败: " + error.getMessage(), error);
        }
    }

    /**
     * 生成业务数据备份文件名。
     *
     * @return 带时间戳的 ZIP 文件名。
     */
    public String buildArchiveFileName() {
        return "agent-data-backup-" + LocalDateTime.now().format(FILE_TIME) + ".zip";
    }

    /**
     * 从 ZIP 恢复 Java 业务数据库与托管文件。
     *
     * <p>该导入器只识别 {@code backup.json} 与 {@code files/data/**}、
     * {@code files/generated/**}。旧备份中的 vectors.json 等 Agent 数据会被忽略。</p>
     *
     * @param archiveFile ZIP 备份文件。
     * @param replaceExisting 是否清空已有业务表后恢复。
     * @return 导入统计。
     */
    public Map<String, Object> importAllDataFromZip(
            MultipartFile archiveFile,
            boolean replaceExisting) {
        if (archiveFile == null || archiveFile.isEmpty()) {
            throw new IllegalArgumentException("请选择有效的 ZIP 文件");
        }

        Path tempRoot = null;
        try {
            tempRoot = Files.createTempDirectory("java-business-archive-");
            Path filesRoot = tempRoot.resolve("files");
            String backupJson = unzipBusinessArchive(archiveFile, tempRoot, filesRoot);
            if (backupJson == null || backupJson.isBlank()) {
                throw new IllegalArgumentException("ZIP 中缺少 backup.json");
            }

            Map<String, Object> payload = objectMapper.readValue(
                    backupJson,
                    new TypeReference<Map<String, Object>>() {});
            Map<String, Object> tables = objectMapper.convertValue(
                    payload.getOrDefault("tables", Map.of()),
                    new TypeReference<Map<String, Object>>() {});

            Map<String, Object> summary = restoreDatabase(tables, replaceExisting);
            summary.put("files", restoreManagedFiles(filesRoot, replaceExisting));
            summary.put("importedAt", LocalDateTime.now());
            summary.put("agentData", "ignored");
            return summary;
        } catch (Exception error) {
            throw new IllegalStateException("导入 Java 业务数据失败: " + error.getMessage(), error);
        } finally {
            if (tempRoot != null) {
                deleteRecursively(tempRoot);
            }
        }
    }

    private void writeDatabasePayload(ZipOutputStream zipOutputStream)
            throws IOException, SQLException {
        zipOutputStream.putNextEntry(new ZipEntry(BACKUP_JSON));
        JsonGenerator generator = objectMapper.getFactory().createGenerator(zipOutputStream);
        generator.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            String quote = safeQuote(metadata.getIdentifierQuoteString());
            generator.writeStartObject();
            generator.writeStringField("version", "5.0-java-business-only");
            generator.writeStringField("exportedAt", LocalDateTime.now().toString());
            generator.writeStringField("databaseProduct", metadata.getDatabaseProductName());
            generator.writeArrayFieldStart("managedDirectories");
            for (String directory : MANAGED_DIRS) {
                generator.writeString(directory);
            }
            generator.writeEndArray();
            generator.writeObjectFieldStart("tables");
            for (String table : listCurrentDatabaseTables(connection, metadata)) {
                generator.writeFieldName(table);
                writeTableRows(generator, connection, table, quote);
            }
            generator.writeEndObject();
            generator.writeEndObject();
            generator.flush();
        }
        zipOutputStream.closeEntry();
    }

    private List<String> listCurrentDatabaseTables(
            Connection connection,
            DatabaseMetaData metadata) throws SQLException {
        List<String> tables = new ArrayList<>();
        String catalog = connection.getCatalog();
        try (ResultSet resultSet = metadata.getTables(catalog, null, "%", new String[]{"TABLE"})) {
            while (resultSet.next()) {
                String table = resultSet.getString("TABLE_NAME");
                if (table != null && isSafeIdentifier(table)) {
                    tables.add(table);
                }
            }
        }
        tables.sort(String.CASE_INSENSITIVE_ORDER);
        return tables;
    }

    private void writeTableRows(
            JsonGenerator generator,
            Connection connection,
            String table,
            String quote) throws SQLException, IOException {
        String sql = "SELECT * FROM " + quoteIdentifier(table, quote);
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            ResultSetMetaData metadata = resultSet.getMetaData();
            generator.writeStartArray();
            while (resultSet.next()) {
                generator.writeStartObject();
                for (int index = 1; index <= metadata.getColumnCount(); index++) {
                    generator.writeFieldName(metadata.getColumnName(index));
                    generator.writeObject(normalizeCellValue(resultSet.getObject(index)));
                }
                generator.writeEndObject();
            }
            generator.writeEndArray();
        }
    }

    private int appendManagedDirectories(ZipOutputStream zipOutputStream) throws IOException {
        int count = 0;
        Path projectRoot = Paths.get(".").toAbsolutePath().normalize();
        for (String directory : MANAGED_DIRS) {
            Path root = projectRoot.resolve(directory).normalize();
            if (!root.startsWith(projectRoot) || !Files.isDirectory(root)) {
                continue;
            }
            try (var stream = Files.walk(root)) {
                for (Path file : stream.filter(Files::isRegularFile).toList()) {
                    String relative = projectRoot.relativize(file).toString().replace('\\', '/');
                    zipOutputStream.putNextEntry(new ZipEntry(FILES_PREFIX + relative));
                    Files.copy(file, zipOutputStream);
                    zipOutputStream.closeEntry();
                    count++;
                }
            }
        }
        return count;
    }

    private String unzipBusinessArchive(
            MultipartFile archiveFile,
            Path tempRoot,
            Path filesRoot) throws IOException {
        String backupJson = null;
        try (ZipInputStream zipInputStream =
                     new ZipInputStream(archiveFile.getInputStream(), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName().replace('\\', '/');
                if (BACKUP_JSON.equals(name)) {
                    backupJson = new String(zipInputStream.readAllBytes(), StandardCharsets.UTF_8);
                    continue;
                }
                if (!isManagedFileEntry(name)) {
                    continue;
                }
                Path target = tempRoot.resolve(name).normalize();
                if (!target.startsWith(filesRoot)) {
                    throw new IllegalArgumentException("ZIP 条目越界: " + name);
                }
                Files.createDirectories(target.getParent());
                Files.copy(zipInputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return backupJson;
    }

    private Map<String, Object> restoreDatabase(
            Map<String, Object> tablePayload,
            boolean replaceExisting) throws SQLException {
        Map<String, Object> result = new LinkedHashMap<>();
        int importedTables = 0;
        int importedRows = 0;
        int skippedTables = 0;

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            DatabaseMetaData metadata = connection.getMetaData();
            String quote = safeQuote(metadata.getIdentifierQuoteString());
            List<String> currentTables = listCurrentDatabaseTables(connection, metadata);
            boolean mysql = metadata.getDatabaseProductName() != null
                    && metadata.getDatabaseProductName().toLowerCase(Locale.ROOT).contains("mysql");
            try {
                if (mysql) {
                    executeStatement(connection, "SET FOREIGN_KEY_CHECKS=0");
                }
                for (Map.Entry<String, Object> entry : tablePayload.entrySet()) {
                    String table = entry.getKey();
                    if (!isSafeIdentifier(table) || !currentTables.contains(table)) {
                        skippedTables++;
                        continue;
                    }
                    List<Map<String, Object>> rows = objectMapper.convertValue(
                            entry.getValue(),
                            new TypeReference<List<Map<String, Object>>>() {});
                    if (replaceExisting) {
                        executeStatement(connection, "DELETE FROM " + quoteIdentifier(table, quote));
                    }
                    importedRows += insertRows(connection, table, rows, quote, replaceExisting);
                    importedTables++;
                }
                connection.commit();
            } catch (RuntimeException | SQLException error) {
                connection.rollback();
                throw error;
            } finally {
                if (mysql) {
                    executeStatement(connection, "SET FOREIGN_KEY_CHECKS=1");
                }
                connection.setAutoCommit(true);
            }
        }
        result.put("importedTables", importedTables);
        result.put("importedRows", importedRows);
        result.put("skippedTables", skippedTables);
        result.put("replaceExisting", replaceExisting);
        return result;
    }

    private int insertRows(
            Connection connection,
            String table,
            List<Map<String, Object>> rows,
            String quote,
            boolean replaceExisting) throws SQLException {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        int inserted = 0;
        for (Map<String, Object> row : rows) {
            if (row == null || row.isEmpty()) {
                continue;
            }
            List<String> columns = row.keySet().stream()
                    .filter(this::isSafeIdentifier)
                    .toList();
            if (columns.isEmpty()) {
                continue;
            }
            String columnSql = columns.stream()
                    .map(column -> quoteIdentifier(column, quote))
                    .reduce((left, right) -> left + "," + right)
                    .orElseThrow();
            String placeholders = String.join(",", java.util.Collections.nCopies(columns.size(), "?"));
            String prefix = replaceExisting ? "INSERT INTO " : "INSERT IGNORE INTO ";
            String sql = prefix + quoteIdentifier(table, quote)
                    + " (" + columnSql + ") VALUES (" + placeholders + ")";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int index = 0; index < columns.size(); index++) {
                    statement.setObject(index + 1, row.get(columns.get(index)));
                }
                inserted += statement.executeUpdate();
            }
        }
        return inserted;
    }

    private int restoreManagedFiles(Path filesRoot, boolean replaceExisting) throws IOException {
        if (!Files.isDirectory(filesRoot)) {
            return 0;
        }
        Path projectRoot = Paths.get(".").toAbsolutePath().normalize();
        int restored = 0;
        try (var stream = Files.walk(filesRoot)) {
            for (Path source : stream.filter(Files::isRegularFile).toList()) {
                Path relative = filesRoot.relativize(source);
                if (relative.getNameCount() == 0
                        || !MANAGED_DIRS.contains(relative.getName(0).toString())) {
                    continue;
                }
                Path target = projectRoot.resolve(relative).normalize();
                if (!target.startsWith(projectRoot)) {
                    throw new IllegalArgumentException("恢复文件路径越界: " + relative);
                }
                if (!replaceExisting && Files.exists(target)) {
                    continue;
                }
                Files.createDirectories(target.getParent());
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                restored++;
            }
        }
        return restored;
    }

    private boolean isManagedFileEntry(String name) {
        if (!name.startsWith(FILES_PREFIX)) {
            return false;
        }
        String relative = name.substring(FILES_PREFIX.length());
        return MANAGED_DIRS.stream().anyMatch(directory ->
                relative.equals(directory) || relative.startsWith(directory + "/"));
    }

    private void executeStatement(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private void deleteRecursively(Path root) {
        try (var stream = Files.walk(root)) {
            stream.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException error) {
                    log.debug("清理临时文件失败: {}", path, error);
                }
            });
        } catch (IOException error) {
            log.debug("清理临时目录失败: {}", root, error);
        }
    }

    private Object normalizeCellValue(Object value) {
        if (value == null
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof String) {
            return value;
        }
        if (value instanceof byte[] bytes) {
            return java.util.Base64.getEncoder().encodeToString(bytes);
        }
        return String.valueOf(value);
    }

    private String quoteIdentifier(String identifier, String quote) {
        if (!isSafeIdentifier(identifier)) {
            throw new IllegalArgumentException("非法数据库标识符: " + identifier);
        }
        return quote + identifier + quote;
    }

    private boolean isSafeIdentifier(String identifier) {
        return identifier != null && identifier.matches("[A-Za-z0-9_]+")
                && !identifier.toLowerCase(Locale.ROOT).startsWith("information_schema");
    }

    private String safeQuote(String quote) {
        return quote == null || quote.isBlank() ? "`" : quote.trim();
    }
}
