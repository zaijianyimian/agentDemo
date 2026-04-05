package com.example.demo.service;

import com.example.demo.service.task.ScheduledTaskService;
import com.fasterxml.jackson.core.JsonGenerator;
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
import java.nio.file.StandardCopyOption;
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
import java.util.Objects;
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

    public void writeArchiveTo(OutputStream outputStream) {
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
            writeDatabasePayload(zipOutputStream);
            int fileCount = appendManagedDirectories(zipOutputStream);
            zipOutputStream.finish();
            log.info("数据导出完成: files={}", fileCount);
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

            Map<String, Object> summary = restoreDatabaseAndFiles(tablePayload, importArchive.tempFilesRoot, replaceExisting, tempRoot);
            scheduledTaskService.reloadScheduledTasks();
            evictAllCaches();
            summary.put("importedAt", LocalDateTime.now());
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

    private void writeDatabasePayload(ZipOutputStream zipOutputStream) throws IOException, SQLException {
        ZipEntry backupEntry = new ZipEntry(BACKUP_JSON);
        zipOutputStream.putNextEntry(backupEntry);

        JsonGenerator generator = objectMapper.getFactory().createGenerator(zipOutputStream);
        generator.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            String quote = safeQuote(metadata.getIdentifierQuoteString());
            String databaseProduct = metadata.getDatabaseProductName();
            List<String> tables = listCurrentDatabaseTables(connection, metadata);

            generator.writeStartObject();
            generator.writeStringField("version", "3.0");
            generator.writeStringField("exportedAt", LocalDateTime.now().toString());
            generator.writeStringField("databaseProduct", databaseProduct);
            generator.writeArrayFieldStart("managedDirectories");
            for (String dir : MANAGED_DIRS) {
                generator.writeString(dir);
            }
            generator.writeEndArray();
            generator.writeObjectFieldStart("tables");
            for (String table : tables) {
                generator.writeFieldName(table);
                writeTableRows(generator, connection, table, quote);
            }
            generator.writeEndObject();
            generator.writeEndObject();
            generator.flush();
        }

        zipOutputStream.closeEntry();
    }

    private void writeTableRows(JsonGenerator generator,
                                Connection connection,
                                String tableName,
                                String quote) throws SQLException, IOException {
        String sql = "SELECT * FROM " + quoteIdentifier(tableName, quote);
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            ResultSetMetaData rsMeta = resultSet.getMetaData();
            int columns = rsMeta.getColumnCount();
            generator.writeStartArray();
            while (resultSet.next()) {
                generator.writeStartObject();
                for (int i = 1; i <= columns; i++) {
                    String column = rsMeta.getColumnName(i);
                    generator.writeFieldName(column);
                    generator.writeObject(normalizeCellValue(resultSet.getObject(i)));
                }
                generator.writeEndObject();
            }
            generator.writeEndArray();
        }
    }

    private int appendManagedDirectories(ZipOutputStream zipOutputStream) throws IOException {
        int fileCount = 0;
        Path projectRoot = Paths.get(".").toAbsolutePath().normalize();
        for (String dirName : MANAGED_DIRS) {
            Path managedDir = projectRoot.resolve(dirName).normalize();
            if (!managedDir.startsWith(projectRoot) || !Files.exists(managedDir) || !Files.isDirectory(managedDir)) {
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
                if (!StringUtils.hasText(name) || entry.isDirectory()) {
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

    private Map<String, Object> restoreDatabaseAndFiles(Map<String, Object> tablePayload,
                                                        Path tempFilesRoot,
                                                        boolean replaceExisting,
                                                        Path tempRoot) throws Exception {
        Path projectRoot = Paths.get(".").toAbsolutePath().normalize();
        FileRestorePlan fileRestorePlan = new FileRestorePlan(projectRoot, tempFilesRoot, replaceExisting, tempRoot.resolve("file-rollback"));

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            String quote = safeQuote(connection.getMetaData().getIdentifierQuoteString());
            String dbProduct = connection.getMetaData().getDatabaseProductName();
            String dbProductNormalized = dbProduct == null ? "" : dbProduct.toLowerCase(Locale.ROOT);

            List<String> dbTables = listCurrentDatabaseTables(connection, connection.getMetaData());
            Map<String, TableMeta> tableMetas = loadTableMetadata(connection, dbTables);

            int importedTableCount = 0;
            int importedRows = 0;
            int skippedTables = 0;
            int skippedRows = 0;
            boolean fkDisabled = false;

            try {
                fkDisabled = disableForeignKeys(connection, dbProductNormalized);
                if (replaceExisting) {
                    clearTables(connection, dbTables, quote);
                }

                for (Map.Entry<String, Object> entry : tablePayload.entrySet()) {
                    String table = entry.getKey();
                    TableMeta tableMeta = tableMetas.get(table);
                    if (!isSafeIdentifier(table) || tableMeta == null) {
                        skippedTables++;
                        continue;
                    }
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> rows = objectMapper.convertValue(
                            entry.getValue(),
                            new TypeReference<List<Map<String, Object>>>() {}
                    );
                    InsertSummary summary = insertRows(connection, tableMeta, rows, quote, replaceExisting);
                    importedRows += summary.insertedRows();
                    skippedRows += summary.skippedRows();
                    importedTableCount++;
                }

                int restoredFiles = fileRestorePlan.apply();
                connection.commit();

                Map<String, Object> dbSummary = new LinkedHashMap<>();
                dbSummary.put("databaseProduct", dbProduct);
                dbSummary.put("importedTables", importedTableCount);
                dbSummary.put("importedRows", importedRows);
                dbSummary.put("skippedTables", skippedTables);
                dbSummary.put("skippedRows", skippedRows);
                dbSummary.put("replaceExisting", replaceExisting);

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("db", dbSummary);
                result.put("files", restoredFiles);
                result.put("replaceExisting", replaceExisting);
                return result;
            } catch (Exception ex) {
                connection.rollback();
                fileRestorePlan.rollback();
                throw ex;
            } finally {
                if (fkDisabled) {
                    enableForeignKeys(connection, dbProductNormalized);
                }
                connection.setAutoCommit(true);
                fileRestorePlan.cleanup();
            }
        }
    }

    private void clearTables(Connection connection, List<String> dbTables, String quote) throws SQLException {
        List<String> reverseTables = new ArrayList<>(dbTables);
        reverseTables.sort(Comparator.reverseOrder());
        for (String table : reverseTables) {
            String deleteSql = "DELETE FROM " + quoteIdentifier(table, quote);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(deleteSql);
            }
        }
    }

    private InsertSummary insertRows(Connection connection,
                                     TableMeta tableMeta,
                                     List<Map<String, Object>> rows,
                                     String quote,
                                     boolean replaceExisting) throws SQLException {
        if (rows == null || rows.isEmpty() || tableMeta.columns().isEmpty()) {
            return new InsertSummary(0, 0);
        }

        Set<String> payloadColumns = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            if (row != null) {
                payloadColumns.addAll(row.keySet());
            }
        }

        List<ColumnMeta> insertColumns = tableMeta.columns().stream()
                .filter(col -> payloadColumns.contains(col.name()))
                .toList();
        if (insertColumns.isEmpty()) {
            return new InsertSummary(0, rows.size());
        }

        String columnSql = joinColumns(insertColumns, quote);
        String valuesSql = "?,".repeat(insertColumns.size());
        valuesSql = valuesSql.substring(0, valuesSql.length() - 1);
        String insertSql = "INSERT INTO " + quoteIdentifier(tableMeta.name(), quote)
                + " (" + columnSql + ") VALUES (" + valuesSql + ")";

        int inserted = 0;
        int skipped = 0;
        try (PreparedStatement ps = connection.prepareStatement(insertSql)) {
            for (Map<String, Object> row : rows) {
                if (row == null) {
                    skipped++;
                    continue;
                }
                if (!replaceExisting && existsByPrimaryKey(connection, tableMeta, row, quote)) {
                    skipped++;
                    continue;
                }
                for (int i = 0; i < insertColumns.size(); i++) {
                    ColumnMeta col = insertColumns.get(i);
                    Object raw = row.get(col.name());
                    Object value = convertValueForSql(raw, col.sqlType());
                    if (value == null) {
                        ps.setNull(i + 1, col.sqlType());
                    } else {
                        ps.setObject(i + 1, value);
                    }
                }
                ps.addBatch();
            }
            int[] batchResult = ps.executeBatch();
            for (int item : batchResult) {
                inserted += item >= 0 ? item : 1;
            }
        }
        return new InsertSummary(inserted, skipped);
    }

    private boolean existsByPrimaryKey(Connection connection,
                                       TableMeta tableMeta,
                                       Map<String, Object> row,
                                       String quote) throws SQLException {
        List<String> primaryKeys = tableMeta.primaryKeys();
        if (primaryKeys.isEmpty()) {
            return false;
        }
        List<ColumnMeta> pkColumns = tableMeta.columns().stream()
                .filter(column -> primaryKeys.contains(column.name()))
                .toList();
        if (pkColumns.size() != primaryKeys.size()) {
            return false;
        }

        StringBuilder sql = new StringBuilder("SELECT 1 FROM ")
                .append(quoteIdentifier(tableMeta.name(), quote))
                .append(" WHERE ");
        for (int i = 0; i < pkColumns.size(); i++) {
            if (i > 0) {
                sql.append(" AND ");
            }
            sql.append(quoteIdentifier(pkColumns.get(i).name(), quote)).append(" = ?");
        }

        try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < pkColumns.size(); i++) {
                ColumnMeta column = pkColumns.get(i);
                Object value = convertValueForSql(row.get(column.name()), column.sqlType());
                if (value == null) {
                    return false;
                }
                ps.setObject(i + 1, value);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
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

    private Map<String, TableMeta> loadTableMetadata(Connection connection, List<String> tables) throws SQLException {
        Map<String, TableMeta> result = new LinkedHashMap<>();
        DatabaseMetaData metadata = connection.getMetaData();
        String catalog = connection.getCatalog();
        String schema = connection.getSchema();
        for (String table : tables) {
            List<ColumnMeta> columns = new ArrayList<>();
            try (ResultSet rs = metadata.getColumns(catalog, schema, table, "%")) {
                while (rs.next()) {
                    columns.add(new ColumnMeta(rs.getString("COLUMN_NAME"), rs.getInt("DATA_TYPE")));
                }
            }
            if (columns.isEmpty()) {
                try (ResultSet rs = metadata.getColumns(catalog, null, table, "%")) {
                    while (rs.next()) {
                        columns.add(new ColumnMeta(rs.getString("COLUMN_NAME"), rs.getInt("DATA_TYPE")));
                    }
                }
            }
            result.put(table, new TableMeta(table, columns, loadPrimaryKeys(metadata, catalog, schema, table)));
        }
        return result;
    }

    private List<String> loadPrimaryKeys(DatabaseMetaData metadata, String catalog, String schema, String table) throws SQLException {
        List<String> primaryKeys = new ArrayList<>();
        try (ResultSet rs = metadata.getPrimaryKeys(catalog, schema, table)) {
            while (rs.next()) {
                primaryKeys.add(rs.getString("COLUMN_NAME"));
            }
        }
        if (primaryKeys.isEmpty()) {
            try (ResultSet rs = metadata.getPrimaryKeys(catalog, null, table)) {
                while (rs.next()) {
                    primaryKeys.add(rs.getString("COLUMN_NAME"));
                }
            }
        }
        return primaryKeys.stream().filter(Objects::nonNull).distinct().toList();
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
        return tables.stream()
                .filter(this::isSafeIdentifier)
                .distinct()
                .sorted()
                .toList();
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
            log.warn("清理目录失败: {}", root, e);
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
            builder.append(quoteIdentifier(columnMetas.get(i).name(), quote));
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

    private record TableMeta(String name, List<ColumnMeta> columns, List<String> primaryKeys) {
    }

    private record InsertSummary(int insertedRows, int skippedRows) {
    }

    private record ImportArchive(String backupJson, Path tempFilesRoot) {
    }

    private static final class FileRestorePlan {
        private final Path projectRoot;
        private final Path sourceRoot;
        private final boolean replaceExisting;
        private final Path backupRoot;
        private final Map<String, Path> movedBackupDirs = new LinkedHashMap<>();
        private final List<Path> createdFiles = new ArrayList<>();
        private boolean applied;

        private FileRestorePlan(Path projectRoot, Path sourceRoot, boolean replaceExisting, Path backupRoot) {
            this.projectRoot = projectRoot;
            this.sourceRoot = sourceRoot;
            this.replaceExisting = replaceExisting;
            this.backupRoot = backupRoot;
        }

        int apply() throws IOException {
            int restoredCount = 0;
            if (replaceExisting) {
                backupManagedDirectories();
            }
            if (!Files.exists(sourceRoot) || !Files.isDirectory(sourceRoot)) {
                applied = true;
                return 0;
            }

            try (var stream = Files.walk(sourceRoot)) {
                for (Path source : stream.filter(Files::isRegularFile).toList()) {
                    Path relative = sourceRoot.relativize(source);
                    if (relative.getNameCount() < 1) {
                        continue;
                    }
                    String topDir = relative.getName(0).toString();
                    if (!MANAGED_DIRS.contains(topDir)) {
                        continue;
                    }

                    Path destination = projectRoot.resolve(relative).normalize();
                    Path allowedRoot = projectRoot.resolve(topDir).normalize();
                    if (!destination.startsWith(allowedRoot)) {
                        throw new IllegalStateException("检测到非法文件路径: " + destination);
                    }

                    Files.createDirectories(destination.getParent());
                    if (!replaceExisting && Files.exists(destination)) {
                        continue;
                    }

                    Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
                    if (!replaceExisting) {
                        createdFiles.add(destination);
                    }
                    restoredCount++;
                }
            }
            applied = true;
            return restoredCount;
        }

        void rollback() {
            if (!applied && movedBackupDirs.isEmpty()) {
                return;
            }
            try {
                if (replaceExisting) {
                    for (String dir : MANAGED_DIRS) {
                        deleteRecursivelyStatic(projectRoot.resolve(dir));
                    }
                    for (Map.Entry<String, Path> entry : movedBackupDirs.entrySet()) {
                        Path target = projectRoot.resolve(entry.getKey()).normalize();
                        if (Files.exists(entry.getValue())) {
                            Files.createDirectories(target.getParent());
                            Files.move(entry.getValue(), target, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                } else {
                    List<Path> rollbackFiles = new ArrayList<>(createdFiles);
                    rollbackFiles.sort(Comparator.reverseOrder());
                    for (Path path : rollbackFiles) {
                        Files.deleteIfExists(path);
                        cleanupEmptyParents(path.getParent(), projectRoot);
                    }
                }
            } catch (Exception e) {
                log.warn("回滚导入文件失败", e);
            }
        }

        void cleanup() {
            deleteRecursivelyStatic(backupRoot);
        }

        private void backupManagedDirectories() throws IOException {
            Files.createDirectories(backupRoot);
            for (String dir : MANAGED_DIRS) {
                Path sourceDir = projectRoot.resolve(dir).normalize();
                if (!Files.exists(sourceDir)) {
                    continue;
                }
                Path backupDir = backupRoot.resolve(dir).normalize();
                Files.createDirectories(backupDir.getParent());
                Files.move(sourceDir, backupDir, StandardCopyOption.REPLACE_EXISTING);
                movedBackupDirs.put(dir, backupDir);
            }
        }

        private static void cleanupEmptyParents(Path candidate, Path stopAt) throws IOException {
            Path current = candidate;
            while (current != null && current.startsWith(stopAt) && !current.equals(stopAt)) {
                try (var stream = Files.list(current)) {
                    if (stream.findAny().isPresent()) {
                        return;
                    }
                }
                Files.deleteIfExists(current);
                current = current.getParent();
            }
        }

        private static void deleteRecursivelyStatic(Path root) {
            if (root == null || !Files.exists(root)) {
                return;
            }
            try (var stream = Files.walk(root)) {
                for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            } catch (IOException e) {
                log.warn("清理回滚目录失败: {}", root, e);
            }
        }
    }
}
