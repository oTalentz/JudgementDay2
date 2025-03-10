package com.judgementday.model;

import org.bukkit.configuration.serialization.ConfigurationSerializable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Punishment implements ConfigurationSerializable {

    private int id;
    private UUID targetUuid;
    private String targetName;
    private UUID punisherUuid;
    private String punisherName;
    private PunishmentType type;
    private String reason;
    private long timeIssued;
    private long duration;
    private long expiry;
    private boolean active;
    private String proofLink;
    private UUID revokerUuid;
    private String revokerName;
    private long timeRevoked;
    private int level;

    public Punishment(int id, UUID targetUuid, String targetName, UUID punisherUuid, String punisherName,
                      PunishmentType type, String reason, long duration, String proofLink, int level) {
        this.id = id;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.punisherUuid = punisherUuid;
        this.punisherName = punisherName;
        this.type = type;
        this.reason = reason;
        this.timeIssued = System.currentTimeMillis();
        this.duration = duration;
        this.expiry = duration == -1 ? -1 : timeIssued + duration;
        this.active = true;
        this.proofLink = proofLink;
        this.level = level;
    }

    public Punishment(Map<String, Object> map) {
        this.id = (int) map.get("id");
        this.targetUuid = UUID.fromString((String) map.get("targetUuid"));
        this.targetName = (String) map.get("targetName");
        this.punisherUuid = UUID.fromString((String) map.get("punisherUuid"));
        this.punisherName = (String) map.get("punisherName");
        this.type = PunishmentType.valueOf((String) map.get("type"));
        this.reason = (String) map.get("reason");
        this.timeIssued = (long) map.get("timeIssued");
        this.duration = (long) map.get("duration");
        this.expiry = (long) map.get("expiry");
        this.active = (boolean) map.get("active");
        this.proofLink = (String) map.get("proofLink");
        this.level = (int) map.get("level");

        if (map.containsKey("revokerUuid")) {
            this.revokerUuid = UUID.fromString((String) map.get("revokerUuid"));
            this.revokerName = (String) map.get("revokerName");
            this.timeRevoked = (long) map.get("timeRevoked");
        }
    }

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("targetUuid", targetUuid.toString());
        map.put("targetName", targetName);
        map.put("punisherUuid", punisherUuid.toString());
        map.put("punisherName", punisherName);
        map.put("type", type.name());
        map.put("reason", reason);
        map.put("timeIssued", timeIssued);
        map.put("duration", duration);
        map.put("expiry", expiry);
        map.put("active", active);
        map.put("proofLink", proofLink);
        map.put("level", level);

        if (revokerUuid != null) {
            map.put("revokerUuid", revokerUuid.toString());
            map.put("revokerName", revokerName);
            map.put("timeRevoked", timeRevoked);
        }

        return map;
    }

    public void revoke(UUID revokerUuid, String revokerName) {
        this.active = false;
        this.revokerUuid = revokerUuid;
        this.revokerName = revokerName;
        this.timeRevoked = System.currentTimeMillis();
    }

    public boolean isExpired() {
        return !active || (expiry != -1 && expiry < System.currentTimeMillis());
    }

    public long getTimeRemaining() {
        if (expiry == -1) return -1;
        long remaining = expiry - System.currentTimeMillis();
        return Math.max(0, remaining);
    }

    // Getters
    public int getId() {
        return id;
    }

    public UUID getTargetUuid() {
        return targetUuid;
    }

    public String getTargetName() {
        return targetName;
    }

    public UUID getPunisherUuid() {
        return punisherUuid;
    }

    public String getPunisherName() {
        return punisherName;
    }

    public PunishmentType getType() {
        return type;
    }

    public String getReason() {
        return reason;
    }

    public long getTimeIssued() {
        return timeIssued;
    }

    public long getDuration() {
        return duration;
    }

    public long getExpiry() {
        return expiry;
    }

    public boolean isActive() {
        return active;
    }

    public String getProofLink() {
        return proofLink;
    }

    public UUID getRevokerUuid() {
        return revokerUuid;
    }

    public String getRevokerName() {
        return revokerName;
    }

    public long getTimeRevoked() {
        return timeRevoked;
    }

    public int getLevel() {
        return level;
    }
}