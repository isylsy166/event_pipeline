package config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.yaml.snakeyaml.Yaml;

import javax.sql.DataSource;
import java.io.InputStream;
import java.util.Map;

public class DatabaseConfig {

    private static HikariDataSource dataSource;

    public static DataSource getDataSource() {
        if (dataSource == null) {
            dataSource = createDataSource();
        }
        return dataSource;
    }

    private static HikariDataSource createDataSource() {
        Map<String, Object> config = loadYaml();
        Map<String, Object> db = (Map<String, Object>) config.get("database");
        Map<String, Object> pool = (Map<String, Object>) db.get("pool");

        String url = (String) db.get("url");
        String dbHost = System.getenv("DB_HOST");
        if (dbHost != null && !dbHost.isBlank()) {
            url = url.replace("localhost", dbHost);
        }

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(url);
        hikariConfig.setUsername((String) db.get("username"));
        hikariConfig.setPassword((String) db.get("password"));
        hikariConfig.setMaximumPoolSize((int) pool.get("maximum-pool-size"));
        hikariConfig.setMinimumIdle((int) pool.get("minimum-idle"));
        hikariConfig.setConnectionTimeout(((Number) pool.get("connection-timeout")).longValue());

        return new HikariDataSource(hikariConfig);
    }

    private static Map<String, Object> loadYaml() {
        Yaml yaml = new Yaml();
        try (InputStream in = DatabaseConfig.class
                .getClassLoader()
                .getResourceAsStream("application.yml")) {
            return yaml.load(in);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load application.yml", e);
        }
    }

    public static void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}

