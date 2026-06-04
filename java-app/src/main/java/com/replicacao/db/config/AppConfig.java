package com.replicacao.db.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public class AppConfig {

    private final Properties props = new Properties();

    public AppConfig() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("config.properties")) {
            if (is == null) throw new RuntimeException("config.properties não encontrado no classpath.");
            props.load(is);
        } catch (IOException e) {
            throw new RuntimeException("Falha ao carregar config.properties", e);
        }
    }

    public String getWriteHost()     { return props.getProperty("db.write.host"); }
    public int    getWritePort()     { return Integer.parseInt(props.getProperty("db.write.port")); }
    public String getWriteDatabase() { return props.getProperty("db.write.database"); }
    public String getWriteUsername() { return props.getProperty("db.write.username"); }
    public String getWritePassword() { return props.getProperty("db.write.password"); }

    public List<String> getReadReplicas() {
        String raw = props.getProperty("db.read.replicas", "");
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public String getReadDatabase() { return props.getProperty("db.read.database"); }
    public String getReadUsername() { return props.getProperty("db.read.username"); }
    public String getReadPassword() { return props.getProperty("db.read.password"); }

    public long getCycleIntervalMs() {
        return Long.parseLong(props.getProperty("app.cycle.interval.ms", "3000"));
    }

    public int getCycles() {
        return Integer.parseInt(props.getProperty("app.cycles", "0"));
    }
}
