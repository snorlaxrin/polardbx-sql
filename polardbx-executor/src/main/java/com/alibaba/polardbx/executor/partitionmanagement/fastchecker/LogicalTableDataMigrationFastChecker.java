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

package com.alibaba.polardbx.executor.partitionmanagement.fastchecker;

import com.alibaba.polardbx.common.exception.TddlRuntimeException;
import com.alibaba.polardbx.common.exception.code.ErrorCode;
import com.alibaba.polardbx.common.properties.ConnectionParams;
import com.alibaba.polardbx.executor.backfill.Extractor;
import com.alibaba.polardbx.executor.fastchecker.FastChecker;
import com.alibaba.polardbx.executor.gsi.GsiUtils;
import com.alibaba.polardbx.executor.gsi.PhysicalPlanBuilder;
import com.alibaba.polardbx.executor.ddl.workqueue.BackFillThreadPool;
import com.alibaba.polardbx.optimizer.OptimizerContext;
import com.alibaba.polardbx.optimizer.config.table.ColumnMeta;
import com.alibaba.polardbx.optimizer.config.table.SchemaManager;
import com.alibaba.polardbx.optimizer.config.table.TableMeta;
import com.alibaba.polardbx.optimizer.context.ExecutionContext;
import com.alibaba.polardbx.optimizer.core.rel.PhyTableOperation;
import com.alibaba.polardbx.optimizer.utils.TableTopologyUtil;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by zhuqiwei.
 *
 * @author zhuqiwei
 */
public class LogicalTableDataMigrationFastChecker extends FastChecker {
    public LogicalTableDataMigrationFastChecker(String srcSchemaName, String dstSchemaName, String srcLogicalTableName,
                                                String dstLogicalTableName, Map<String, String> sourceTargetGroup,
                                                Map<String, Set<String>> srcPhyDbAndTables,
                                                Map<String, Set<String>> dstPhyDbAndTables, List<String> srcColumns,
                                                List<String> dstColumns, List<String> srcPks, List<String> dstPks,
                                                long parallelism, int lockTimeOut,
                                                PhyTableOperation planSelectHashCheckSrc,
                                                PhyTableOperation planSelectHashCheckWithUpperBoundSrc,
                                                PhyTableOperation planSelectHashCheckWithLowerBoundSrc,
                                                PhyTableOperation planSelectHashCheckWithLowerUpperBoundSrc,
                                                PhyTableOperation planSelectHashCheckDst,
                                                PhyTableOperation planSelectHashCheckWithUpperBoundDst,
                                                PhyTableOperation planSelectHashCheckWithLowerBoundDst,
                                                PhyTableOperation planSelectHashCheckWithLowerUpperBoundDst,
                                                PhyTableOperation planIdleSelectSrc,
                                                PhyTableOperation planIdleSelectDst,
                                                PhyTableOperation planSelectSampleSrc,
                                                PhyTableOperation planSelectSampleDst) {
        super(srcSchemaName, dstSchemaName, srcLogicalTableName, dstLogicalTableName, sourceTargetGroup,
            srcPhyDbAndTables, dstPhyDbAndTables, srcColumns, dstColumns, srcPks, dstPks, parallelism, lockTimeOut,
            planSelectHashCheckSrc, planSelectHashCheckWithUpperBoundSrc, planSelectHashCheckWithLowerBoundSrc,
            planSelectHashCheckWithLowerUpperBoundSrc, planSelectHashCheckDst, planSelectHashCheckWithUpperBoundDst,
            planSelectHashCheckWithLowerBoundDst, planSelectHashCheckWithLowerUpperBoundDst, planIdleSelectSrc,
            planIdleSelectDst, planSelectSampleSrc, planSelectSampleDst);
    }

    /**
     * srcTable: primary table
     * dstTable: gsi table
     */
    public static List<FastChecker> create(String logicalSchemaSrc, String logicalSchemaDst, String logicalTableSrc,
                                           String logicalTableDst, long parallelism, ExecutionContext ec) {
        final SchemaManager srcSm = OptimizerContext.getContext(logicalSchemaSrc).getLatestSchemaManager();
        final TableMeta tableMetaSrc = srcSm.getTable(logicalTableSrc);
        final SchemaManager dstSm = OptimizerContext.getContext(logicalSchemaDst).getLatestSchemaManager();
        final TableMeta tableMetaDst = dstSm.getTable(logicalTableDst);
        boolean isBroadcastTable = TableTopologyUtil.isBroadcast(tableMetaSrc);

        if (null == tableMetaSrc || null == tableMetaDst) {
            throw new TddlRuntimeException(ErrorCode.ERR_SCALEOUT_EXECUTE, "FastChecker find no table meta");
        }
        final List<String> allColumns =
            tableMetaDst.getAllColumns().stream().map(ColumnMeta::getName).collect(Collectors.toList());

        // 重要：构造planSelectSampleSrc 和 planSelectSampleDst时，传入的主键必须按原本的主键顺序!!!
        final List<String> pks = FastChecker.getorderedPrimaryKeys(tableMetaDst, ec);

        if (parallelism <= 0) {
            parallelism = Math.max(BackFillThreadPool.getInstance().getCorePoolSize() / 2, 1);
        }

        final int lockTimeOut = ec.getParamManager().getInt(ConnectionParams.FASTCHECKER_LOCK_TIMEOUT);

        final PhysicalPlanBuilder srcBuilder = new PhysicalPlanBuilder(logicalSchemaSrc, ec);
        final PhysicalPlanBuilder dstBuilder = new PhysicalPlanBuilder(logicalSchemaDst, ec);

        Map<String, Set<String>> srcPhyDbAndTbs = GsiUtils.getPhyTables(logicalSchemaSrc, logicalTableSrc);
        Map<String, Set<String>> dstPhyDbAndTbs = GsiUtils.getPhyTables(logicalSchemaDst, logicalTableDst);

        if (isBroadcastTable) {
            if (srcPhyDbAndTbs.isEmpty() || dstPhyDbAndTbs.isEmpty()) {
                return null;
            }
            String srcDb = srcPhyDbAndTbs.keySet().stream().findFirst().get();
            String dstDb = dstPhyDbAndTbs.keySet().stream().findFirst().get();
            if (srcPhyDbAndTbs.get(srcDb).isEmpty() || dstPhyDbAndTbs.get(dstDb).isEmpty()) {
                return null;
            }
            String srcTb = srcPhyDbAndTbs.get(srcDb).stream().findFirst().get();
            List<String> dstTbs = dstPhyDbAndTbs.get(dstDb).stream().collect(Collectors.toList());

            List<FastChecker> fastCheckers = new ArrayList<>();

            for (String dstTb : dstTbs) {
                FastChecker fc =
                    new LogicalTableDataMigrationFastChecker(logicalSchemaSrc, logicalSchemaDst, logicalTableSrc,
                        logicalTableDst, null, ImmutableMap.of(srcDb, ImmutableSet.of(srcTb)),
                        ImmutableMap.of(dstDb, ImmutableSet.of(dstTb)), allColumns, allColumns, pks, pks, parallelism,
                        lockTimeOut,

                        srcBuilder.buildSelectHashCheckForChecker(tableMetaSrc, allColumns, pks, false, false),
                        srcBuilder.buildSelectHashCheckForChecker(tableMetaSrc, allColumns, pks, false, true),
                        srcBuilder.buildSelectHashCheckForChecker(tableMetaSrc, allColumns, pks, true, false),
                        srcBuilder.buildSelectHashCheckForChecker(tableMetaSrc, allColumns, pks, true, true),

                        dstBuilder.buildSelectHashCheckForChecker(tableMetaDst, allColumns, pks, false, false),
                        dstBuilder.buildSelectHashCheckForChecker(tableMetaDst, allColumns, pks, false, true),
                        dstBuilder.buildSelectHashCheckForChecker(tableMetaDst, allColumns, pks, true, false),
                        dstBuilder.buildSelectHashCheckForChecker(tableMetaDst, allColumns, pks, true, true),

                        srcBuilder.buildIdleSelectForChecker(tableMetaSrc, allColumns),
                        dstBuilder.buildIdleSelectForChecker(tableMetaDst, allColumns),

                        srcBuilder.buildSqlSelectForSample(tableMetaSrc, pks, pks, false, false),
                        dstBuilder.buildSqlSelectForSample(tableMetaDst, pks, pks, false, false));
                fastCheckers.add(fc);
            }

            return fastCheckers;
        } else {
            return ImmutableList.of(
                new LogicalTableDataMigrationFastChecker(logicalSchemaSrc, logicalSchemaDst, logicalTableSrc,
                    logicalTableDst, null, srcPhyDbAndTbs, dstPhyDbAndTbs, allColumns, allColumns, pks, pks,
                    parallelism, lockTimeOut,

                    srcBuilder.buildSelectHashCheckForChecker(tableMetaSrc, allColumns, pks, false, false),
                    srcBuilder.buildSelectHashCheckForChecker(tableMetaSrc, allColumns, pks, false, true),
                    srcBuilder.buildSelectHashCheckForChecker(tableMetaSrc, allColumns, pks, true, false),
                    srcBuilder.buildSelectHashCheckForChecker(tableMetaSrc, allColumns, pks, true, true),

                    dstBuilder.buildSelectHashCheckForChecker(tableMetaDst, allColumns, pks, false, false),
                    dstBuilder.buildSelectHashCheckForChecker(tableMetaDst, allColumns, pks, false, true),
                    dstBuilder.buildSelectHashCheckForChecker(tableMetaDst, allColumns, pks, true, false),
                    dstBuilder.buildSelectHashCheckForChecker(tableMetaDst, allColumns, pks, true, true),

                    srcBuilder.buildIdleSelectForChecker(tableMetaSrc, allColumns),
                    dstBuilder.buildIdleSelectForChecker(tableMetaDst, allColumns),

                    srcBuilder.buildSqlSelectForSample(tableMetaSrc, pks, pks, false, false),
                    dstBuilder.buildSqlSelectForSample(tableMetaDst, pks, pks, false, false)));
        }
    }
}
