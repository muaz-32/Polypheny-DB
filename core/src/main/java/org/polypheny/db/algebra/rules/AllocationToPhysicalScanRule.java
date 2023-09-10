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

package org.polypheny.db.algebra.rules;

import org.polypheny.db.adapter.AdapterManager;
import org.polypheny.db.algebra.AlgNode;
import org.polypheny.db.algebra.core.AlgFactories;
import org.polypheny.db.algebra.core.common.Scan;
import org.polypheny.db.algebra.logical.document.LogicalDocumentScan;
import org.polypheny.db.algebra.logical.lpg.LogicalLpgScan;
import org.polypheny.db.algebra.logical.relational.LogicalRelScan;
import org.polypheny.db.algebra.type.AlgDataType;
import org.polypheny.db.catalog.entity.allocation.AllocationEntity;
import org.polypheny.db.catalog.exceptions.GenericRuntimeException;
import org.polypheny.db.plan.AlgOptRule;
import org.polypheny.db.plan.AlgOptRuleCall;
import org.polypheny.db.schema.trait.ModelTraitDef;
import org.polypheny.db.tools.AlgBuilder;

public class AllocationToPhysicalScanRule extends AlgOptRule {

    public static final AllocationToPhysicalScanRule REL_INSTANCE = new AllocationToPhysicalScanRule( LogicalRelScan.class );
    public static final AllocationToPhysicalScanRule DOC_INSTANCE = new AllocationToPhysicalScanRule( LogicalDocumentScan.class );
    public static final AllocationToPhysicalScanRule GRAPH_INSTANCE = new AllocationToPhysicalScanRule( LogicalLpgScan.class );


    public AllocationToPhysicalScanRule( Class<? extends Scan<?>> scan ) {
        super( operand( scan, any() ), AlgFactories.LOGICAL_BUILDER, AllocationToPhysicalScanRule.class.getSimpleName() + "_" + scan.getSimpleName() );
    }


    @Override
    public void onMatch( AlgOptRuleCall call ) {
        Scan<?> scan = call.alg( 0 );
        AllocationEntity alloc = scan.entity.unwrap( AllocationEntity.class );
        if ( alloc == null ) {
            return;
        }

        AlgNode newAlg;

        switch ( scan.entity.namespaceType ) {
            case RELATIONAL:
                newAlg = handleRelationalEntity( call, scan, alloc );
                break;
            case DOCUMENT:
                newAlg = handleDocumentEntity( call, scan, alloc );
                break;
            case GRAPH:
                newAlg = handleGraphEntity( call, scan, alloc );
                break;
            default:
                throw new GenericRuntimeException( "Could not transform allocation to physical" );
        }
        call.transformTo( newAlg );
    }


    private static AlgNode handleGraphEntity( AlgOptRuleCall call, Scan<?> scan, AllocationEntity alloc ) {
        AlgNode alg = AdapterManager.getInstance().getAdapter( alloc.adapterId ).getGraphScan( alloc.id, call.builder() );

        if ( scan.getModel() != scan.entity.namespaceType ) {
            // cross-model queries need a transformer first, we let another rule handle that
            alg = call.builder().push( alg ).transform( scan.getTraitSet().getTrait( ModelTraitDef.INSTANCE ), scan.getRowType(), true ).build();
        }
        return alg;
    }


    private static AlgNode handleDocumentEntity( AlgOptRuleCall call, Scan<?> scan, AllocationEntity alloc ) {
        AlgNode alg = AdapterManager.getInstance().getAdapter( alloc.adapterId ).getDocumentScan( alloc.id, call.builder() );

        if ( scan.getModel() != scan.entity.namespaceType ) {
            // cross-model queries need a transformer first, we let another rule handle that
            alg = call.builder().push( alg ).transform( scan.getTraitSet().getTrait( ModelTraitDef.INSTANCE ), scan.getRowType(), true ).build();
        }
        return alg;
    }


    private AlgNode handleRelationalEntity( AlgOptRuleCall call, Scan<?> scan, AllocationEntity alloc ) {
        AlgNode alg = AdapterManager.getInstance().getAdapter( alloc.adapterId ).getRelScan( alloc.id, call.builder() );
        if ( scan.getModel() == scan.entity.namespaceType ) {
            alg = attachReorder( alg, scan, call.builder() );
        }

        if ( scan.getModel() != scan.entity.namespaceType ) {
            // cross-model queries need a transformer first, we let another rule handle that
            alg = call.builder().push( alg ).transform( scan.getTraitSet().getTrait( ModelTraitDef.INSTANCE ), scan.getRowType(), true ).build();
        }
        return alg;
    }


    private AlgNode attachReorder( AlgNode newAlg, Scan<?> original, AlgBuilder builder ) {
        if ( newAlg.getRowType().equals( original.getRowType() ) ) {
            return newAlg;
        }
        builder.push( newAlg );
        AlgDataType originalType = original.getRowType();
        builder.reorder( originalType );
        return builder.build();
    }

}
