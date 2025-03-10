package com.judgementday.event;

import com.judgementday.model.Appeal;
import com.judgementday.model.Punishment;
import com.judgementday.model.Report;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * Event called when a punishment is issued
 */
public class PunishmentEvent extends Event implements Cancellable {

    private static final HandlerList handlers = new HandlerList();
    private boolean cancelled = false;
    private final Punishment punishment;

    public PunishmentEvent(Punishment punishment) {
        this.punishment = punishment;
    }

    public Punishment getPunishment() {
        return punishment;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}

/**
 * Event called when a punishment is revoked
 */
class PunishmentRevokeEvent extends Event implements Cancellable {

    private static final HandlerList handlers = new HandlerList();
    private boolean cancelled = false;
    private final Punishment punishment;
    private final UUID revokerUuid;
    private final String revokerName;

    public PunishmentRevokeEvent(Punishment punishment, UUID revokerUuid, String revokerName) {
        this.punishment = punishment;
        this.revokerUuid = revokerUuid;
        this.revokerName = revokerName;
    }

    public Punishment getPunishment() {
        return punishment;
    }

    public UUID getRevokerUuid() {
        return revokerUuid;
    }

    public String getRevokerName() {
        return revokerName;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}

/**
 * Event called when a report is created
 */
public class ReportCreateEvent extends Event implements Cancellable {

    private static final HandlerList handlers = new HandlerList();
    private boolean cancelled = false;
    private final Report report;

    public ReportCreateEvent(Report report) {
        this.report = report;
    }

    public Report getReport() {
        return report;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}

/**
 * Event called when a report is processed
 */
public class ReportProcessEvent extends Event implements Cancellable {

    private static final HandlerList handlers = new HandlerList();
    private boolean cancelled = false;
    private final Report report;
    private final UUID processorUuid;
    private final String processorName;
    private final int punishmentId;

    public ReportProcessEvent(Report report, UUID processorUuid, String processorName, int punishmentId) {
        this.report = report;
        this.processorUuid = processorUuid;
        this.processorName = processorName;
        this.punishmentId = punishmentId;
    }

    public Report getReport() {
        return report;
    }

    public UUID getProcessorUuid() {
        return processorUuid;
    }

    public String getProcessorName() {
        return processorName;
    }

    public int getPunishmentId() {
        return punishmentId;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}

/**
 * Event called when an appeal is created
 */
class AppealCreateEvent extends Event implements Cancellable {

    private static final HandlerList handlers = new HandlerList();
    private boolean cancelled = false;
    private final Appeal appeal;

    public AppealCreateEvent(Appeal appeal) {
        this.appeal = appeal;
    }

    public Appeal getAppeal() {
        return appeal;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}

/**
 * Event called when an appeal is processed
 */
class AppealProcessEvent extends Event implements Cancellable {

    private static final HandlerList handlers = new HandlerList();
    private boolean cancelled = false;
    private final Appeal appeal;
    private final UUID reviewerUuid;
    private final String reviewerName;
    private final String comment;
    private final boolean approved;

    public AppealProcessEvent(Appeal appeal, UUID reviewerUuid, String reviewerName, String comment, boolean approved) {
        this.appeal = appeal;
        this.reviewerUuid = reviewerUuid;
        this.reviewerName = reviewerName;
        this.comment = comment;
        this.approved = approved;
    }

    public Appeal getAppeal() {
        return appeal;
    }

    public UUID getReviewerUuid() {
        return reviewerUuid;
    }

    public String getReviewerName() {
        return reviewerName;
    }

    public String getComment() {
        return comment;
    }

    public boolean isApproved() {
        return approved;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}