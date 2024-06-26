/*
 * Copyright 1999-2017 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.polardbx.druid.sql.ast;

import com.alibaba.polardbx.druid.sql.ast.statement.SQLTableElement;
import com.alibaba.polardbx.druid.sql.visitor.SQLASTVisitor;

import java.util.ArrayList;
import java.util.List;

public class SQLDeclareItem extends SQLObjectImpl implements SQLObjectWithDataType, SQLReplaceable {

    protected Type type;

    protected SQLName name;

    protected SQLDataType dataType;

    protected SQLExpr value;

    protected List<SQLTableElement> tableElementList = new ArrayList<SQLTableElement>();

    protected transient SQLObject resolvedObject;

    public SQLDeclareItem() {

    }

    public SQLDeclareItem(SQLName name, SQLDataType dataType) {
        this.setName(name);
        this.setDataType(dataType);
    }

    public SQLDeclareItem(SQLName name, SQLDataType dataType, SQLExpr value) {
        this.setName(name);
        this.setDataType(dataType);
        this.setValue(value);
    }

    public boolean replace(SQLExpr expr, SQLExpr target) {
        if (name == expr) {
            setName((SQLName) target);
            return true;
        }

        if (value == expr) {
            setValue(target);
            return true;
        }

        return false;
    }

    @Override
    protected void accept0(SQLASTVisitor visitor) {
        if (visitor.visit(this)) {
            acceptChild(visitor, this.name);
            acceptChild(visitor, this.dataType);
            acceptChild(visitor, this.value);
            acceptChild(visitor, this.tableElementList);
        }
        visitor.endVisit(this);
    }

    public SQLName getName() {
        return name;
    }

    public void setName(SQLName name) {
        if (name != null) {
            name.setParent(this);
        }
        this.name = name;
    }

    public SQLDataType getDataType() {
        return dataType;
    }

    public void setDataType(SQLDataType dataType) {
        if (dataType != null) {
            dataType.setParent(this);
        }
        this.dataType = dataType;
    }

    public SQLExpr getValue() {
        return value;
    }

    public void setValue(SQLExpr value) {
        if (value != null) {
            value.setParent(this);
        }
        this.value = value;
    }

    public List<SQLTableElement> getTableElementList() {
        return tableElementList;
    }

    public void setTableElementList(List<SQLTableElement> tableElementList) {
        this.tableElementList = tableElementList;
    }

    public enum Type {
        TABLE, LOCAL, CURSOR;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public SQLObject getResolvedObject() {
        return resolvedObject;
    }

    public void setResolvedObject(SQLObject resolvedObject) {
        this.resolvedObject = resolvedObject;
    }

    @Override
    public SQLObject clone() {
        SQLDeclareItem x = new SQLDeclareItem();
        x.type = this.type;

        if (this.name != null) {
            x.name = this.name.clone();
            x.name.setParent(x);
        }

        if (this.dataType != null) {
            x.dataType = this.dataType.clone();
            x.dataType.setParent(x);
        }

        if (this.value != null) {
            x.value = this.value.clone();
            x.value.setParent(x);
        }

        if (this.resolvedObject != null) {
            x.resolvedObject = this.resolvedObject.clone();
            x.resolvedObject.setParent(x);
        }

        for (SQLTableElement element : this.tableElementList) {
            SQLTableElement newElement = element.clone();
            x.tableElementList.add(newElement);
        }

        return x;
    }
}
