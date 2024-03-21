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

package org.polypheny.db.algebra.polyalg;

import com.google.common.collect.ImmutableList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.polypheny.db.algebra.AlgNode;
import org.polypheny.db.algebra.core.JoinAlgType;
import org.polypheny.db.algebra.core.relational.RelScan;
import org.polypheny.db.algebra.logical.relational.*;
import org.polypheny.db.algebra.polyalg.PolyAlgDeclaration.OperatorTag;
import org.polypheny.db.algebra.polyalg.PolyAlgDeclaration.ParamType;
import org.polypheny.db.algebra.polyalg.PolyAlgDeclaration.Parameter;

public class PolyAlgRegistry {

    private static final Map<Class<? extends AlgNode>, PolyAlgDeclaration> declarations = new HashMap<>();
    private static final Map<String, Class<? extends AlgNode>> classes = new HashMap<>();


    static {
        populateDeclarationsMap();
        populateClassesMap();
    }


    private static void populateDeclarationsMap() {
        ImmutableList<OperatorTag> logRelTags = ImmutableList.of( OperatorTag.LOGICAL, OperatorTag.REL );

        declarations.put( LogicalRelProject.class, PolyAlgDeclaration.builder()
                .opName( "PROJECT" ).opAlias( "P" ).numInputs( 1 ).opTags( logRelTags )
                .param( Parameter.builder().name( "projects" ).isMultiValued( true ).type( ParamType.SIMPLE_REX ).build() )
                .build() );

        declarations.put( RelScan.class, PolyAlgDeclaration.builder()
                .opName( "SCAN" ).numInputs( 0 ).opTags( logRelTags )
                .param( Parameter.builder().name( "entity" ).type( ParamType.SIMPLE_REX ).build() )
                .build() );

        declarations.put( LogicalRelFilter.class, PolyAlgDeclaration.builder()
                .opName( "FILTER" ).numInputs( 1 ).opTags( logRelTags )
                .param( Parameter.builder().name( "condition" ).type( ParamType.SIMPLE_REX ).build() )
                .build() );

        declarations.put( LogicalRelAggregate.class, PolyAlgDeclaration.builder()
                .opName( "AGG" ).numInputs( 1 ).opTags( logRelTags )
                .param( Parameter.builder().name( "group" ).type( ParamType.FIELD ).isMultiValued( true ).build() )
                .param( Parameter.builder().name( "groups" ).type( ParamType.ANY ).isMultiValued( true ).defaultValue( "" ).build() )
                .param( Parameter.builder().name( "aggs" ).type( ParamType.AGGREGATE ).isMultiValued( true ).defaultValue( "" ).build() )
                .build() );

        declarations.put( LogicalRelMinus.class, PolyAlgDeclaration.builder()
                .opName( "MINUS" ).numInputs( 2 ).opTags( logRelTags )
                .param( Parameter.builder().name( "all" ).type( ParamType.BOOLEAN ).defaultValue( "FALSE" ).build() )
                .build() );

        declarations.put( LogicalRelUnion.class, PolyAlgDeclaration.builder()
                .opName( "UNION" ).numInputs( 2 ).opTags( logRelTags )
                .param( Parameter.builder().name( "all" ).type( ParamType.BOOLEAN ).defaultValue( "FALSE" ).build() )
                .build() );

        declarations.put( LogicalRelIntersect.class, PolyAlgDeclaration.builder()
                .opName( "INTERSECT" ).numInputs( 2 ).opTags( logRelTags )
                .param( Parameter.builder().name( "all" ).type( ParamType.BOOLEAN ).defaultValue( "FALSE" ).build() )
                .build() );

        declarations.put( LogicalRelSort.class, PolyAlgDeclaration.builder()
                .opName( "SORT" ).numInputs( 1 ).opTags( logRelTags )
                .param( Parameter.builder().name( "sort" ).aliases( List.of( "collation", "order" ) ).type( ParamType.COLLATION ).defaultValue( "" ).build() )
                .param( Parameter.builder().name( "limit" ).alias( "fetch" ).type( ParamType.SIMPLE_REX ).defaultValue( "" ).build() )
                .param( Parameter.builder().name( "offset" ).type( ParamType.SIMPLE_REX ).defaultValue( "" ).build() )
                .build() );

        declarations.put( LogicalRelJoin.class, PolyAlgDeclaration.builder()
                .opName( "JOIN" ).numInputs( 2 ).opTags( logRelTags )
                .param( Parameter.builder().name( "condition" ).type( ParamType.SIMPLE_REX ).build() )
                .param( Parameter.builder().name( "type" ).type( ParamType.JOIN_TYPE_ENUM ).defaultValue( JoinAlgType.INNER.name() ).build() )
                .param( Parameter.builder().name( "semiJoinDone" ).type( ParamType.BOOLEAN ).defaultValue( "FALSE" ).build() )
                .build() );

        declarations.put( LogicalCalc.class, PolyAlgDeclaration.builder()
                .opName( "CALC" ).numInputs( 1 ).opTags( logRelTags )
                .param( Parameter.builder().name( "exps" ).type( ParamType.SIMPLE_REX ).isMultiValued( true ).build() )
                .param( Parameter.builder().name( "projects" ).type( ParamType.SIMPLE_REX ).isMultiValued( true ).build() )
                .param( Parameter.builder().name( "condition" ).type( ParamType.SIMPLE_REX ).defaultValue( "" ).build() )
                .build() );

        /*
        declarations.put( .class, PolyAlgDeclaration.builder()
                .opName( "" ).numInputs(  ).opTags( logRelTags )
                .param( Parameter.builder().name( "" ).type( ParamType. ).build() )
                .build() );
        */

    }


    private static void populateClassesMap() {
        for ( Map.Entry<Class<? extends AlgNode>, PolyAlgDeclaration> entry : declarations.entrySet() ) {
            Class<? extends AlgNode> clazz = entry.getKey();
            PolyAlgDeclaration declaration = entry.getValue();

            assert !classes.containsKey( declaration.opName );
            classes.put( declaration.opName, clazz );
            for ( String alias : declaration.opAliases ) {
                assert !classes.containsKey( alias );
                classes.put( alias, clazz );
            }
        }
    }


    public static PolyAlgDeclaration getDeclaration( Class<? extends AlgNode> clazz ) {
        return getDeclaration( clazz, 0 );
    }


    /**
     * Retrieves the PolyAlgDeclaration associated with the specified class from a map of declarations,
     * or returns a default PolyAlgDeclaration if none is found.
     *
     * @param clazz The class for which the PolyAlgDeclaration is being retrieved
     * @param numInputs The number of inputs associated with the PolyAlgDeclaration if a new one is created.
     * @return The PolyAlgDeclaration associated with the specified class if found in the map,
     * or a new PolyAlgDeclaration initialized with the class name and the specified number of inputs.
     */
    public static PolyAlgDeclaration getDeclaration( Class<? extends AlgNode> clazz, int numInputs ) {
        return declarations.getOrDefault(
                clazz,
                PolyAlgDeclaration.builder().opName( clazz.getSimpleName() ).numInputs( numInputs ).build() );
    }


    public static Class<? extends AlgNode> getClass( String opName ) {
        return classes.get( opName );
    }


    /**
     * Retrieves the PolyAlgDeclaration associated with the specified operator
     * or returns null if the operator is not known.
     * It is also possible to use an alias for the operator name.
     *
     * @param opName The name of the operator or one of its aliases
     * @return The PolyAlgDeclaration associated with the opName if it exists,
     * or null otherwise.
     */
    public static PolyAlgDeclaration getDeclaration( String opName ) {
        return declarations.get( getClass( opName ) );
    }

}
