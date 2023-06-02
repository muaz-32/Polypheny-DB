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

package org.polypheny.db.type.entity;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Objects;
import lombok.Value;
import org.apache.calcite.linq4j.tree.Expression;
import org.apache.calcite.linq4j.tree.Expressions;
import org.apache.commons.lang3.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.polypheny.db.type.PolySerializable;
import org.polypheny.db.type.PolyType;
import org.polypheny.db.type.entity.category.PolyNumber;

@Value(staticConstructor = "of")
public class PolyLong extends PolyNumber {

    public Long value;


    public PolyLong( Long value ) {
        super( PolyType.BIGINT );
        this.value = value;
    }


    public PolyLong( long value ) {
        super( PolyType.BIGINT );
        this.value = value;
    }


    public static PolyLong of( long value ) {
        return new PolyLong( value );
    }


    @Override
    public int compareTo( @NotNull PolyValue o ) {
        if ( !o.isNumber() ) {
            return -1;
        }
        return ObjectUtils.compare( value, o.asNumber().LongValue() );
    }


    @Override
    public Expression asExpression() {
        return Expressions.new_( PolyLong.class, Expressions.constant( value ) );
    }


    @Override
    public PolySerializable copy() {
        return PolySerializable.deserialize( serialize(), PolyLong.class );
    }


    @Override
    public int intValue() {
        return Math.toIntExact( value );
    }


    @Override
    public long longValue() {
        return value;
    }


    @Override
    public float floatValue() {
        return value.floatValue();
    }


    @Override
    public double doubleValue() {
        return value;
    }


    @Override
    public BigDecimal bigDecimalValue() {
        return BigDecimal.valueOf( value );
    }


    @Override
    public PolyLong increment() {
        return PolyLong.of( value + 1 );
    }


    @Override
    public PolyNumber divide( @NotNull PolyNumber other ) {
        return PolyBigDecimal.of( bigDecimalValue().divide( other.bigDecimalValue(), MathContext.DECIMAL64 ) );
    }


    @Override
    public PolyNumber multiply( @NotNull PolyNumber other ) {
        return other.isDecimal() ? PolyBigDecimal.of( bigDecimalValue().multiply( other.bigDecimalValue() ) ) : PolyLong.of( value * other.longValue() );
    }


    @Override
    public PolyNumber plus( @NotNull PolyNumber other ) {
        return other.isDecimal() ? PolyBigDecimal.of( bigDecimalValue().add( other.bigDecimalValue() ) ) : PolyLong.of( value + other.longValue() );
    }


    @Override
    public PolyNumber subtract( @NotNull PolyNumber other ) {
        return other.isDecimal() ? PolyBigDecimal.of( bigDecimalValue().subtract( other.bigDecimalValue() ) ) : PolyLong.of( value - other.longValue() );
    }


    @Override
    public boolean equals( Object o ) {
        if ( this == o ) {
            return true;
        }
        if ( o == null || getClass() != o.getClass() ) {
            return false;
        }
        if ( !super.equals( o ) ) {
            return false;
        }
        PolyLong polyLong = (PolyLong) o;
        return Objects.equals( value, polyLong.value );
    }


    @Override
    public int hashCode() {
        return Objects.hash( super.hashCode(), value );
    }


    @Override
    public String toString() {
        return value.toString();
    }

}
