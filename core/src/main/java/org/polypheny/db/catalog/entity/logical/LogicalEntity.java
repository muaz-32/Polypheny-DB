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
import io.activej.serializer.annotations.SerializeClass;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.experimental.SuperBuilder;
import org.polypheny.db.catalog.entity.CatalogEntity;
import org.polypheny.db.catalog.logistic.EntityType;
import org.polypheny.db.catalog.logistic.NamespaceType;

@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
@Value
@NonFinal
public abstract class LogicalEntity extends CatalogEntity {

    @Serialize
    public String namespaceName;


    public LogicalEntity(
            long id,
            String name,
            long namespaceId,
            String namespaceName,
            EntityType type,
            NamespaceType namespaceType ) {
        super( id, name, namespaceId, type, namespaceType );
        this.namespaceName = namespaceName;
    }


    public State getCatalogType() {
        return State.LOGICAL;
    }

}
