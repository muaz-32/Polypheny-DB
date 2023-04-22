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
 *
 * This file incorporates code covered by the following terms:
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.polypheny.db.schema.impl;


import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import org.apache.calcite.linq4j.tree.Expression;
import org.polypheny.db.algebra.type.AlgProtoDataType;
import org.polypheny.db.catalog.entity.CatalogEntity;
import org.polypheny.db.catalog.snapshot.Snapshot;
import org.polypheny.db.plan.Convention;
import org.polypheny.db.schema.Function;
import org.polypheny.db.schema.Namespace;
import org.polypheny.db.schema.SchemaVersion;
import org.polypheny.db.schema.Schemas;


/**
 * Abstract implementation of {@link Namespace}.
 *
 * <p>Behavior is as follows:</p>
 * <ul>
 * <li>The schema has no tables unless you override {@link #getTables()}.</li>
 * <li>The schema has no functions unless you override {@link #getFunctionMultimap()}.</li>
 * <li>The schema has no sub-schemas unless you override {@link #getSubSchemaMap()}.</li>
 * <li>The schema is mutable unless you override {@link #isMutable()}.</li>
 * <li>The name and parent schema are as specified in the constructor arguments.</li>
 * </ul>
 */
public class AbstractNamespace implements Namespace {

    @Getter
    public final long id;


    public AbstractNamespace( long id ) {
        this.id = id;
    }


    @Override
    public boolean isMutable() {
        return true;
    }


    @Override
    public Namespace snapshot( SchemaVersion version ) {
        return this;
    }


    @Override
    public Convention getConvention() {
        return null;
    }


    @Override
    public Expression getExpression( Snapshot snapshot, long id ) {
        return Schemas.subSchemaExpression( null, id, null, getClass() );
    }


    /**
     * Returns a map of tables in this schema by name.
     *
     * The implementations of {@link #getEntityNames()} and {@link #getEntity(String)} depend on this map.
     * The default implementation of this method returns the empty map.
     * Override this method to change their behavior.
     *
     * @return Map of tables in this schema by name
     */
    protected Map<String, CatalogEntity> getTables() {
        return ImmutableMap.of();
    }


    @Override
    public final Set<String> getEntityNames() {
        return getTables().keySet();
    }


    @Override
    public final CatalogEntity getEntity( String name ) {
        return getTables().get( name );
    }


    /**
     * Returns a map of types in this schema by name.
     *
     * The implementations of {@link #getTypeNames()} and {@link #getType(String)} depend on this map.
     * The default implementation of this method returns the empty map.
     * Override this method to change their behavior.
     *
     * @return Map of types in this schema by name
     */
    protected Map<String, AlgProtoDataType> getTypeMap() {
        return ImmutableMap.of();
    }


    @Override
    public AlgProtoDataType getType( String name ) {
        return getTypeMap().get( name );
    }


    @Override
    public Set<String> getTypeNames() {
        return getTypeMap().keySet();
    }


    /**
     * Returns a multi-map of functions in this schema by name.
     * It is a multi-map because functions are overloaded; there may be more than one function in a schema with a given
     * name (as long as they have different parameter lists).
     *
     * The implementations of {@link #getFunctionNames()} and {@link Namespace#getFunctions(String)} depend on this map.
     * The default implementation of this method returns the empty multi-map.
     * Override this method to change their behavior.
     *
     * @return Multi-map of functions in this schema by name
     */
    protected Multimap<String, Function> getFunctionMultimap() {
        return ImmutableMultimap.of();
    }


    @Override
    public final Collection<Function> getFunctions( String name ) {
        return getFunctionMultimap().get( name ); // never null
    }


    @Override
    public final Set<String> getFunctionNames() {
        return getFunctionMultimap().keySet();
    }


    /**
     * Returns a map of sub-schemas in this schema by name.
     *
     * The implementations of {@link #getSubNamespaceNames()} and {@link #getSubNamespace(String)} depend on this map.
     * The default implementation of this method returns the empty map.
     * Override this method to change their behavior.
     *
     * @return Map of sub-schemas in this schema by name
     */
    protected Map<String, Namespace> getSubSchemaMap() {
        return ImmutableMap.of();
    }


    @Override
    public final Set<String> getSubNamespaceNames() {
        return getSubSchemaMap().keySet();
    }


    @Override
    public final Namespace getSubNamespace( String name ) {
        return getSubSchemaMap().get( name );
    }

}

