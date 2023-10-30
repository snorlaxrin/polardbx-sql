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

package com.alibaba.polardbx.executor.handler.ddl;

import com.alibaba.polardbx.common.cdc.CdcManagerHelper;
import com.alibaba.polardbx.common.cdc.DdlVisibility;
import com.alibaba.polardbx.common.charset.CharsetName;
import com.alibaba.polardbx.common.charset.CollationName;
import com.alibaba.polardbx.common.exception.TddlRuntimeException;
import com.alibaba.polardbx.common.exception.code.ErrorCode;
import com.alibaba.polardbx.common.properties.ConnectionParams;
import com.alibaba.polardbx.common.properties.ConnectionProperties;
import com.alibaba.polardbx.common.properties.DynamicConfig;
import com.alibaba.polardbx.config.ConfigDataMode;
import com.alibaba.polardbx.config.ConfigDataMode.Mode;
import com.alibaba.polardbx.executor.cursor.Cursor;
import com.alibaba.polardbx.executor.cursor.impl.AffectRowCursor;
import com.alibaba.polardbx.executor.handler.HandlerCommon;
import com.alibaba.polardbx.executor.mpp.planner.PartitionHandle;
import com.alibaba.polardbx.executor.spi.IRepository;
import com.alibaba.polardbx.executor.utils.ExecUtils;
import com.alibaba.polardbx.gms.locality.LocalityDesc;
import com.alibaba.polardbx.gms.topology.CreateDbInfo;
import com.alibaba.polardbx.gms.topology.DbInfoManager;
import com.alibaba.polardbx.gms.topology.DbInfoRecord;
import com.alibaba.polardbx.gms.topology.DbTopologyManager;
import com.alibaba.polardbx.gms.topology.StorageInfoRecord;
import com.alibaba.polardbx.gms.util.DbEventUtil;
import com.alibaba.polardbx.gms.util.DbNameUtil;
import com.alibaba.polardbx.optimizer.context.ExecutionContext;
import com.alibaba.polardbx.optimizer.core.rel.ddl.LogicalCreateDatabase;
import com.alibaba.polardbx.optimizer.locality.LocalityInfoUtils;
import com.alibaba.polardbx.optimizer.locality.LocalityManager;
import com.alibaba.polardbx.optimizer.locality.StoragePoolManager;
import com.alibaba.polardbx.optimizer.utils.DdlCharsetInfo;
import com.alibaba.polardbx.optimizer.utils.DdlCharsetInfoUtil;
import com.alibaba.polardbx.optimizer.utils.KeyWordsUtil;
import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.sql.SqlCreateDatabase;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;

import static com.alibaba.polardbx.executor.ddl.job.task.cdc.CdcMarkUtil.buildExtendParameter;

/**
 * @author chenmo.cm
 */
public class LogicalCreateDatabaseHandler extends HandlerCommon {

    public LogicalCreateDatabaseHandler(IRepository repo) {
        super(repo);
    }

    @Override
    public Cursor handle(RelNode logicalPlan, ExecutionContext executionContext) {
        final LogicalCreateDatabase createDatabase = (LogicalCreateDatabase) logicalPlan;
        final SqlCreateDatabase sqlCreateDatabase = (SqlCreateDatabase) createDatabase.getNativeSqlNode();
        final LocalityManager lm = LocalityManager.getInstance();

        String dbName = sqlCreateDatabase.getDbName().getSimple();
        if (!DbNameUtil.validateDbName(dbName, KeyWordsUtil.isKeyWord(dbName))) {
            throw new TddlRuntimeException(ErrorCode.ERR_EXECUTOR,
                String.format("Failed to create database because the dbName[%s] is invalid", dbName));
        }

        int normalDbCnt = DbTopologyManager.getNormalDbCountFromMetaDb();
        int maxDbCnt = DbTopologyManager.maxLogicalDbCount;
        if (normalDbCnt >= maxDbCnt) {
            throw new TddlRuntimeException(ErrorCode.ERR_EXECUTOR,
                String.format(
                    "Failed to create database because there are too many databases, the max count of database is %s",
                    maxDbCnt));
        }

        DdlCharsetInfo createDbCharInfo = fetchCreateDbCharsetInfo(executionContext, sqlCreateDatabase);
        Boolean encryption = sqlCreateDatabase.isEncryption();

        String locality = Strings.nullToEmpty(sqlCreateDatabase.getLocality());
        String partitionMode = Strings.nullToEmpty(sqlCreateDatabase.getPartitionMode());
        boolean isCreateIfNotExists = sqlCreateDatabase.isIfNotExists();
        Long socketTimeout = executionContext.getParamManager().getLong(ConnectionParams.SOCKET_TIMEOUT);
        long socketTimeoutVal = socketTimeout == null ? -1 : socketTimeout;

        String shardDbCountEachStorageInstStr =
            (String) executionContext.getExtraCmds()
                .get(ConnectionProperties.SHARD_DB_COUNT_EACH_STORAGE_INST_FOR_STMT);
        int shardDbCountEachStorageInst = -1;
        if (shardDbCountEachStorageInstStr != null) {
            shardDbCountEachStorageInst = Integer.valueOf(shardDbCountEachStorageInstStr);
        }

        // choose dn by locality
        LocalityDesc localityDesc = LocalityInfoUtils.parse(locality);
//        if (!localityDesc.holdEmptyDnList() && !localityDesc.getDnList()
//            .contains(DbTopologyManager.singleGroupStorageInstList.get(0))) {
//            throw new TddlRuntimeException(ErrorCode.ERR_GMS_GENERIC,
//                String.format("Failed to create database because the locality[%s] is invalid, storage 0 is required",
//                    localityDesc.toString()));
//        }
        final LocalityDesc finalLocalityDesc;
        if (StoragePoolManager.getInstance().isTriggered() && localityDesc.holdEmptyDnList()) {
            finalLocalityDesc = StoragePoolManager.getInstance().getDefaultStoragePoolLocalityDesc();
        } else {
            finalLocalityDesc = localityDesc;
        }
        Predicate<StorageInfoRecord> predLocality =
            (x -> finalLocalityDesc.fullMatchStorageInstance(x.getInstanceId()));
        int dbType = decideDbType(partitionMode, executionContext);

        Boolean defaultSingle = sqlCreateDatabase.isDefaultSingle();
        boolean databaseDefaultSingle =
            executionContext.getParamManager().getBoolean(ConnectionParams.DATABASE_DEFAULT_SINGLE);
        if (dbType == DbInfoRecord.DB_TYPE_NEW_PART_DB) {
            if (defaultSingle == null) {
                if (databaseDefaultSingle) {
                    defaultSingle = new Boolean(true);
                }
            }
        } else {
            defaultSingle = false;
        }

        CreateDbInfo createDbInfo = DbTopologyManager.initCreateDbInfo(
            dbName, createDbCharInfo.finalCharset, createDbCharInfo.finalCollate, encryption, defaultSingle,
            finalLocalityDesc,
            predLocality,
            dbType,
            isCreateIfNotExists, socketTimeoutVal, shardDbCountEachStorageInst);
        long dbId = DbTopologyManager.createLogicalDb(createDbInfo);
        DbEventUtil.logFirstAutoDbCreationEvent(createDbInfo);
        CdcManagerHelper.getInstance()
            .notifyDdl(dbName, null, sqlCreateDatabase.getKind().name(), executionContext.getOriginSql(),
                DdlVisibility.Public, buildExtendParameter(executionContext));

        if (!finalLocalityDesc.holdEmptyDnList()) {
            lm.setLocalityOfDb(dbId, finalLocalityDesc.toString());
        }
        return new AffectRowCursor(new int[] {1});
    }

    @NotNull
    private DdlCharsetInfo fetchCreateDbCharsetInfo(ExecutionContext executionContext,
                                                    SqlCreateDatabase sqlCreateDatabase) {
        boolean useMySql80 = ExecUtils.isMysql80Version();
        DdlCharsetInfo serverCharInfo = DdlCharsetInfoUtil.fetchServerDefaultCharsetInfo(executionContext, useMySql80);
        DdlCharsetInfo createDbCharInfo =
            DdlCharsetInfoUtil.decideDdlCharsetInfo(executionContext, serverCharInfo.finalCharset,
                serverCharInfo.finalCollate, sqlCreateDatabase.getCharSet(), sqlCreateDatabase.getCollate(),
                useMySql80);
        return createDbCharInfo;
    }

    protected int decideDbType(String partitionMode, ExecutionContext executionContext) {

        String defaultPartMode = DbTopologyManager.getDefaultPartitionMode();
        int dbType = -1;
        if (StringUtils.isEmpty(partitionMode)) {
            partitionMode = defaultPartMode;
        }
        if (partitionMode.equalsIgnoreCase(DbInfoManager.MODE_AUTO) || partitionMode.equalsIgnoreCase(
            DbInfoManager.MODE_PARTITIONING)) {
            dbType = DbInfoRecord.DB_TYPE_NEW_PART_DB;
        } else if (partitionMode.equalsIgnoreCase(DbInfoManager.MODE_DRDS) || partitionMode.equalsIgnoreCase(
            DbInfoManager.MODE_SHARDING)) {
            dbType = DbInfoRecord.DB_TYPE_PART_DB;
        } else {
            throw new TddlRuntimeException(ErrorCode.ERR_EXECUTOR,
                "Failed to create database with invalid mode=" + partitionMode);
        }
        return dbType;
    }

}
