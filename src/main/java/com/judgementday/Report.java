package com.judgementday.model;

import org.bukkit.configuration.serialization.ConfigurationSerializable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Report implements ConfigurationSerializable {

    private int id;
    private UUID reporterUuid;
    private String reporterName;
    private UUID reportedUuid;
    private String reportedName;
    private String reason;
    private long timeCreated;
    private boolean processed;
    private UUID processorUuid;
    private String processorName;
    private long timeProcessed;
    private int resultPunishmentId;

    public Report(int id, UUID reporterUuid, String reporterName, UUID reportedUuid, String reportedName, String reason) {
        this.id = id;
        this.reporterUuid = reporterUuid;
        this.reporterName = reporterName;
        this.reportedUuid = reportedUuid;
        this.reportedName = reportedName;
        this.reason = reason;
        this.timeCreated = System.currentTimeMillis();
        this.processed = false;
    }

    public Report(Map<String, Object> map) {
        this.id = (int) map.get("id");
        this.reporterUuid = UUID.fromString((String) map.get("reporterUuid"));
        this.reporterName = (String) map.get("reporterName");
        this.reportedUuid = UUID.fromString((String) map.get("reportedUuid"));
        this.reportedName = (String) map.get("reportedName");
        this.reason = (String) map.get("reason");
        this.timeCreated = (long) map.get("timeCreated");
        this.processed = (boolean) map.get("processed");

        if (processed) {
            this.processorUuid = UUID.fromString((String) map.get("processorUuid"));
            this.processorName = (String) map.get("processorName");
            this.timeProcessed = (long) map.get("timeProcessed");
            if (map.containsKey("resultPunishmentId")) {
                this.resultPunishmentId = (int) map.get("resultPunishmentId");
            }
        }
    }

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("reporterUuid", reporterUuid.toString());
        map.put("reporterName", reporterName);
        map.put("reportedUuid", reportedUuid.toString());
        map.put("reportedName", reportedName);
        map.put("reason", reason);
        map.put("timeCreated", timeCreated);
        map.put("processed", processed);

        if (processed) {
            map.put("processorUuid", processorUuid.toString());
            map.put("processorName", processorName);
            map.put("timeProcessed", timeProcessed);
            if (resultPunishmentId > 0) {
                map.put("resultPunishmentId", resultPunishmentId);
            }
        }

        return map;
    }

    public void markProcessed(UUID processorUuid, String processorName, int punishmentId) {
        this.processed = true;
        this.processorUuid = processorUuid;
        this.processorName = processorName;
        this.timeProcessed = System.currentTimeMillis();
        this.resultPunishmentId = punishmentId;
    }

    // Getters
    public int getId() {
        return id;
    }

    public UUID getReporterUuid() {
        return reporterUuid;
    }

    public String getReporterName() {
        return reporterName;
    }

    public UUID getReportedUuid() {
        return reportedUuid;
    }

    public String getReportedName() {
        return reportedName;
    }

    public String getReason() {
        return reason;
    }

    public long getTimeCreated() {
        return timeCreated;
    }

    public boolean isProcessed() {
        return processed;
    }

    public UUID getProcessorUuid() {
        return processorUuid;
    }

    public String getProcessorName() {
        return processorName;
    }

    public long getTimeProcessed() {
        return timeProcessed;
    }

    public int getResultPunishmentId() {
        return resultPunishmentId;
    }
}