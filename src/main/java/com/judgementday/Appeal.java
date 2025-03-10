package com.judgementday.model;

import org.bukkit.configuration.serialization.ConfigurationSerializable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Appeal implements ConfigurationSerializable {

    private int id;
    private int punishmentId;
    private UUID playerUuid;
    private String playerName;
    private String reason;
    private String evidence;
    private long timeCreated;
    private AppealStatus status;
    private UUID reviewerUuid;
    private String reviewerName;
    private long timeReviewed;
    private String reviewComment;

    public enum AppealStatus {
        PENDING("Pending"),
        APPROVED("Approved"),
        DENIED("Denied");

        private final String displayName;

        AppealStatus(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public Appeal(int id, int punishmentId, UUID playerUuid, String playerName, String reason, String evidence) {
        this.id = id;
        this.punishmentId = punishmentId;
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.reason = reason;
        this.evidence = evidence;
        this.timeCreated = System.currentTimeMillis();
        this.status = AppealStatus.PENDING;
    }

    public Appeal(Map<String, Object> map) {
        this.id = (int) map.get("id");
        this.punishmentId = (int) map.get("punishmentId");
        this.playerUuid = UUID.fromString((String) map.get("playerUuid"));
        this.playerName = (String) map.get("playerName");
        this.reason = (String) map.get("reason");
        this.evidence = (String) map.get("evidence");
        this.timeCreated = (long) map.get("timeCreated");
        this.status = AppealStatus.valueOf((String) map.get("status"));

        if (map.containsKey("reviewerUuid")) {
            this.reviewerUuid = UUID.fromString((String) map.get("reviewerUuid"));
            this.reviewerName = (String) map.get("reviewerName");
            this.timeReviewed = (long) map.get("timeReviewed");
            this.reviewComment = (String) map.get("reviewComment");
        }
    }

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("punishmentId", punishmentId);
        map.put("playerUuid", playerUuid.toString());
        map.put("playerName", playerName);
        map.put("reason", reason);
        map.put("evidence", evidence);
        map.put("timeCreated", timeCreated);
        map.put("status", status.name());

        if (reviewerUuid != null) {
            map.put("reviewerUuid", reviewerUuid.toString());
            map.put("reviewerName", reviewerName);
            map.put("timeReviewed", timeReviewed);
            map.put("reviewComment", reviewComment);
        }

        return map;
    }

    public void approve(UUID reviewerUuid, String reviewerName, String comment) {
        this.status = AppealStatus.APPROVED;
        this.reviewerUuid = reviewerUuid;
        this.reviewerName = reviewerName;
        this.timeReviewed = System.currentTimeMillis();
        this.reviewComment = comment;
    }

    public void deny(UUID reviewerUuid, String reviewerName, String comment) {
        this.status = AppealStatus.DENIED;
        this.reviewerUuid = reviewerUuid;
        this.reviewerName = reviewerName;
        this.timeReviewed = System.currentTimeMillis();
        this.reviewComment = comment;
    }

    // Getters
    public int getId() {
        return id;
    }

    public int getPunishmentId() {
        return punishmentId;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getReason() {
        return reason;
    }

    public String getEvidence() {
        return evidence;
    }

    public long getTimeCreated() {
        return timeCreated;
    }

    public AppealStatus getStatus() {
        return status;
    }

    public UUID getReviewerUuid() {
        return reviewerUuid;
    }

    public String getReviewerName() {
        return reviewerName;
    }

    public long getTimeReviewed() {
        return timeReviewed;
    }

    public String getReviewComment() {
        return reviewComment;
    }
}