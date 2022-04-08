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

package org.polypheny.db.schema.graph;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.google.gson.annotations.Expose;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NonNull;
import org.apache.calcite.linq4j.tree.Expression;
import org.apache.calcite.linq4j.tree.Expressions;
import org.polypheny.db.adapter.enumerable.EnumUtils;
import org.polypheny.db.runtime.PolyCollections;
import org.polypheny.db.runtime.PolyCollections.PolyDictionary;
import org.polypheny.db.runtime.PolyCollections.PolyList;
import org.polypheny.db.tools.ExpressionTransformable;

@Getter
public class PolyEdge extends GraphPropertyHolder implements Comparable<PolyEdge>, ExpressionTransformable {

    @Expose
    public final String source;
    @Expose
    public final String target;
    @Expose
    public final EdgeDirection direction;


    public PolyEdge( @NonNull PolyCollections.PolyDictionary properties, List<String> labels, String source, String target, EdgeDirection direction ) {
        this( UUID.randomUUID().toString(), properties, labels, source, target, direction );
    }


    public PolyEdge( String id, @NonNull PolyCollections.PolyDictionary properties, List<String> labels, String source, String target, EdgeDirection direction ) {
        super( id, GraphObjectType.EDGE, properties, labels );
        this.source = source;
        this.target = target;
        this.direction = direction;
    }


    @Override
    public int compareTo( PolyEdge other ) {
        return id.compareTo( other.id );
    }


    public PolyEdge from( String left, String right ) {
        return new PolyEdge( id, properties, labels, left == null ? this.source : left, right == null ? this.target : right, direction );
    }


    @Override
    public void setLabels( List<String> labels ) {
        this.labels.clear();
        this.labels.add( labels.get( 0 ) );
    }


    @Override
    public Expression getAsExpression() {
        return Expressions.convert_(
                Expressions.new_(
                        PolyEdge.class,
                        Expressions.constant( id ),
                        properties.getAsExpression(),
                        EnumUtils.constantArrayList( labels, String.class ),
                        Expressions.constant( source ),
                        Expressions.constant( target ),
                        Expressions.constant( direction ) ),
                PolyEdge.class );
    }


    public enum EdgeDirection {
        LEFT_TO_RIGHT,
        RIGHT_TO_LEFT,
        NONE
    }


    @Override
    public String toString() {
        return "PolyEdge{" +
                "id=" + id +
                ", properties=" + properties +
                ", labels=" + labels +
                ", leftId=" + source +
                ", rightId=" + target +
                ", direction=" + direction +
                '}';
    }


    public static class PolyEdgeSerializer extends Serializer<PolyEdge> {

        @Override
        public void write( Kryo kryo, Output output, PolyEdge object ) {
            kryo.writeClassAndObject( output, object.id );
            kryo.writeClassAndObject( output, object.properties );
            kryo.writeClassAndObject( output, object.labels );
            kryo.writeClassAndObject( output, object.source );
            kryo.writeClassAndObject( output, object.target );
            kryo.writeClassAndObject( output, object.direction );
            kryo.writeClassAndObject( output, object.variableName() );
        }


        @Override
        public PolyEdge read( Kryo kryo, Input input, Class<? extends PolyEdge> type ) {
            String id = (String) kryo.readClassAndObject( input );
            PolyDictionary properties = (PolyDictionary) kryo.readClassAndObject( input );
            PolyList<String> labels = (PolyList<String>) kryo.readClassAndObject( input );
            String leftId = (String) kryo.readClassAndObject( input );
            String rightId = (String) kryo.readClassAndObject( input );
            EdgeDirection direction = (EdgeDirection) kryo.readClassAndObject( input );
            String variableName = (String) kryo.readClassAndObject( input );

            return (PolyEdge) new PolyEdge( id, properties, labels, leftId, rightId, direction ).variableName( variableName );
        }


    }

}
