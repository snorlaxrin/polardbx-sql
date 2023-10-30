/*
 * Copyright [2013-2021], Alibaba Group Holding Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.polardbx.executor.ddl.job.task.changset;

import com.alibaba.fastjson.annotation.JSONCreator;
import com.alibaba.polardbx.common.ddl.newengine.DdlTaskState;
import com.alibaba.polardbx.common.eventlogger.EventLogger;
import com.alibaba.polardbx.common.eventlogger.EventType;
import com.alibaba.polardbx.common.exception.TddlNestableRuntimeException;
import com.alibaba.polardbx.common.exception.TddlRuntimeException;
import com.alibaba.polardbx.common.exception.code.ErrorCode;
import com.alibaba.polardbx.common.properties.ConnectionParams;
import com.alibaba.polardbx.common.utils.GeneralUtil;
import com.alibaba.polardbx.executor.corrector.Checker;
import com.alibaba.polardbx.executor.corrector.Reporter;
import com.alibaba.polardbx.executor.ddl.job.task.BaseBackfillTask;
import com.alibaba.polardbx.executor.ddl.job.task.util.TaskName;
import com.alibaba.polardbx.executor.ddl.newengine.job.DdlTask;
import com.alibaba.polardbx.executor.ddl.newengine.meta.DdlEngineAccessorDelegate;
import com.alibaba.polardbx.executor.ddl.newengine.utils.TaskHelper;
import com.alibaba.polardbx.executor.fastchecker.FastChecker;
import com.alibaba.polardbx.executor.gsi.CheckerManager;
import com.alibaba.polardbx.executor.scaleout.corrector.MoveTableChecker;
import com.alibaba.polardbx.executor.scaleout.corrector.MoveTableReporter;
import com.alibaba.polardbx.executor.scaleout.fastchecker.MoveTableFastChecker;
import com.alibaba.polardbx.executor.sync.SyncManagerHelper;
import com.alibaba.polardbx.executor.sync.TablesMetaChangePreemptiveSyncAction;
import com.alibaba.polardbx.gms.metadb.misc.DdlEngineTaskRecord;
import com.alibaba.polardbx.gms.metadb.table.TableInfoManager;
import com.alibaba.polardbx.optimizer.config.table.ComplexTaskMetaManager;
import com.alibaba.polardbx.optimizer.context.ExecutionContext;
import com.alibaba.polardbx.statistics.SQLRecorderLogger;
import lombok.Getter;
import org.apache.calcite.sql.SqlSelect;
import org.apache.commons.lang3.StringUtils;

import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@TaskName(name = "MoveTableCheckTask")
@Getter
public class MoveTableCheckTask extends BaseBackfillTask {
    final private String logicalTableName;
    final private Map<String, String> sourceTargetGroup;
    final private Map<String, Set<String>> sourcePhyTableNames;
    final private Map<String, Set<String>> targetPhyTableNames;
    final private Boolean stopDoubleWrite;
    final private List<String> relatedTables;

    @JSONCreator
    public MoveTableCheckTask(String schemaName, String logicalTableName,
                              Map<String, String> sourceTargetGroup,
                              Map<String, Set<String>> sourcePhyTableNames,
                              Map<String, Set<String>> targetPhyTableNames,
                              Boolean stopDoubleWrite, List<String> relatedTables
    ) {
        super(schemaName);
        this.logicalTableName = logicalTableName;
        this.sourceTargetGroup = sourceTargetGroup;
        this.sourcePhyTableNames = sourcePhyTableNames;
        this.targetPhyTableNames = targetPhyTableNames;
        this.stopDoubleWrite = stopDoubleWrite;
        this.relatedTables = relatedTables;
        onExceptionTryRollback();
    }

    @Override
    protected void executeImpl(ExecutionContext executionContext) {
        // for debug, skip checker
        if (executionContext.getParamManager().getBoolean(ConnectionParams.SKIP_CHANGE_SET_CHECKER)
            || !executionContext.getParamManager().getBoolean(ConnectionParams.SCALEOUT_CHECK_AFTER_BACKFILL)) {
            if (stopDoubleWrite) {
                DdlTask currentTask = this;
                DdlEngineAccessorDelegate delegate = new DdlEngineAccessorDelegate<Integer>() {
                    @Override
                    protected Integer invoke() {
                        ComplexTaskMetaManager
                            .updateSubTasksStatusByJobIdAndObjName(getJobId(),
                                schemaName,
                                logicalTableName,
                                ComplexTaskMetaManager.ComplexTaskStatus.WRITE_REORG,
                                ComplexTaskMetaManager.ComplexTaskStatus.DOING_CHECKER,
                                getConnection());
                        try {
                            for (String tbName : relatedTables) {
                                TableInfoManager.updateTableVersionWithoutDataId(schemaName, tbName, getConnection());
                            }
                        } catch (Exception e) {
                            throw GeneralUtil.nestedException(e);
                        }
                        currentTask.setState(DdlTaskState.DIRTY);
                        DdlEngineTaskRecord taskRecord = TaskHelper.toDdlEngineTaskRecord(currentTask);
                        return engineTaskAccessor.updateTask(taskRecord);
                    }
                };
                delegate.execute();

                LOGGER.info(
                    String.format(
                        "Update table status[ schema:%s, table:%s, before state:%s, after state:%s]",
                        schemaName,
                        logicalTableName,
                        ComplexTaskMetaManager.ComplexTaskStatus.WRITE_REORG.name(),
                        ComplexTaskMetaManager.ComplexTaskStatus.DOING_CHECKER.name()));

                try {
                    SyncManagerHelper.sync(
                        new TablesMetaChangePreemptiveSyncAction(schemaName, relatedTables, 1500L, 1500L,
                            TimeUnit.SECONDS),
                        true);
                } catch (Throwable t) {
                    LOGGER.error(String.format(
                        "error occurs while sync table meta, schemaName:%s, tableName:%s", schemaName,
                        logicalTableName));
                    throw GeneralUtil.nestedException(t);
                }
            }
            return;
        }

        final boolean useFastChecker =
            FastChecker.isSupported(schemaName) &&
                executionContext.getParamManager().getBoolean(ConnectionParams.SCALEOUT_BACKFILL_USE_FASTCHECKER);
        if (stopDoubleWrite) {
            checkWithStopDoubleWrite(executionContext, useFastChecker);
        } else {
            checkWithDoubleCheck(executionContext, useFastChecker);
        }
    }

    @Override
    protected void onRollbackSuccess(ExecutionContext executionContext) {
        if (stopDoubleWrite) {
            // sync to restore the status of table meta
            SyncManagerHelper.sync(
                new TablesMetaChangePreemptiveSyncAction(schemaName, relatedTables, 1500L, 1500L,
                    TimeUnit.MICROSECONDS));
        }
    }

    @Override
    protected void rollbackImpl(ExecutionContext executionContext) {
        if (stopDoubleWrite) {
            DdlEngineAccessorDelegate delegate = new DdlEngineAccessorDelegate<Integer>() {
                @Override
                protected Integer invoke() {
                    ComplexTaskMetaManager
                        .updateSubTasksStatusByJobIdAndObjName(getJobId(), schemaName, logicalTableName,
                            ComplexTaskMetaManager.ComplexTaskStatus.DOING_CHECKER,
                            ComplexTaskMetaManager.ComplexTaskStatus.WRITE_REORG,
                            getConnection());
                    return null;
                }
            };
            delegate.execute();

            LOGGER.info(String
                .format(
                    "Rollback table status[ schema:%s, table:%s, before state:%s, after state:%s]",
                    schemaName,
                    logicalTableName,
                    ComplexTaskMetaManager.ComplexTaskStatus.WRITE_REORG.name(),
                    ComplexTaskMetaManager.ComplexTaskStatus.DOING_CHECKER.name()));
        }
    }

    private void checkWithStopDoubleWrite(ExecutionContext executionContext, boolean useFastChecker) {
        // check and unlock
        if (useFastChecker) {
            boolean fastCheck = fastCheckWithCatchEx(executionContext);
            if (!fastCheck) {
                throw GeneralUtil.nestedException(
                    "alter tableGroup checker found error. Please try to rollback/recover this job");
            }
        } else {
            throw new TddlRuntimeException(ErrorCode.ERR_SCALEOUT_EXECUTE,
                "move database should use fastchecker to check");
        }
    }

    private void checkWithDoubleCheck(ExecutionContext executionContext, boolean useFastChecker) {
        if (useFastChecker && fastCheckWithCatchEx(executionContext)) {
            // ignore success
        } else {
            checkInCN(executionContext);
        }
    }

    protected boolean fastCheckWithCatchEx(ExecutionContext executionContext) {
        boolean fastCheckSucc = false;
        try {
            fastCheckSucc = fastCheck(executionContext);
        } catch (Throwable ex) {
            fastCheckSucc = false;
            String msg = String.format(
                "Failed to use fastChecker to check move database backFill because of throwing exceptions,  so use old checker instead");
            SQLRecorderLogger.ddlLogger.warn(msg, ex);
        }
        return fastCheckSucc;
    }

    void checkInCN(ExecutionContext executionContext) {
        final long batchSize =
            executionContext.getParamManager().getLong(ConnectionParams.SCALEOUT_CHECK_BATCH_SIZE);
        final long speedLimit =
            executionContext.getParamManager().getLong(ConnectionParams.SCALEOUT_CHECK_SPEED_LIMITATION);
        final long speedMin =
            executionContext.getParamManager().getLong(ConnectionParams.SCALEOUT_CHECK_SPEED_MIN);
        final long parallelism =
            executionContext.getParamManager().getLong(ConnectionParams.SCALEOUT_CHECK_PARALLELISM);
        final long earlyFailNumber =
            executionContext.getParamManager().getLong(ConnectionParams.SCALEOUT_EARLY_FAIL_NUMBER);

        Checker checker = MoveTableChecker.create(schemaName,
            logicalTableName,
            logicalTableName,
            batchSize,
            speedMin,
            speedLimit,
            parallelism,
            SqlSelect.LockMode.UNDEF,
            SqlSelect.LockMode.UNDEF,
            executionContext,
            sourcePhyTableNames,
            targetPhyTableNames,
            sourceTargetGroup);
        checker.setInBackfill(true);
        if (null == executionContext.getDdlJobId() || 0 == executionContext.getDdlJobId()) {
            checker.setJobId(JOB_ID_GENERATOR.nextId());
        } else {
            checker.setJobId(executionContext.getDdlJobId());
        }

        // Run the simple check.
        final Reporter reporter = new MoveTableReporter(earlyFailNumber);
        try {
            checker.check(executionContext, reporter);
        } catch (TddlNestableRuntimeException e) {
            if (e.getMessage().contains("Too many conflicts")) {
                throw GeneralUtil
                    .nestedException(
                        "move table checker error limit exceeded. Please try to rollback/recover this job");
            } else {
                throw e;
            }
        }

        final List<CheckerManager.CheckerReport> checkerReports = reporter.getCheckerReports();
        if (!checkerReports.isEmpty()) {
            // Some error found.
            throw GeneralUtil.nestedException(
                "move table checker found error after backfill. Please try to rollback/recover this job");
        }
    }

    boolean fastCheck(ExecutionContext executionContext) {
        long startTime = System.currentTimeMillis();

        String schemaName = getSchemaName();
        String logicalTable = logicalTableName;

        SQLRecorderLogger.ddlLogger.warn(MessageFormat.format(
            "FastChecker for move table, schema [{0}] logical src table [{1}] logic dst table [{2}] start",
            schemaName, logicalTable, logicalTable));
        final int fastCheckerParallelism =
            executionContext.getParamManager().getInt(ConnectionParams.SCALEOUT_FASTCHECKER_PARALLELISM);

        FastChecker fastChecker = MoveTableFastChecker
            .create(schemaName, logicalTable, sourceTargetGroup,
                sourcePhyTableNames, targetPhyTableNames, fastCheckerParallelism, executionContext);

        boolean fastCheckResult = false;
        try {
            fastCheckResult = fastChecker.checkWithChangeSet(executionContext, stopDoubleWrite, this, relatedTables);
        } catch (TddlNestableRuntimeException e) {
            //other exception, we simply throw out
            throw new TddlRuntimeException(ErrorCode.ERR_SCALEOUT_EXECUTE, e,
                "alter tablegroup fastchecker failed to check");
        } finally {
            SQLRecorderLogger.ddlLogger.warn(MessageFormat.format(
                "FastChecker for alter tablegroup, schema [{0}] logical src table [{1}] finish, time use [{2}], check result [{3}]",
                schemaName, logicalTableName,
                (System.currentTimeMillis() - startTime) / 1000.0,
                fastCheckResult ? "pass" : "not pass")
            );
            if (!fastCheckResult) {
                EventLogger.log(EventType.DDL_WARN, "FastChecker failed");
            }
        }

        return fastCheckResult;
    }

    @Override
    protected String remark() {
        return "|tableName: " + logicalTableName;
    }
}
