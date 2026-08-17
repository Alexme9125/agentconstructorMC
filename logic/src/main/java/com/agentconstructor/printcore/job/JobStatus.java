package com.agentconstructor.printcore.job;

public enum JobStatus {
    SUBMITTED,
    DRY_RUN_OK,
    RUNNING,
    COMPLETED,
    FAILED,
    ROLLED_BACK,
    CANCELLED
}
