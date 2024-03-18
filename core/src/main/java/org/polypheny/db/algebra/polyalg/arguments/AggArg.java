/*
 * Copyright 2019-2024 The Polypheny Project
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

package org.polypheny.db.algebra.polyalg.arguments;

import org.polypheny.db.algebra.AlgNode;
import org.polypheny.db.algebra.core.AggregateCall;
import org.polypheny.db.algebra.polyalg.PolyAlgDeclaration.ParamType;
import org.polypheny.db.algebra.polyalg.PolyAlgUtils;

public class AggArg implements PolyAlgArg {

    private final AggregateCall agg;
    private final AlgNode algNode;


    public AggArg( AggregateCall agg ) {
        this( agg, null );
    }


    public AggArg( AggregateCall agg, AlgNode fieldNameProvider ) {
        this.agg = agg;
        this.algNode = fieldNameProvider;
    }


    @Override
    public ParamType getType() {
        return ParamType.AGGREGATE;
    }


    @Override
    public String toPolyAlg() {
        String str = agg.toString();
        if ( algNode != null ) {
            str = PolyAlgUtils.replaceWithFieldNames( algNode, str );
        }
        return PolyAlgUtils.appendAlias( str, agg.getName() );
    }

}