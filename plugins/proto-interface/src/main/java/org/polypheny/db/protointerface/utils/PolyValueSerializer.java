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

package org.polypheny.db.protointerface.utils;

import com.google.protobuf.ByteString;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import org.apache.commons.lang3.NotImplementedException;
import org.polypheny.db.protointerface.proto.ProtoBigDecimal;
import org.polypheny.db.protointerface.proto.ProtoBinary;
import org.polypheny.db.protointerface.proto.ProtoBoolean;
import org.polypheny.db.protointerface.proto.ProtoDate;
import org.polypheny.db.protointerface.proto.ProtoDocument;
import org.polypheny.db.protointerface.proto.ProtoDouble;
import org.polypheny.db.protointerface.proto.ProtoEdge;
import org.polypheny.db.protointerface.proto.ProtoEntry;
import org.polypheny.db.protointerface.proto.ProtoFloat;
import org.polypheny.db.protointerface.proto.ProtoGraph;
import org.polypheny.db.protointerface.proto.ProtoGraphPropertyHolder;
import org.polypheny.db.protointerface.proto.ProtoInteger;
import org.polypheny.db.protointerface.proto.ProtoInterval;
import org.polypheny.db.protointerface.proto.ProtoList;
import org.polypheny.db.protointerface.proto.ProtoLong;
import org.polypheny.db.protointerface.proto.ProtoMap;
import org.polypheny.db.protointerface.proto.ProtoNode;
import org.polypheny.db.protointerface.proto.ProtoNull;
import org.polypheny.db.protointerface.proto.ProtoPath;
import org.polypheny.db.protointerface.proto.ProtoPolyType;
import org.polypheny.db.protointerface.proto.ProtoSegment;
import org.polypheny.db.protointerface.proto.ProtoString;
import org.polypheny.db.protointerface.proto.ProtoTime;
import org.polypheny.db.protointerface.proto.ProtoTimeStamp;
import org.polypheny.db.protointerface.proto.ProtoUserDefinedType;
import org.polypheny.db.protointerface.proto.ProtoValue;
import org.polypheny.db.type.PolyType;
import org.polypheny.db.type.entity.PolyBinary;
import org.polypheny.db.type.entity.PolyBoolean;
import org.polypheny.db.type.entity.PolyInterval;
import org.polypheny.db.type.entity.PolyList;
import org.polypheny.db.type.entity.PolyLong;
import org.polypheny.db.type.entity.PolyNull;
import org.polypheny.db.type.entity.PolyString;
import org.polypheny.db.type.entity.PolyUserDefinedValue;
import org.polypheny.db.type.entity.PolyValue;
import org.polypheny.db.type.entity.category.PolyBlob;
import org.polypheny.db.type.entity.document.PolyDocument;
import org.polypheny.db.type.entity.graph.GraphPropertyHolder;
import org.polypheny.db.type.entity.graph.PolyEdge;
import org.polypheny.db.type.entity.graph.PolyGraph;
import org.polypheny.db.type.entity.graph.PolyNode;
import org.polypheny.db.type.entity.graph.PolyPath;
import org.polypheny.db.type.entity.graph.PolyPath.PolySegment;
import org.polypheny.db.type.entity.numerical.PolyBigDecimal;
import org.polypheny.db.type.entity.numerical.PolyDouble;
import org.polypheny.db.type.entity.numerical.PolyFloat;
import org.polypheny.db.type.entity.numerical.PolyInteger;
import org.polypheny.db.type.entity.relational.PolyMap;
import org.polypheny.db.type.entity.temporal.PolyDate;
import org.polypheny.db.type.entity.temporal.PolyTime;
import org.polypheny.db.type.entity.temporal.PolyTimestamp;

public class PolyValueSerializer {


    public static List<ProtoValue> serializeList( List<PolyValue> valuesList ) {
        return valuesList.stream().map( PolyValueSerializer::serialize ).collect( Collectors.toList() );
    }


    private static Map<String, ProtoValue> serializeValueMap( Map<String, PolyValue> valueMap ) {
        return valueMap.entrySet().stream().collect( Collectors.toMap( Entry::getKey, e -> serialize( e.getValue() ) ) );
    }


    public static Map<String, ProtoPolyType> convertTypeMap( Map<String, PolyType> typeMap ) {
        return typeMap.entrySet().stream().collect( Collectors.toMap( Entry::getKey, e -> getType( e.getValue() ) ) );
    }


    public static List<ProtoEntry> serializeToProtoEntryList( PolyMap<PolyValue, PolyValue> polyMap ) {
        return polyMap.entrySet().stream().map( PolyValueSerializer::serializeToProtoEntry ).collect( Collectors.toList() );
    }


    public static ProtoEntry serializeToProtoEntry( Map.Entry<PolyValue, PolyValue> polyMapEntry ) {
        return ProtoEntry.newBuilder()
                .setKey( serialize( polyMapEntry.getKey() ) )
                .setValue( serialize( polyMapEntry.getValue() ) )
                .build();
    }


    public static ProtoValue serialize( PolyValue polyValue ) {
        if ( polyValue == null ) {
            return serializeAsProtoNull();
        }
        switch ( polyValue.getType() ) {
            case BOOLEAN:
                // used by PolyBoolean
                return serializeAsProtoBoolean( polyValue.asBoolean() );
            case TINYINT:
            case SMALLINT:
            case INTEGER:
                // used by PolyInteger
                return serializeAsProtoInteger( polyValue.asInteger() );
            case BIGINT:
                // used by PolyLong
                return serializeAsProtoLong( polyValue.asLong() );
            case DECIMAL:
                // used by PolyBigDecimal
                return serializeAsProtoBigDecimal( polyValue.asBigDecimal() );
            case REAL:
            case FLOAT:
                // used by PolyFloat
                return serializeAsProtoFloat( polyValue.asFloat() );
            case DOUBLE:
                // used by PolyDouble
                return serializeAsProtoDouble( polyValue.asDouble() );
            case DATE:
                // used by PolyDate
                return serializeAsProtoDate( polyValue.asDate() );
            case TIME:
                // used by PolyTime
            case TIME_WITH_LOCAL_TIME_ZONE:
                return serializeAsProtoTime( polyValue.asTime() );
            case TIMESTAMP:
                // used by PolyTimeStamp
            case TIMESTAMP_WITH_LOCAL_TIME_ZONE:
                return serializeAsProtoTimeStamp( polyValue.asTimestamp() );
            case INTERVAL_SECOND:
                // used by PolyInterval
            case INTERVAL_MINUTE_SECOND:
                // used by PolyInterval
            case INTERVAL_MINUTE:
                // used by PolyInterval
            case INTERVAL_HOUR_SECOND:
                // used by PolyInterval
            case INTERVAL_HOUR_MINUTE:
                // used by PolyInterval
            case INTERVAL_HOUR:
                // used by PolyInterval
            case INTERVAL_DAY_SECOND:
                // used by PolyInterval
            case INTERVAL_DAY_MINUTE:
                // used by PolyInterval
            case INTERVAL_DAY_HOUR:
                // used by PolyInterval
            case INTERVAL_DAY:
                // used by PolyInterval
            case INTERVAL_MONTH:
                // used by PolyInterval
            case INTERVAL_YEAR_MONTH:
                // used by PolyInterval
            case INTERVAL_YEAR:
                return serializeAsProtoInterval( polyValue.asInterval() );
            case CHAR:
            case VARCHAR:
                // used by PolyString
                return serializeAsProtoString( polyValue.asString() );
            case BINARY:
            case VARBINARY:
                // used by PolyBinary
                return serializeAsProtoBinary( polyValue.asBinary() );
            case NULL:
                // used by PolyNull
                return serializeAsProtoNull( polyValue.asNull() );
            case ARRAY:
                // used by PolyList
                return serializeAsProtoList( polyValue.asList() );
            case MAP:
                // used by PolyDictionary
                // used by PolyMap
                return serializeAsProtoMap( polyValue.asMap() );
            case DOCUMENT:
                // used by PolyDocument
                return serializeAsProtoDocument( polyValue.asDocument() );
            case GRAPH:
                // used by PolyGraph
                return serializeAsProtoGraph( polyValue.asGraph() );
            case NODE:
                // used by PolyNode
                return serializeAsProtoNode( polyValue.asNode() );
            case EDGE:
                // used by PolyEdge
                return serializeAsProtoEdge( polyValue.asEdge() );
            case PATH:
                // used by PolyPath
                serializeAsProtoPath( polyValue.asPath() );
            case IMAGE:
            case VIDEO:
            case AUDIO:
            case FILE:
                // used by PolyFile
                return serializeAsProtoFile( polyValue.asBlob() );
            case DISTINCT:
            case STRUCTURED:
            case ROW:
            case OTHER:
            case CURSOR:
            case COLUMN_LIST:
            case DYNAMIC_STAR:
            case GEOMETRY:
            case SYMBOL: // used
            case JSON: // used
            case MULTISET:
            case ANY:
                throw new NotImplementedException( "Serialization of " + polyValue.getType() + " to proto not implemented" );
            case USER_DEFINED_TYPE:
                // used by PolyUserDefinedType
                return serializeAsProtoUserDefinedType( polyValue.asUserDefinedValue() );
        }
        throw new NotImplementedException();
    }


    private static ProtoValue serializeAsProtoPath( PolyPath polyPath ) {
        ProtoPath protoPath = ProtoPath.newBuilder()
                .setNodes( serializeToProtoList( polyPath.getNodes().asList() ) )
                .setEdges( serializeToProtoList( polyPath.getEdges().asList() ) )
                .setNames( serializeToProtoList( polyPath.getNames().asList() ) )
                .addAllPaths( serializeToProtoGraphPropertyHolderList( polyPath.getPath() ) )
                .addAllSegments( serializeToProtoSegmentList( polyPath.getSegments() ) )
                .build();
        return ProtoValue.newBuilder()
                .setPath( protoPath )
                .build();
    }


    private static ProtoValue serializeAsProtoEdge( PolyEdge polyEdge ) {
        return ProtoValue.newBuilder()
                .setEdge( serializeToProtoEdge( polyEdge ) )
                .build();

    }


    private static ProtoEdge serializeToProtoEdge( PolyEdge polyEdge ) {
        return ProtoEdge.newBuilder()
                .setGraphPropertyHolder( serializeToProtoGraphPropertyHolder( polyEdge ) )
                .setSource( serializeToProtoString( polyEdge.getSource() ) )
                .setTarget( serializeToProtoString( polyEdge.getTarget() ) )
                .setEdgeDirection( getEdgeDirection( polyEdge.getDirection() ) )
                .build();
    }


    private static ProtoEdge.EdgeDirection getEdgeDirection( PolyEdge.EdgeDirection edgeDirection ) {
        return ProtoEdge.EdgeDirection.valueOf( edgeDirection.name() );
    }


    private static ProtoValue serializeAsProtoNode( PolyNode polyNode ) {
        return ProtoValue.newBuilder()
                .setNode( serializeToProtoNode( polyNode ) )
                .build();
    }


    private static ProtoNode serializeToProtoNode( PolyNode polyNode ) {
        return ProtoNode.newBuilder()
                .setGraphPropertyHolder( serializeToProtoGraphPropertyHolder( polyNode ) )
                .build();
    }


    private static ProtoGraphPropertyHolder serializeToProtoGraphPropertyHolder( GraphPropertyHolder polyGraphPropertyHolder ) {
        return ProtoGraphPropertyHolder.newBuilder()
                .setId( serializeToProtoString( polyGraphPropertyHolder.getId() ) )
                .setVariableName( serializeToProtoString( polyGraphPropertyHolder.getVariableName() ) )
                .setProperties( serializeToProtoMap( polyGraphPropertyHolder.getProperties().asMap() ) )
                .setLabels( serializeToProtoList( polyGraphPropertyHolder.getLabels().asList() ) )
                .build();
    }


    private static ProtoSegment serializeToProtoSegment( PolySegment polySegment ) {
        return ProtoSegment.newBuilder()
                .setId( serializeToProtoString( polySegment.getId() ) )
                .setVariableName( serializeToProtoString( polySegment.getVariableName() ) )
                .setSourceId( serializeToProtoString( polySegment.getSourceId() ) )
                .setEdgeId( serializeToProtoString( polySegment.getEdgeId() ) )
                .setTargetId( serializeToProtoString( polySegment.getTargetId() ) )
                .setSource( serializeToProtoNode( polySegment.getSource() ) )
                .setEdge( serializeToProtoEdge( polySegment.getEdge() ) )
                .setTarget( serializeToProtoNode( polySegment.getTarget() ) )
                .setIsRef( polySegment.isRef() )
                .setEdgeDirection( getEdgeDirection( polySegment.getDirection() ) )
                .build();
    }


    private static List<ProtoGraphPropertyHolder> serializeToProtoGraphPropertyHolderList( List<GraphPropertyHolder> polyGraphPropertyHolders ) {
        return polyGraphPropertyHolders.stream().map( PolyValueSerializer::serializeToProtoGraphPropertyHolder ).collect( Collectors.toList() );
    }


    private static List<ProtoSegment> serializeToProtoSegmentList( List<PolySegment> polySegments ) {
        return polySegments.stream().map( PolyValueSerializer::serializeToProtoSegment ).toList();
    }


    private static ProtoValue serializeAsProtoGraph( PolyGraph polyGraph ) {
        ProtoGraph protoGraph = ProtoGraph.newBuilder()
                .setId( serializeToProtoString( polyGraph.getId() ) )
                .setVariableName( serializeToProtoString( polyGraph.getVariableName() ) )
                .setNodes( serializeToProtoMap( polyGraph.getNodes().asMap() ) )
                .setEdges( serializeToProtoMap( polyGraph.getEdges().asMap() ) )
                .build();
        return ProtoValue.newBuilder()
                .setGraph( protoGraph )
                .build();
    }


    public static ProtoDocument buildProtoDocument( PolyDocument polyDocument ) {
        return ProtoDocument.newBuilder()
                .addAllEntries( serializeToProtoEntryList( polyDocument.asMap() ) )
                .build();
    }


    private static ProtoValue serializeAsProtoDocument( PolyDocument polyDocument ) {
        return ProtoValue.newBuilder()
                .setDocument( buildProtoDocument( polyDocument ) )
                .build();
    }


    private static ProtoValue serializeAsProtoMap( PolyMap<PolyValue, PolyValue> polyMap ) {
        return ProtoValue.newBuilder()
                .setMap( serializeToProtoMap( polyMap ) )
                .build();
    }


    private static ProtoMap serializeToProtoMap( PolyMap<PolyValue, PolyValue> polyMap ) {
        return ProtoMap.newBuilder()
                .addAllEntries( serializeToProtoEntryList( polyMap ) )
                .build();
    }


    private static ProtoValue serializeAsProtoList( PolyList<PolyValue> polyList ) {
        return ProtoValue.newBuilder()
                .setList( serializeToProtoList( polyList ) )
                .build();

    }


    private static ProtoList serializeToProtoList( PolyList<PolyValue> polyList ) {
        return ProtoList.newBuilder()
                .addAllValues( serializeList( polyList.getValue() ) )
                .build();
    }


    private static ProtoValue serializeAsProtoFile( PolyBlob polyBlob ) {
        ProtoBinary protoBinary = ProtoBinary.newBuilder()
                .setBinary( ByteString.copyFrom( polyBlob.getValue() ) )
                .build();
        return ProtoValue.newBuilder()
                .setBinary( protoBinary )
                .build();
    }


    private static ProtoValue serializeAsProtoUserDefinedType( PolyUserDefinedValue userDefinedValue ) {
        ProtoUserDefinedType protoUserDefinedType = ProtoUserDefinedType.newBuilder()
                .putAllTemplate( convertTypeMap( userDefinedValue.getTemplate() ) )
                .putAllValue( serializeValueMap( userDefinedValue.getValue() ) )
                .build();
        return ProtoValue.newBuilder()
                .setUserDefinedType( protoUserDefinedType )
                .build();
    }


    private static ProtoValue serializeAsProtoInterval( PolyInterval polyInterval ) {
        ProtoInterval protoInterval = ProtoInterval.newBuilder()
                .setValue( serializeBigDecimal( polyInterval.getValue() ) )
                .build();
        return ProtoValue.newBuilder()
                .setInterval( protoInterval )
                .build();
    }


    private static ProtoPolyType getType( PolyValue polyValue ) {
        return getType( polyValue.getType() );
    }


    private static ProtoPolyType getType( PolyType polyType ) {
        return ProtoPolyType.valueOf( polyType.getName() );
    }


    public static ProtoValue serializeAsProtoBoolean( PolyBoolean polyBoolean ) {
        ProtoBoolean protoBoolean = ProtoBoolean.newBuilder()
                .setBoolean( polyBoolean.getValue() )
                .build();
        return ProtoValue.newBuilder()
                .setBoolean( protoBoolean )
                .build();
    }


    public static ProtoValue serializeAsProtoInteger( PolyInteger polyInteger ) {
        ProtoInteger protoInteger = ProtoInteger.newBuilder()
                .setInteger( polyInteger.getValue() )
                .build();
        return ProtoValue.newBuilder()
                .setInteger( protoInteger )
                .build();
    }


    public static ProtoValue serializeAsProtoLong( PolyLong polyLong ) {
        ProtoLong protoLong = ProtoLong.newBuilder()
                .setLong( polyLong.value )
                .build();
        return ProtoValue.newBuilder()
                .setLong( protoLong )
                .build();
    }


    public static ProtoValue serializeAsProtoBinary( PolyBinary polyBinary ) {
        ProtoBinary protoBinary = ProtoBinary.newBuilder()
                .setBinary( ByteString.copyFrom( polyBinary.getValue().getBytes() ) )
                .build();
        return ProtoValue.newBuilder()
                .setBinary( protoBinary )
                .build();
    }


    public static ProtoValue serializeAsProtoDate( PolyDate polyDate ) {
        ProtoDate protoDate = ProtoDate.newBuilder()
                .setDate( polyDate.getMillisSinceEpoch() )
                .build();
        return ProtoValue.newBuilder()
                .setDate( protoDate )
                .build();
    }


    public static ProtoValue serializeAsProtoDouble( PolyDouble polyDouble ) {
        ProtoDouble protoDouble = ProtoDouble.newBuilder()
                .setDouble( polyDouble.doubleValue() )
                .build();
        return ProtoValue.newBuilder()
                .setDouble( protoDouble )
                .build();
    }


    public static ProtoValue serializeAsProtoFloat( PolyFloat polyFloat ) {
        ProtoFloat protoFloat = ProtoFloat.newBuilder()
                .setFloat( polyFloat.floatValue() )
                .build();
        return ProtoValue.newBuilder()
                .setFloat( protoFloat )
                .build();
    }


    public static ProtoValue serializeAsProtoString( PolyString polyString ) {
        return ProtoValue.newBuilder()
                .setString( serializeToProtoString( polyString ) )
                .build();
    }


    public static ProtoString serializeToProtoString( PolyString polyString ) {
        return ProtoString.newBuilder()
                .setString( polyString.getValue() )
                .build();
    }


    public static ProtoValue serializeAsProtoTime( PolyTime polyTime ) {
        ProtoTime protoTime = ProtoTime.newBuilder()
                .setValue( polyTime.ofDay )
                .build();
        return ProtoValue.newBuilder()
                .setTime( protoTime )
                .build();
    }


    public static ProtoValue serializeAsProtoTimeStamp( PolyTimestamp polyTimeStamp ) {
        ProtoTimeStamp protoTimeStamp = ProtoTimeStamp.newBuilder()
                .setTimeStamp( polyTimeStamp.getMillisSinceEpoch() )
                .build();
        return ProtoValue.newBuilder()
                .setTimeStamp( protoTimeStamp )
                .build();
    }


    public static ProtoValue serializeAsProtoNull( PolyNull polyNull ) {
        return ProtoValue.newBuilder()
                .setNull( ProtoNull.newBuilder().build() )
                .build();
    }


    private static ProtoValue serializeAsProtoNull() {
        return ProtoValue.newBuilder()
                .setNull( ProtoNull.newBuilder().build() )
                .build();
    }


    public static ProtoValue serializeAsProtoBigDecimal( PolyBigDecimal polyBigDecimal ) {
        ProtoBigDecimal protoBigDecimal = serializeBigDecimal( polyBigDecimal.getValue() );
        return ProtoValue.newBuilder()
                .setBigDecimal( protoBigDecimal )
                .build();
    }


    private static ProtoBigDecimal serializeBigDecimal( BigDecimal bigDecimal ) {
        return ProtoBigDecimal.newBuilder()
                .setUnscaledValue( ByteString.copyFrom( bigDecimal.unscaledValue().toByteArray() ) )
                .setScale( bigDecimal.scale() )
                .setPrecision( bigDecimal.precision() )
                .build();
    }

}
