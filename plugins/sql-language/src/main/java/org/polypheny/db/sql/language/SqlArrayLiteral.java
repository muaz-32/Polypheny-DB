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

import org.polypheny.db.algebra.type.AlgDataType;
import org.polypheny.db.languages.ParserPos;
import org.polypheny.db.sql.language.SqlWriter.Frame;
import org.polypheny.db.type.entity.PolyList;
import org.polypheny.db.type.entity.PolyValue;

public class SqlArrayLiteral extends SqlLiteral {

    private final PolyList<?> nodes;


    /**
     * Creates a <code>SqlLiteral</code>.
     */
    protected SqlArrayLiteral( PolyList<?> value, AlgDataType type, ParserPos pos ) {
        super( value, type.getPolyType(), pos );
        this.nodes = value;
    }


    @Override
    public void unparse( SqlWriter writer, int leftPrec, int rightPrec ) {
        Frame frame = writer.startList( "ARRAY[", "]" );
        for ( PolyValue node : nodes ) {
            writer.sep( "," );
            SqlLiteral.unparsePoly( node.type, node, writer );
        }
        writer.endList( frame );
    }

}
