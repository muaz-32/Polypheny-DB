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

package org.polypheny.db.policies.policy;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import org.polypheny.db.policies.policy.Clause.Category;
import org.polypheny.db.policies.policy.Clause.ClauseName;

public class ClausesRegister {


    @Getter
    private static boolean isInit = false;

    @Getter
    private static final Map<ClauseName, Clause> registry = new HashMap<>();


    public static void registerClauses() {
        if ( isInit ) {
            throw new RuntimeException( "Clauses were already registered." );
        }
        isInit = true;

        register( ClauseName.FULLY_PERSISTENT,
                new BooleanClause( 
                        ClauseName.FULLY_PERSISTENT,
                        false, 
                        true,
                        Category.PERSISTENCY,
                        "If persistency is switched on, Polypheny only adds tables and partitions only to persistent stores."  ) 
        );

        register( ClauseName.ONLY_EMBEDDED,
                new BooleanClause(
                        ClauseName.ONLY_EMBEDDED,
                        false,
                        true,
                        Category.AVAILABILITY,
                        "If only embedded is switched on, Polypheny only adds tables and partitions to embedded store."  )
        );

    }


    private static void register( ClauseName name, Clause clause ) {
        registry.put( name, clause );
    }

}
