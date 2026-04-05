package com.example.demo.service;

import com.example.demo.service.task.ScheduledTaskService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
public class DataArchiveService {

    private static final List<String> MANAGED_DIRS = List.of("data", "generated");
    private static final String BACKUP_JSON = "backup.json";
    private static final String FILES_PREFIX = "files/";
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final String BASE64_MARKER = "__base64__";

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    private final ScheduledTaskService scheduledTaskService;
    private final CacheManager cacheManager;

    public DataArchiveService(DataSource dataSource,
                              ObjectMapper objectMapper,
                              ScheduledTaskService scheduledTaskService,
                              CacheManager cacheManager) {
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
        this.scheduledTaskService = scheduledTaskService;
        this.cacheManager = cacheManager;
    }

    public byte[] exportAllDataAsZip() {
        try {
            Map<String, Object> payload = exportDatabasePayload();
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            int fileCount;
            try (ZipOutputStream zipOutputStream = new ZipOutputStream(byteArrayOutputStream, StandardCharsets.UTF_8)) {
                byte[] json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(payload);
                ZipEntry backupEntry = new ZipEntry(BACKUP_JSON);
                zipOutputStream.putNextEntry(backupEntry);
                zipOutputStream.write(json);
                zipOutputStream.closeEntry();

                fileCount = appendManagedDirectories(zipOutputStream);
            }
            log.info("数据导出完成: tables={}, files={}",
                    ((Map<?, ?>) payload.getOrDefault("tables", Map.of())).size(),
                    fileCount);
            return byteArrayOutputStream.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("导出数据失败: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> importAllDataFromZip(MultipartFile archiveFile, boolean replaceExisting) {
        if (archiveFile == null || archiveFile.isEmpty()) {
            throw new IllegalArgumentException("请选择有效的 ZIP 文件");
        }

        Path tempRoot = null;
        try {
            tempRoot = Files.createTempDirectory("agent-data-archive-");
            ImportArchive importArchive = unzipToTemp(archiveFile, tempRoot);

            if (!StringUtils.hasText(importArchive.backupJson)) {
                throw new IllegalArgumentException("ZIP 中缺少 backup.json");
            }

            Map<String, Object> backupPayload = objectMapper.readValue(
                    importArchive.backupJson,
                    new TypeReference<>() {}
            );
            @SuppressWarnings("unchecked")
            Map<String, Object> tablePayload = (Map<String, Object>) backupPayload.getOrDefault("tables", Map.of());

            Map<String, Object> dbSummary = restoreDatabase(tablePayload, replaceExisting);
            int restoredFiles = restoreManagedFiles(importArchive.tempFilesRoot, replaceExisting);

            scheduledTaskService.reloadScheduledTasks();
            evictAllCaches();

            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("importedAt", LocalDateTime.now());
            summary.put("replaceExisting", replaceExisting);
            summary.put("db", dbSummary);
            summary.put("files", restoredFiles);
            return summary;
        } catch (Exception e) {
            throw new IllegalStateException("导入数据失败: " + e.getMessage(), e);
        } finally {
            if (tempRoot != null) {
                deleteRecursively(tempRoot);
            }
        }
    }

    public String buildArchiveFileName() {
        return "agent-data-backup-" + LocalDateTime.now().format(FILE_TIME) + ".zip";
    }

    private Map<String, Object> exportDatabasePayload() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            String quote = safeQuote(metadata.getIdentifierQuoteString());
            String databaseProduct = metadata.getDatabaseProductName();

            List<String> tables = listCurrentDatabaseTables(connection, metadata);
            Map<String, Object> tableRows = new LinkedHashMap<>();
            for (String table : tables) {
                List<Map<String, Object>> rows = queryTableRows(connection, table, quote);
                tableRows.put(table, rows);
            }

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("version", "2.0");
            payload.put("exportedAt", LocalDateTime.now());
            payload.put("databaseProduct", databaseProduct);
            payload.put("tables", tableRows);
            payload.put("managedDirectories", MANAGED_DIRS);
            return payload;
        }
    }

    private List<Map<String, Object>> queryTableRows(Connection connection, String tableName, String quote) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        String sql = "SELECT * FROM " + quoteIdentifier(tableName, quote);
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            ResultSetMetaData rsMeta = resultSet.getMetaData();
            int columns = rsMeta.getColumnCount();
            while (resultSet.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= columns; i++) {
                    String column = rsMeta.getColumnName(i);
                    Object value = resultSet.getObject(i);
                    row.put(column, normalizeCellValue(value));
                }
                rows.add(row);
            }
        }
        return rows;
    }

    private int appendManagedDirectories(ZipOutputStream zipOutputStream) throws IOException {
        int fileCount = 0;
        Path projectRoot = Paths.get(".").toAbsolutePath().normalize();
        for (String dirName : MANAGED_DIRS) {
            Path managedDir = projectRoot.resolve(dirName).normalize();
            if (!managedDir.startsWith(projectRoot) || !Files.exists(managedDir)) {
                continue;
            }
            if (!Files.isDirectory(managedDir)) {
                continue;
            }
            try (var stream = Files.walk(managedDir)) {
                for (Path file : stream.filter(Files::isRegularFile).toList()) {
                    Path relative = projectRoot.relativize(file);
                    String entryName = FILES_PREFIX + relative.toString().replace('\\', '/');
                    ZipEntry entry = new ZipEntry(entryName);
                    zipOutputStream.putNextEntry(entry);
                    Files.copy(file, zipOutputStream);
                    zipOutputStream.closeEntry();
                    fileCount++;
                }
            }
        }
        return fileCount;
    }

    private ImportArchive unzipToTemp(MultipartFile archiveFile, Path tempRoot) throws IOException {
        String backupJson = null;
        Path tempFilesRoot = tempRoot.resolve("files").normalize();
        int extractedFiles = 0;
        try (ZipInputStream zipInputStream = new ZipInputStream(archiveFile.getInputStream(), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                String name = normalizeZipEntryName(entry.getName());
                if (!StringUtils.hasText(name)) {
                    zipInputStream.closeEntry();
                    continue;
                }

                if (entry.isDirectory()) {
                    zipInputStream.closeEntry();
                    continue;
                }

                if (BACKUP_JSON.equals(name)) {
                    backupJson = new String(readAllBytes(zipInputStream), StandardCharsets.UTF_8);
                    zipInputStream.closeEntry();
                    continue;
                }

                if (!name.startsWith(FILES_PREFIX) || !isManagedFileEntry(name)) {
                    zipInputStream.closeEntry();
                    continue;
                }

                Path target = safeResolveInDirectory(tempRoot, name);
                Files.createDirectories(target.getParent());
                try (OutputStream outputStream = Files.newOutputStream(target)) {
                    zipInputStream.transferTo(outputStream);
                }
                extractedFiles++;
                zipInputStream.closeEntry();
            }
        }
        log.info("读取导入压缩包完成: extractedFiles={}", extractedFiles);
        return new ImportArchive(backupJson, tempFilesRoot);
    }

    private int restoreManagedFiles(Path tempFilesRoot, boolean replaceExisting) throws IOException {
        Path projectRoot = Paths.get(".").toAbsolutePath().normalize();
        if (replaceExisting) {
            for (String managedDir : MANAGED_DIRS) {
                Path targetDir = projectRoot.resolve(managedDir).normalize();
                if (!targetDir.startsWith(projectRoot)) {
                    throw new IllegalStateException("非法目录: " + targetDir);
                }
                deleteRecursively(targetDir);
            }
        }

        if (!Files.exists(tempFilesRoot) || !Files.isDirectory(tempFilesRoot)) {
            return 0;
        }

        int restoredCount = 0;
        try (var stream = Files.walk(tempFilesRoot)) {
            for (Path source : stream.filter(Files::isRegularFile).toList()) {
                Path relative = tempFilesRoot.relativize(source);
                if (relative.getNameCount() < 1) {
                    continue;
                }
                String topDir = relative.getName(0).toString();
                if (!MANAGED_DIRS.contains(topDir)) {
                    continue;
                }
                Path destination = projectRoot.resolve(relative).normalize();
                if (!destination.startsWith(projectRoot.resolve(topDir).normalize())) {
                    throw new IllegalStateException("检测到非法文件路径: " + destination);
                }
                Files.createDirectories(destination.getParent());
                Files.copy(source, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                restoredCount++;
            }
        }
        return restoredCount;
    }

    private Map<String, Object> restoreDatabase(Map<String, Object> tablePayload, boolean replaceExisting) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            String quote = safeQuote(connection.getMetaData().getIdentifierQuoteString());
            String dbProduct = connection.getMetaData().getDatabaseProductName();
            String dbProductNormalized = dbProduct == null ? "" : dbProduct.toLowerCase(Locale.ROOT);

            List<String> dbTables = listCurrentDatabaseTables(connection, connection.getMetaData());
            Map<String, List<ColumnMeta>> columnsByTable = loadColumnMetadata(connection, dbTables);
            Map<String, Object> result = new LinkedHashMap<>();

            int importedTableCount = 0;
            int importedRows = 0;
            int skippedTables = 0;
            boolean fkDisabled = false;
            try {
                fkDisabled = disableForeignKeys(connection, dbProductNormalized);
                if (replaceExisting) {
                    List<String> reverseTables = new ArrayList<>(dbTables);
                    reverseTables.sort(Comparator.reverseOrder());
                    for (String table : reverseTables) {
                        String deleteSql = "DELETE FROM " + quoteIdentifier(table, quote);
                        try (Statement statement = connection.createStatement()) {
                            statement.executeUpdate(deleteSql);
                        }
                    }
                }

                for (Map.Entry<String, Object> entry : tablePayload.entrySet()) {
                    String table = entry.getKey();
                    if (!isSafeIdentifier(table) || !columnsByTable.containsKey(table)) {
                        skippedTables++;
                        continue;
                    }
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> rows = objectMapper.convertValue(
                            entry.getValue(),
                            new TypeReference<List<Map<String, Object>>>() {}
                    );
                    List<ColumnMeta> columnMetas = columnsByTable.getOrDefault(table, List.of());
                    int inserted = insertRows(connection, table, rows, columnMetas, quote);
                    importedRows += inserted;
                    importedTableCount++;
                }
                connection.commit();
            } catch (Exception ex) {
                connection.rollback();
                throw ex;
            } finally {
                if (fkDisabled) {
                    enableForeignKeys(connection, dbProductNormalized);
                }
                connection.setAutoCommit(true);
            }

            result.put("databaseProduct", dbProduct);
            result.put("importedTables", importedTableCount);
            result.put("importedRows", importedRows);
            result.put("skippedTables", skippedTables);
            result.put("replaceExisting", replaceExisting);
            return result;
        }
    }

    private int insertRows(Connection connection,
                           String table,
                           List<Map<String, Object>> rows,
                           List<ColumnMeta> columnMetas,
                           String quote) throws SQLException {
        if (rows == null || rows.isEmpty() || columnMetas == null || columnMetas.isEmpty()) {
            return 0;
        }

        Set<String> payloadColumns = new LinkedHashSet<>(rows.get(0).keySet());
        List<ColumnMeta> insertColumns = columnMetas.stream()
                .filter(col -> payloadColumns.contains(col.name))
                .toList();
        if (insertColumns.isEmpty()) {
            return 0;
        }

        String columnSql = joinColumns(insertColumns, quote);
        String valuesSql = "?,".repeat(insertColumns.size());
        valuesSql = valuesSql.substring(0, valuesSql.length() - 1);
        String insertSql = "INSERT INTO " + quoteIdentifier(table, quote) + " (" + columnSql + ") VALUES (" + valuesSql + ")";

        int inserted = 0;
        try (PreparedStatement ps = connection.prepareStatement(insertSql)) {
            for (Map<String, Object> row : rows) {
                for (int i = 0; i < insertColumns.size(); i++) {
                    ColumnMeta col = insertColumns.get(i);
                    Object raw = row.get(col.name);
                    Object value = convertValueForSql(raw, col.sqlType);
                    if (value == null) {
                        ps.setNull(i + 1, col.sqlType);
                    } else {
                        ps.setObject(i + 1, value);
                    }
                }
                ps.addBatch();
            }
            int[] batchResult = ps.executeBatch();
            for (int item : batchResult) {
                if (item >= 0) {
                    inserted += item;
                } else {
                    inserted += 1;
                }
            }
        }
        return inserted;
    }

    private Object convertValueForSql(Object rawValue, int sqlType) {
        Object value = decodeBinaryValue(rawValue);
        if (value == null) {
            return null;
        }

        try {
            switch (sqlType) {
                case Types.BIGINT -> {
                    if (value instanceof Number number) {
                        return number.longValue();
                    }
                    return Long.parseLong(String.valueOf(value));
                }
                case Types.INTEGER, Types.SMALLINT, Types.TINYINT -> {
                    if (value instanceof Number number) {
                        return number.intValue();
                    }
                    return Integer.parseInt(String.valueOf(value));
                }
                case Types.DOUBLE, Types.REAL, Types.FLOAT -> {
                    if (value instanceof Number number) {
                        return number.doubleValue();
                    }
                    return Double.parseDouble(String.valueOf(value));
                }
                case Types.DECIMAL, Types.NUMERIC -> {
                    if (value instanceof Number) {
                        return new java.math.BigDecimal(String.valueOf(value));
                    }
                    return new java.math.BigDecimal(String.valueOf(value));
                }
                case Types.BOOLEAN, Types.BIT -> {
                    if (value instanceof Boolean b) {
                        return b;
                    }
                    String text = String.valueOf(value).trim();
                    if ("1".equals(text)) {
                        return true;
                    }
                    if ("0".equals(text)) {
                        return false;
                    }
                    return Boolean.parseBoolean(text);
                }
                case Types.DATE -> {
                    if (value instanceof java.sql.Date) {
                        return value;
                    }
                    String text = String.valueOf(value).trim();
                    if (text.length() >= 10) {
                        return java.sql.Date.valueOf(text.substring(0, 10));
                    }
                }
                case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> {
                    if (value instanceof java.sql.Timestamp) {
                        return value;
                    }
                    String text = String.valueOf(value).trim();
                    return java.sql.Timestamp.valueOf(text.replace('T', ' '));
                }
                case Types.TIME, Types.TIME_WITH_TIMEZONE -> {
                    if (value instanceof java.sql.Time) {
                        return value;
                    }
                    return java.sql.Time.valueOf(String.valueOf(value).trim());
                }
                case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> {
                    if (value instanceof byte[]) {
                        return value;
                    }
                    return Base64.getDecoder().decode(String.valueOf(value));
                }
                default -> {
                    return value;
                }
            }
        } catch (Exception ignored) {
            // 类型转换失败则降级为原始值，交由 JDBC 驱动处理
        }
        return value;
    }

    private Object decodeBinaryValue(Object rawValue) {
        if (!(rawValue instanceof Map<?, ?> map)) {
            return rawValue;
        }
        Object marker = map.get("type");
        Object value = map.get("value");
        if (!BASE64_MARKER.equals(marker) || value == null) {
            return rawValue;
        }
        return Base64.getDecoder().decode(String.valueOf(value));
    }

    private Map<String, List<ColumnMeta>> loadColumnMetadata(Connection connection, List<String> tables) throws SQLException {
        Map<String, List<ColumnMeta>> result = new LinkedHashMap<>();
        DatabaseMetaData metadata = connection.getMetaData();
        String catalog = connection.getCatalog();
        String schema = connection.getSchema();
        for (String table : tables) {
            List<ColumnMeta> columns = new ArrayList<>();
            try (ResultSet rs = metadata.getColumns(catalog, schema, table, "%")) {
                while (rs.next()) {
                    String columnName = rs.getString("COLUMN_NAME");
                    int sqlType = rs.getInt("DATA_TYPE");
                    columns.add(new ColumnMeta(columnName, sqlType));
                }
            }
            if (columns.isEmpty()) {
                try (ResultSet rs = metadata.getColumns(catalog, null, table, "%")) {
                    while (rs.next()) {
                        String columnName = rs.getString("COLUMN_NAME");
                        int sqlType = rs.getInt("DATA_TYPE");
                        columns.add(new ColumnMeta(columnName, sqlType));
                    }
                }
            }
            result.put(table, columns);
        }
        return result;
    }

    private List<String> listCurrentDatabaseTables(Connection connection, DatabaseMetaData metadata) throws SQLException {
        String catalog = connection.getCatalog();
        String schema = connection.getSchema();
        List<String> tables = new ArrayList<>();
        collectTables(metadata, catalog, schema, tables);
        if (tables.isEmpty()) {
            collectTables(metadata, catalog, null, tables);
        }
        if (tables.isEmpty()) {
            collectTables(metadata, null, schema, tables);
        }
        if (tables.isEmpty()) {
            collectTables(metadata, null, null, tables);
        }
        tables = tables.stream()
                .filter(this::isSafeIdentifier)
                .distinct()
                .sorted()
                .toList();
        return tables;
    }

    private void collectTables(DatabaseMetaData metadata, String catalog, String schema, List<String> receiver) throws SQLException {
        try (ResultSet rs = metadata.getTables(catalog, schema, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                String table = rs.getString("TABLE_NAME");
                if (StringUtils.hasText(table)) {
                    receiver.add(table);
                }
            }
        }
    }

    private boolean disableForeignKeys(Connection connection, String productName) {
        String sql = null;
        if (productName.contains("mysql") || productName.contains("mariadb")) {
            sql = "SET FOREIGN_KEY_CHECKS=0";
        } else if (productName.contains("h2")) {
            sql = "SET REFERENTIAL_INTEGRITY FALSE";
        }
        if (sql == null) {
            return false;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
            return true;
        } catch (Exception e) {
            log.warn("关闭外键约束失败，继续执行: {}", e.getMessage());
            return false;
        }
    }

    private void enableForeignKeys(Connection connection, String productName) {
        String sql = null;
        if (productName.contains("mysql") || productName.contains("mariadb")) {
            sql = "SET FOREIGN_KEY_CHECKS=1";
        } else if (productName.contains("h2")) {
            sql = "SET REFERENTIAL_INTEGRITY TRUE";
        }
        if (sql == null) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (Exception e) {
            log.warn("恢复外键约束失败: {}", e.getMessage());
        }
    }

    private Object normalizeCellValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof byte[] bytes) {
            Map<String, Object> encoded = new LinkedHashMap<>();
            encoded.put("type", BASE64_MARKER);
            encoded.put("value", Base64.getEncoder().encodeToString(bytes));
            return encoded;
        }
        if (value instanceof java.sql.Timestamp
                || value instanceof java.sql.Date
                || value instanceof java.sql.Time) {
            return String.valueOf(value);
        }
        return value;
    }

    private void evictAllCaches() {
        if (cacheManager == null) {
            return;
        }
        Collection<String> cacheNames = cacheManager.getCacheNames();
        for (String cacheName : cacheNames) {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
            }
        }
    }

    private String normalizeZipEntryName(String entryName) {
        if (!StringUtils.hasText(entryName)) {
            return "";
        }
        String normalized = entryName.replace('\\', '/').trim();
        if (normalized.startsWith("/") || normalized.startsWith("\\") || normalized.contains("..") || normalized.contains(":")) {
            throw new IllegalArgumentException("ZIP 中存在非法路径: " + entryName);
        }
        return normalized;
    }

    private boolean isManagedFileEntry(String entryName) {
        for (String dir : MANAGED_DIRS) {
            if (entryName.startsWith(FILES_PREFIX + dir + "/")) {
                return true;
            }
        }
        return false;
    }

    private Path safeResolveInDirectory(Path root, String relativePath) {
        Path target = root.resolve(relativePath).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("检测到非法路径: " + relativePath);
        }
        return target;
    }

    private void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var stream = Files.walk(root)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            log.warn("清理临时目录失败: {}", root, e);
        }
    }

    private byte[] readAllBytes(ZipInputStream zipInputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        zipInputStream.transferTo(buffer);
        return buffer.toByteArray();
    }

    private String joinColumns(List<ColumnMeta> columnMetas, String quote) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < columnMetas.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(quoteIdentifier(columnMetas.get(i).name, quote));
        }
        return builder.toString();
    }

    private String quoteIdentifier(String identifier, String quote) {
        if (!isSafeIdentifier(identifier)) {
            throw new IllegalArgumentException("非法标识符: " + identifier);
        }
        return quote + identifier + quote;
    }

    private boolean isSafeIdentifier(String value) {
        return StringUtils.hasText(value) && value.matches("[A-Za-z0-9_]+");
    }

    private String safeQuote(String quote) {
        if (!StringUtils.hasText(quote)) {
            return "";
        }
        String trimmed = quote.trim();
        return " ".equals(trimmed) ? "" : trimmed;
    }

    private record ColumnMeta(String name, int sqlType) {
    }

    private record ImportArchive(String backupJson, Path tempFilesRoot) {
    }
}
