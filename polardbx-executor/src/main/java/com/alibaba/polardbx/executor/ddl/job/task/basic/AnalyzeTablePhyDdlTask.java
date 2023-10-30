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

package com.alibaba.polardbx.executor.ddl.job.task.basic;

import com.alibaba.druid.util.JdbcUtils;
import com.alibaba.polardbx.common.exception.TddlRuntimeException;
import com.alibaba.polardbx.common.exception.code.ErrorCode;
import com.alibaba.polardbx.common.utils.Pair;
import com.alibaba.polardbx.common.utils.logger.Logger;
import com.alibaba.polardbx.common.utils.logger.LoggerFactory;
import com.alibaba.polardbx.executor.ddl.job.task.BaseDdlTask;
import com.alibaba.polardbx.executor.ddl.job.task.util.TaskName;
import com.alibaba.polardbx.executor.ddl.newengine.cross.CrossEngineValidator;
import com.alibaba.polardbx.executor.spi.IDataSourceGetter;
import com.alibaba.polardbx.executor.utils.DdlUtils;
import com.alibaba.polardbx.executor.utils.SchemaMetaUtil;
import com.alibaba.polardbx.gms.module.LogLevel;
import com.alibaba.polardbx.gms.module.LogPattern;
import com.alibaba.polardbx.gms.module.Module;
import com.alibaba.polardbx.gms.module.ModuleLogInfo;
import com.alibaba.polardbx.optimizer.OptimizerContext;
import com.alibaba.polardbx.optimizer.config.table.statistic.StatisticManager;
import com.alibaba.polardbx.optimizer.context.ExecutionContext;
import com.alibaba.polardbx.repo.mysql.spi.DatasourceMySQLImplement;
import lombok.Getter;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;

import static com.alibaba.polardbx.common.properties.ConnectionParams.ENABLE_HLL;
import static com.alibaba.polardbx.executor.gms.util.StatisticUtils.forceAnalyzeColumns;
import static com.alibaba.polardbx.executor.gms.util.StatisticUtils.forceAnalyzeColumnsDdl;

@Getter
@TaskName(name = "AnalyzeTablePhyDdlTask")
public class AnalyzeTablePhyDdlTask extends BaseDdlTask {
    private static final Logger logger = LoggerFactory.getLogger("STATISTICS");

    public final String ANALYZE_TABLE_SQL = "ANALYZE TABLE ";

    private List<String> schemaNames;
    private List<String> tableNames;
    private List<Boolean> useHll;
    private List<Boolean> success;

    public AnalyzeTablePhyDdlTask(List<String> schemaNames, List<String> tableNames,
                                  List<Boolean> useHll, List<Boolean> success) {
        super(schemaNames.get(0));
        this.schemaNames = schemaNames;
        this.tableNames = tableNames;
        this.useHll = useHll;
        this.success = success;
        onExceptionTryRollback();
    }

    @Override
    protected void duringTransaction(Connection metaDbConnection, ExecutionContext executionContext) {
        executeImpl(metaDbConnection, executionContext);
    }

    public void executeImpl(Connection metaDbConnection, ExecutionContext executionContext) {
        if (tableNames == null || tableNames.isEmpty()) {
            return;
        }

        List<Boolean> retUseHll = new ArrayList<>(tableNames.size());
        List<Boolean> retSuccess = new ArrayList<>(tableNames.size());
        List<String> fullTableName = new ArrayList<>(tableNames.size());

        long start = System.currentTimeMillis();
        for (int i = 0; i < tableNames.size(); ++i) {
            String schemaName = schemaNames.get(i);
            String table = tableNames.get(i);
            fullTableName.add(schemaName + "." + table);

            IDataSourceGetter mysqlDsGetter = new DatasourceMySQLImplement(schemaName);

            doAnalyzeOneLogicalTable(schemaName, table, mysqlDsGetter, executionContext);

            retUseHll.add(executionContext.getParamManager().getBoolean(ENABLE_HLL) && SchemaMetaUtil
                .checkSupportHll(schemaName));

            if (OptimizerContext.getContext(schemaName).getRuleManager().getTableRule(table) == null) {
                logger.warn(
                    "no table rule for logicalTableName = " + table + ", analyze this table as the single table!");
            }

            retSuccess.add(forceAnalyzeColumnsDdl(schemaName, table, executionContext));

            // refresh plan cache
            DdlUtils.invalidatePlan(schemaName, table, false);
        }

        this.useHll = retUseHll;
        this.success = retSuccess;

        long end = System.currentTimeMillis();
        ModuleLogInfo.getInstance()
            .logRecord(Module.STATISTICS,
                LogPattern.PROCESS_END,
                new String[] {
                    "analyze table " + String.join(",", fullTableName),
                    "consuming " + (end - start) / 1000.0 + " seconds " + executionContext.getTraceId()
                },
                LogLevel.NORMAL);
    }

    protected void doAnalyzeOneLogicalTable(String schemaName, String logicalTableName,
                                            IDataSourceGetter mysqlDsGetter, ExecutionContext executionContext) {
        List<Pair<String, String>> keys =
            StatisticManager.getInstance().buildStatisticKey(schemaName, logicalTableName, executionContext);
        for (Pair<String, String> key : keys) {
            String group = key.getKey();
            String physicalTableName = key.getValue();
            doAnalyzeOnePhysicalTable(group, physicalTableName, mysqlDsGetter);
            if (CrossEngineValidator.isJobInterrupted(executionContext) || Thread.currentThread().isInterrupted()) {
                long jobId = executionContext.getDdlJobId();
                throw new TddlRuntimeException(ErrorCode.ERR_DDL_JOB_ERROR,
                    "The job '" + jobId + "' has been cancelled");
            }
        }
    }

    protected void doAnalyzeOnePhysicalTable(String group, String physicalTableName, IDataSourceGetter mysqlDsGetter) {
        DataSource ds = mysqlDsGetter.getDataSource(group);
        if (ds == null) {
            logger.error("Analyze physical table " + physicalTableName
                + " cannot be fetched, datasource is null, group name is " + group);
            return;
        }
        Connection conn = null;
        PreparedStatement stmt = null;
        try {
            conn = ds.getConnection();
            stmt = conn.prepareStatement(ANALYZE_TABLE_SQL + physicalTableName);
            stmt.execute();
        } catch (Exception e) {
            logger.error("Analyze physical table " + physicalTableName + " ERROR");
        } finally {
            JdbcUtils.close(stmt);
            JdbcUtils.close(conn);
        }
    }
}
