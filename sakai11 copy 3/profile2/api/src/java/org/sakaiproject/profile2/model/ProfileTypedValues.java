/**
 * Copyright (c) 2008-2012 The Sakai Foundation
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *	       http://www.osedu.org/licenses/ECL-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sakaiproject.profile2.model;

import java.io.Serializable;
import java.util.Date;

import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
public class ProfileTypedValues implements Serializable {

	private static final long serialVersionUID = 1L;

	private long id;
	private String userUuid;
	private String valueGroup;
	private String type;
	private String typeQualifier;
	private String value;

	public ProfileTypedValues(String userUuid, String valueGroup, String type, String typeQualifier, String value) {
		this.userUuid = userUuid;
		this.valueGroup = valueGroup;
		this.type = type;
		this.typeQualifier = typeQualifier;
		this.value = value;
	}
}
