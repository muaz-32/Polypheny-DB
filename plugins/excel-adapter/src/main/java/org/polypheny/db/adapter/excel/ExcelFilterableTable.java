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

package org.polypheny.db.adapter.excel;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.calcite.linq4j.AbstractEnumerable;
import org.apache.calcite.linq4j.Enumerable;
import org.apache.calcite.linq4j.Enumerator;
import org.polypheny.db.adapter.DataContext;
import org.polypheny.db.algebra.constant.Kind;
import org.polypheny.db.algebra.type.AlgProtoDataType;
import org.polypheny.db.catalog.entity.physical.PhysicalTable;
import org.polypheny.db.rex.RexCall;
import org.polypheny.db.rex.RexIndexRef;
import org.polypheny.db.rex.RexLiteral;
import org.polypheny.db.rex.RexNode;
import org.polypheny.db.schema.types.FilterableEntity;
import org.polypheny.db.type.entity.PolyValue;
import org.polypheny.db.util.Source;

public class ExcelFilterableTable extends ExcelTable implements FilterableEntity {

    /**
     * Creates a ExcelFilterableTable.
     */


    public ExcelFilterableTable( PhysicalTable table, Source source, AlgProtoDataType protoRowType, List<ExcelFieldType> fieldTypes, int[] fields, ExcelSource excelSource ) {
        this( table, source, protoRowType, fieldTypes, fields, excelSource, "" );
    }


    public ExcelFilterableTable( PhysicalTable table, Source source, AlgProtoDataType protoRowType, List<ExcelFieldType> fieldTypes, int[] fields, ExcelSource excelSource, String sheet ) {
        super( table, source, protoRowType, fieldTypes, fields, excelSource, sheet );
    }


    public String toString() {
        return "ExcelFilterableTable";
    }


    @Override
    public Enumerable<PolyValue[]> scan( DataContext dataContext, List<RexNode> filters ) {
        dataContext.getStatement().getTransaction().registerInvolvedAdapter( excelSource );
        final String[] filterValues = new String[fieldTypes.size()];
        filters.removeIf( filter -> addFilter( filter, filterValues ) );
        final AtomicBoolean cancelFlag = DataContext.Variable.CANCEL_FLAG.get( dataContext );
        return new AbstractEnumerable<PolyValue[]>() {
            @Override
            public Enumerator<PolyValue[]> enumerator() {
                return new ExcelEnumerator( source, cancelFlag, false, filterValues, new ExcelEnumerator.ArrayRowConverter( fieldTypes, fields ), sheet );
            }
        };
    }


    private boolean addFilter( RexNode filter, Object[] filterValues ) {
        if ( filter.isA( Kind.EQUALS ) ) {
            final RexCall call = (RexCall) filter;
            RexNode left = call.getOperands().get( 0 );
            if ( left.isA( Kind.CAST ) ) {
                left = ((RexCall) left).operands.get( 0 );
            }
            final RexNode right = call.getOperands().get( 1 );
            if ( left instanceof RexIndexRef && right instanceof RexLiteral ) {
                final int index = ((RexIndexRef) left).getIndex();
                if ( filterValues[index] == null ) {
                    filterValues[index] = ((RexLiteral) right).getValue().toString();
                    return true;
                }
            }
        }
        return false;
    }

}

