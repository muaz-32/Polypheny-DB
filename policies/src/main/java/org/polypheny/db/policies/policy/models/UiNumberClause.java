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

package org.polypheny.db.policies.policy.models;

import java.util.HashMap;
import java.util.List;
import org.polypheny.db.policies.policy.Clause.Category;
import org.polypheny.db.policies.policy.Clause.ClauseName;
import org.polypheny.db.policies.policy.Clause.ClauseType;
import org.polypheny.db.policies.policy.Policy.Target;
import org.polypheny.db.util.Pair;

public class UiNumberClause extends UiClause {

    private final int value;

    private final HashMap<Category, Pair<Integer, Integer>> categoryRange;


    public UiNumberClause( ClauseName clauseName, int id, boolean isDefault, ClauseType clauseType, Category category, String description, List<Target> possibleTargets, int value, HashMap<Category, Pair<Integer, Integer>> categoryRange ) {
        super( clauseName, id, isDefault, clauseType, category, description, possibleTargets );
        this.value = value;
        this.categoryRange = categoryRange;
    }

}
