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

package org.polypheny.db.adapter.cottontail.rules;

import com.google.common.collect.ImmutableList;
import java.util.List;
import org.polypheny.db.algebra.core.AlgFactories;
import org.polypheny.db.plan.AlgOptRule;
import org.polypheny.db.tools.AlgBuilderFactory;


public class CottontailRules {

    public static List<AlgOptRule> rules() {
        return rules( AlgFactories.LOGICAL_BUILDER );
    }


    public static List<AlgOptRule> rules( AlgBuilderFactory algBuilderFactory ) {
        return ImmutableList.of(
                new CottontailToEnumerableConverterRule( algBuilderFactory ),
                new CottontailValuesRule( algBuilderFactory ),
                new CottontailTableModificationRule( algBuilderFactory ),
                new CottontailProjectRule( algBuilderFactory ),
                new CottontailFilterRule( algBuilderFactory ),
                new CottontailSortRule( algBuilderFactory )
        );
    }

}
