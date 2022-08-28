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

package org.polypheny.db.algebra.core;


import org.polypheny.db.algebra.AlgNode;
import org.polypheny.db.algebra.constant.Kind;
import org.polypheny.db.algebra.metadata.AlgMetadataQuery;
import org.polypheny.db.plan.AlgOptCluster;
import org.polypheny.db.plan.AlgTraitSet;

import java.util.List;
import java.util.stream.Collectors;


/**
 * Relational expression for build queries containing trigger
 */
public abstract class TriggerExecution extends SetOp {

    // TODO(Nic): Save NamedDynamicParam map

    protected TriggerExecution(AlgOptCluster cluster, AlgTraitSet traits, List<AlgNode> inputs, boolean all ) {
        super( cluster, traits, inputs, Kind.UNION, all );
        // TODO(Nic): Fill data context
    }


    @Override
    public double estimateRowCount( AlgMetadataQuery mq ) {
        return 1;
    }


    @Override
    public String algCompareString() {
        return this.getClass().getSimpleName() + "$" +
                inputs.stream().map( AlgNode::algCompareString ).collect( Collectors.joining( "$" ) ) + "$" +
                all + "&";
    }

}

