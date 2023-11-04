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

package org.polypheny.db.sql.language;


import org.polypheny.db.algebra.constant.Syntax;
import org.polypheny.db.catalog.exceptions.GenericRuntimeException;
import org.polypheny.db.util.Conformance;
import org.polypheny.db.util.Util;


/**
 * Enumeration of possible syntactic types of {@link SqlOperator operators}.
 */
public enum SqlSyntax {
    /**
     * Function syntax, as in "Foo(x, y)".
     */
    FUNCTION {
        @Override
        public void unparse( SqlWriter writer, SqlOperator operator, SqlCall call, int leftPrec, int rightPrec ) {
            SqlUtil.unparseFunctionSyntax( operator, writer, call );
        }
    },

    /**
     * Function syntax, as in "Foo(x, y)", but uses "*" if there are no arguments, for example "COUNT(*)".
     */
    FUNCTION_STAR {
        @Override
        public void unparse( SqlWriter writer, SqlOperator operator, SqlCall call, int leftPrec, int rightPrec ) {
            SqlUtil.unparseFunctionSyntax( operator, writer, call );
        }
    },

    /**
     * Binary operator syntax, as in "x + y".
     */
    BINARY {
        @Override
        public void unparse( SqlWriter writer, SqlOperator operator, SqlCall call, int leftPrec, int rightPrec ) {
            SqlUtil.unparseBinarySyntax( operator, call, writer, leftPrec, rightPrec );
        }
    },

    /**
     * Prefix unary operator syntax, as in "- x".
     */
    PREFIX {
        @Override
        public void unparse( SqlWriter writer, SqlOperator operator, SqlCall call, int leftPrec, int rightPrec ) {
            assert call.operandCount() == 1;
            writer.keyword( operator.getName() );
            ((SqlNode) call.operand( 0 )).unparse( writer, operator.getLeftPrec(), operator.getRightPrec() );
        }
    },

    /**
     * Postfix unary operator syntax, as in "x ++".
     */
    POSTFIX {
        @Override
        public void unparse( SqlWriter writer, SqlOperator operator, SqlCall call, int leftPrec, int rightPrec ) {
            assert call.operandCount() == 1;
            ((SqlNode) call.operand( 0 )).unparse( writer, operator.getLeftPrec(), operator.getRightPrec() );
            writer.keyword( operator.getName() );
        }
    },

    /**
     * Special syntax, such as that of the SQL CASE operator, "CASE x WHEN 1 THEN 2 ELSE 3 END".
     */
    SPECIAL {
        @Override
        public void unparse( SqlWriter writer, SqlOperator operator, SqlCall call, int leftPrec, int rightPrec ) {
            // You probably need to override the operator's unparse method.
            throw Util.needToImplement( this );
        }
    },

    /**
     * Function syntax which takes no parentheses if there are no arguments, for example "CURRENTTIME".
     *
     * @see Conformance#allowNiladicParentheses()
     */
    FUNCTION_ID {
        @Override
        public void unparse( SqlWriter writer, SqlOperator operator, SqlCall call, int leftPrec, int rightPrec ) {
            SqlUtil.unparseFunctionSyntax( operator, writer, call );
        }
    },

    /**
     * Syntax of an internal operator, which does not appear in the SQL.
     */
    INTERNAL {
        @Override
        public void unparse( SqlWriter writer, SqlOperator operator, SqlCall call, int leftPrec, int rightPrec ) {
            throw new UnsupportedOperationException( "Internal operator '" + operator + "' " + "cannot be un-parsed" );
        }
    };


    public static SqlSyntax fromSyntax( Syntax syntax ) {
        switch ( syntax ) {
            case FUNCTION:
                return SqlSyntax.FUNCTION;
            case FUNCTION_STAR:
                return SqlSyntax.FUNCTION_STAR;
            case BINARY:
                return SqlSyntax.BINARY;

            case PREFIX:
                return SqlSyntax.PREFIX;
            case POSTFIX:
                return SqlSyntax.POSTFIX;
            case SPECIAL:
                return SqlSyntax.SPECIAL;
            case FUNCTION_ID:
                return SqlSyntax.FUNCTION_ID;
            case INTERNAL:
                return SqlSyntax.INTERNAL;
            default:
                throw new GenericRuntimeException( "There seems to be an internal error while translating the Syntax." );
        }
    }


    /**
     * Converts a call to an operator of this syntax into a string.
     */
    public abstract void unparse( SqlWriter writer, SqlOperator operator, SqlCall call, int leftPrec, int rightPrec );


    public Syntax getSyntax() {
        switch ( this ) {
            case FUNCTION:
                return Syntax.FUNCTION;
            case FUNCTION_STAR:
                return Syntax.FUNCTION_STAR;
            case BINARY:
                return Syntax.BINARY;
            case PREFIX:
                return Syntax.PREFIX;
            case POSTFIX:
                return Syntax.POSTFIX;
            case SPECIAL:
                return Syntax.SPECIAL;
            case FUNCTION_ID:
                return Syntax.FUNCTION_ID;
            case INTERNAL:
                return Syntax.INTERNAL;
        }
        throw new GenericRuntimeException( "There seems to be an internal error while translating the Syntax." );
    }
}

