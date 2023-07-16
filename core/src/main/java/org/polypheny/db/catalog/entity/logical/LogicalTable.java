/*
 * Copyright 2019-2023 The Polypheny Project
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

package org.polypheny.db.catalog.entity.logical;


import io.activej.serializer.annotations.Deserialize;
import io.activej.serializer.annotations.Serialize;
import io.activej.serializer.annotations.SerializeNullable;
import java.io.Serializable;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.experimental.SuperBuilder;
import org.apache.calcite.linq4j.tree.Expression;
import org.apache.calcite.linq4j.tree.Expressions;
import org.polypheny.db.algebra.type.AlgDataType;
import org.polypheny.db.algebra.type.AlgDataTypeFactory;
import org.polypheny.db.algebra.type.AlgDataTypeImpl;
import org.polypheny.db.catalog.Catalog;
import org.polypheny.db.catalog.logistic.EntityType;
import org.polypheny.db.catalog.logistic.NamespaceType;
import org.polypheny.db.schema.ColumnStrategy;

@EqualsAndHashCode(callSuper = false)
@SuperBuilder(toBuilder = true)
@Value
@NonFinal
public class LogicalTable extends LogicalEntity {

    private static final long serialVersionUID = 4653390333258552102L;


    @Serialize
    @SerializeNullable
    public Long primaryKey;

    public LogicalTable(
            @Deserialize("id") final long id,
            @Deserialize("name") @NonNull final String name,
            @Deserialize("namespaceId") final long namespaceId,
            @Deserialize("entityType") @NonNull final EntityType type,
            @Deserialize("primaryKey") final Long primaryKey,
            @Deserialize("modifiable") boolean modifiable ) {
        super( id, name, namespaceId, type, NamespaceType.RELATIONAL, modifiable );
        this.primaryKey = primaryKey;

        if ( type == EntityType.ENTITY && !modifiable ) {
            throw new RuntimeException( "Tables of table type TABLE must be modifiable!" );
        }
    }

    public String getOwnerName() {
        // TODO DL: implement at some point
        return Catalog.USER_NAME;
    }

    public String getDatabaseName() {
        // TODO DL: implement at some point
        return Catalog.DATABASE_NAME;
    }


    // Used for creating ResultSets
    @Override
    public Serializable[] getParameterArray() {
        return new Serializable[]{
                "APP",
                getNamespaceName(),
                name,
                entityType.name(),
                "",
                null,
                null,
                null,
                null,
                null,
                "pa"

        };
    }



    @Override
    public AlgDataType getRowType() {
        final AlgDataTypeFactory.Builder fieldInfo = AlgDataTypeFactory.DEFAULT.builder();

        for ( LogicalColumn column : Catalog.getInstance().getSnapshot().rel().getColumns( id ).stream().sorted( Comparator.comparingInt( a -> a.position ) ).collect( Collectors.toList() ) ) {
            AlgDataType sqlType = column.getAlgDataType( AlgDataTypeFactory.DEFAULT );
            fieldInfo.add( column.name, null, sqlType ).nullable( column.nullable );
        }

        return AlgDataTypeImpl.proto( fieldInfo.build() ).apply( AlgDataTypeFactory.DEFAULT );
    }


    @Override
    public Expression asExpression() {
        return Expressions.call( Expressions.call( Catalog.class, "getInstance" ), "getTable", Expressions.constant( id ) );
    }


    public List<ColumnStrategy> getColumnStrategies() {
        return getColumns().stream().map( c -> c.nullable ? ColumnStrategy.NULLABLE : ColumnStrategy.NOT_NULLABLE ).collect( Collectors.toList() );
    }


    public List<LogicalColumn> getColumns() {
        return Catalog.getInstance().getSnapshot().rel().getColumns( id );
    }


    public List<Long> getColumnIds() {
        return getColumns().stream().sorted( Comparator.comparingInt( a -> a.position ) ).map( c -> c.id ).collect( Collectors.toList() );
    }


    public List<String> getColumnNames() {
        return getColumns().stream().map( c -> c.name ).collect( Collectors.toList() );
    }


    @Override
    public String getNamespaceName() {
        return Catalog.getInstance().getSnapshot().getNamespace( namespaceId ).name;
    }


    public List<Long> getConstraintIds() {
        return List.of();
    }


    @RequiredArgsConstructor
    public static class PrimitiveCatalogTable {

        public final String tableCat;
        public final String tableSchem;
        public final String tableName;
        public final String tableType;
        public final String remarks;
        public final String typeCat;
        public final String typeSchem;
        public final String typeName;
        public final String selfReferencingColName;
        public final String refGeneration;
        public final String owner;

    }


    @Override
    public String toString() {
        return "LogicalTable{" +
                "primaryKey=" + primaryKey +
                ", id=" + id +
                ", entityType=" + entityType +
                ", namespaceType=" + namespaceType +
                ", name='" + name + '\'' +
                '}';
    }

}
