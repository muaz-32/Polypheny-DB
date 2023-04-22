/*
 * Copyright 2019-2022 The Polypheny Project
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

package org.polypheny.db.view;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import org.polypheny.db.adapter.DataStore;
import org.polypheny.db.algebra.AlgNode;
import org.polypheny.db.algebra.AlgRoot;
import org.polypheny.db.algebra.AlgShuttleImpl;
import org.polypheny.db.algebra.core.common.Modify;
import org.polypheny.db.algebra.logical.relational.LogicalRelModify;
import org.polypheny.db.catalog.entity.MaterializedCriteria;
import org.polypheny.db.catalog.entity.allocation.AllocationEntity;
import org.polypheny.db.catalog.entity.logical.LogicalMaterializedView;
import org.polypheny.db.catalog.entity.physical.PhysicalEntity;
import org.polypheny.db.transaction.PolyXid;
import org.polypheny.db.transaction.Transaction;


public abstract class MaterializedViewManager {

    public static MaterializedViewManager INSTANCE = null;
    public boolean isDroppingMaterialized = false;
    public boolean isCreatingMaterialized = false;
    public boolean isUpdatingMaterialized = false;


    public static MaterializedViewManager setAndGetInstance( MaterializedViewManager transaction ) {
        if ( INSTANCE != null ) {
            throw new RuntimeException( "Overwriting the MaterializedViewManager is not permitted." );
        }
        INSTANCE = transaction;
        return INSTANCE;
    }


    public static MaterializedViewManager getInstance() {
        if ( INSTANCE == null ) {
            throw new RuntimeException( "MaterializedViewManager was not set correctly on Polypheny-DB start-up" );
        }
        return INSTANCE;
    }


    public abstract void deleteMaterializedViewFromInfo( Long tableId );

    public abstract void addData(
            Transaction transaction,
            List<DataStore<?>> stores,
            AlgRoot algRoot,
            LogicalMaterializedView materializedView );

    public abstract void addTables( Transaction transaction, List<Long> ids );

    public abstract void updateData( Transaction transaction, long viewId );

    public abstract void updateCommittedXid( PolyXid xid );

    public abstract void updateMaterializedTime( Long materializedId );

    public abstract void addMaterializedInfo( Long materializedId, MaterializedCriteria matViewCriteria );


    /**
     * to track updates on tables for materialized views with update freshness
     */
    public static class TableUpdateVisitor extends AlgShuttleImpl {

        @Getter
        private final List<Long> ids = new ArrayList<>();


        @Override
        public AlgNode visit( LogicalRelModify modify ) {
            if ( modify.getOperation() != Modify.Operation.MERGE ) {
                if ( (modify.getEntity() != null) ) {
                    if ( modify.getEntity().unwrap( PhysicalEntity.class ) != null ) {
                        ids.add( modify.getEntity().unwrap( PhysicalEntity.class ).id );
                    } else if ( modify.getEntity().unwrap( AllocationEntity.class ) != null ) {
                        ids.add( modify.getEntity().unwrap( AllocationEntity.class ).getLogicalId() );
                    } else {
                        ids.add( modify.getEntity().id );
                    }
                }
            }
            return super.visit( modify );
        }


    }


}
