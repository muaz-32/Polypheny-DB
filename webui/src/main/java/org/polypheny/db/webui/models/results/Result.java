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

package org.polypheny.db.webui.models.results;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.io.IOException;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.experimental.SuperBuilder;
import org.polypheny.db.catalog.logistic.NamespaceType;
import org.polypheny.db.languages.QueryLanguage;


@Value
@NonFinal
@SuperBuilder
@AllArgsConstructor
public abstract class Result<E, F> {

    /**
     * namespace type of result DOCUMENT/RELATIONAL
     */
    public NamespaceType namespaceType;

    public String namespaceName;

    public E[] data;

    public F[] header;

    /**
     * Exception with additional information
     */
    public Throwable exception;

    public String query;


    /**
     * Transaction id, for the websocket. It will not be serialized to gson.
     */
    public transient String xid;

    /**
     * Error message if a query failed
     */
    public String error;
    /**
     * Information for the pagination: what current page is being displayed
     */
    public int currentPage;
    /**
     * Information for the pagination: how many pages there can be in total
     */
    public int highestPage;
    /**
     * Indicate that only a subset of the specified query is being displayed.
     */
    public boolean hasMore;
    /**
     * language type of result MQL/SQL/CQL
     */
    @JsonSerialize(using = LanguageSerializer.class)
    public QueryLanguage language = QueryLanguage.from( "sql" );


    /**
     * Remove when bugs in SuperBuilder regarding generics are fixed
     */
    public static abstract class ResultBuilder<E, F, C extends Result<E, F>, B extends ResultBuilder<E, F, C, B>> {

        protected B $fillValuesFrom( C instance ) {
            this.data = instance.data;
            this.namespaceType = instance.namespaceType;
            this.xid = instance.xid;
            this.error = instance.error;
            this.namespaceName = instance.namespaceName;
            this.query = instance.query;
            this.exception = instance.exception;

            return self();
        }

    }


    private static class LanguageSerializer extends JsonSerializer<QueryLanguage> {

        @Override
        public void serialize( QueryLanguage value, JsonGenerator gen, SerializerProvider serializers ) throws IOException {
            gen.writeString( value.getSerializedName() );
        }

    }

}
