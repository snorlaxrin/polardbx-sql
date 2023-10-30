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

package org.apache.calcite.sql;

import org.apache.calcite.sql.parser.SqlParserPos;
import org.apache.calcite.sql.util.SqlString;

import java.util.Collections;
import java.util.List;

/**
 * see com/alibaba/polardbx/druid/sql/dialect/mysql/parser/MySqlStatementParser.java:7484
 * key words: "ALLOCATE"
 *
 * @author guxu
 */
public class SqlAlterTableAllocateLocalPartition extends SqlAlterSpecification {

    private static final SqlOperator OPERATOR = new SqlSpecialOperator("ALLOCATE LOCAL PARTITION", SqlKind.ALLOCATE_LOCAL_PARTITION);

    protected SqlNode parent;

    public SqlAlterTableAllocateLocalPartition(SqlParserPos pos) {
        super(pos);
    }

    @Override
    public SqlOperator getOperator() {
        return OPERATOR;
    }

    @Override
    public List<SqlNode> getOperandList() {
        return Collections.emptyList();
    }

    public List<SqlNode> getPartitions() {
        return null;
    }

    public SqlNode getParent() {
        return parent;
    }

    public void setParent(SqlNode parent) {
        this.parent = parent;
    }

    @Override
    public String toString() {
        return "ALLOCATE LOCAL PARTITION";
    }

    @Override
    public SqlString toSqlString(SqlDialect dialect) {
        return new SqlString(dialect ,toString());
    }

    @Override
    public boolean supportFileStorage() { return true;}
}
