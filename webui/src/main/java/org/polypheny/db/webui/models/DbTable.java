/*
 * Copyright 2019-2021 The Polypheny Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.polypheny.db.webui.models;


import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import lombok.Getter;
import org.polypheny.db.catalog.Catalog.TableType;
import org.polypheny.db.catalog.entity.CatalogTrigger;


/**
 * Model for a table of a database
 */
public class DbTable {

    @Getter
    private final String tableName;
    @Getter
    private final String schema;
    private final ArrayList<DbColumn> columns = new ArrayList<>();
    private final ArrayList<String> primaryKeyFields = new ArrayList<>();
    private final ArrayList<String> uniqueColumns = new ArrayList<>();
    private final ArrayList<String> triggers = new ArrayList<>();
    private final boolean modifiable;
    private final String tableType;


    /**
     * Constructor for DbTable
     *
     * @param tableName name of the table
     * @param schema name of the schema this table belongs to
     * @param modifiable If the table is modifiable
     * @param tableType TableType (see Catalog)
     */
    public DbTable( final String tableName, final String schema, final boolean modifiable, final TableType tableType ) {
        this.tableName = tableName;
        this.schema = schema;
        this.modifiable = modifiable;
        this.tableType = tableType.toString();
    }


    /**
     * Add a column to a table when building the DbTable object
     *
     * @param col column that is part of this table
     */
    public void addColumn( final DbColumn col ) {
        this.columns.add( col );
    }


    /**
     * Add a primary key column (multiple if composite PK) when building the DbTable object
     */
    public void addPrimaryKeyField( final String columnName ) {
        this.primaryKeyFields.add( columnName );
    }


    /**
     * Add a column to the unique Columns list
     */
    public void addUniqueColumn( final String uniqueColumn ) {
        this.uniqueColumns.add( uniqueColumn );
    }

    /**
     * Add a trigger to the trigger list
     */
    public void addTriggers(final Collection<CatalogTrigger> triggers) {
        List<String> eventNames = triggers.stream()
                .map(CatalogTrigger::getEvent)
                .map(Enum::name)
                .collect(Collectors.toList());
        this.triggers.addAll(eventNames);
    }

}
