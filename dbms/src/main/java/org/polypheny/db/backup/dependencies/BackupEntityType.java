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

package org.polypheny.db.backup.dependencies;

/**
 * Enum that represents the different types of entities that can be backed up. (Used for dependencies/Referencers)
 */
public enum BackupEntityType {
    NAMESPACE( 1 ),
    TABLE( 2 );
    //SOURCE( 3 ),
    //VIEW( 4 ),
    //MATERIALIZED_VIEW( 5 );


    private final int id;


    BackupEntityType( int id ) { this.id = id; }
}
