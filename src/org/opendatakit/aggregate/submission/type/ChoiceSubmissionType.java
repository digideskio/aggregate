/*
 * Copyright (C) 2010 University of Washington
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.opendatakit.aggregate.submission.type;

import java.util.ArrayList;
import java.util.List;

import org.opendatakit.aggregate.datamodel.FormElementModel;
import org.opendatakit.aggregate.datamodel.SelectChoice;
import org.opendatakit.aggregate.exception.ODKConversionException;
import org.opendatakit.aggregate.format.Row;
import org.opendatakit.aggregate.format.element.ElementFormatter;
import org.opendatakit.aggregate.submission.SubmissionKeyPart;
import org.opendatakit.aggregate.submission.SubmissionValue;
import org.opendatakit.common.persistence.CommonFieldsBase;
import org.opendatakit.common.persistence.Datastore;
import org.opendatakit.common.persistence.EntityKey;
import org.opendatakit.common.persistence.Query;
import org.opendatakit.common.persistence.Query.Direction;
import org.opendatakit.common.persistence.Query.FilterOperation;
import org.opendatakit.common.persistence.exception.ODKDatastoreException;
import org.opendatakit.common.persistence.exception.ODKEntityPersistException;
import org.opendatakit.common.security.User;
import org.opendatakit.common.web.CallingContext;

/**
 * 
 * @author wbrunette@gmail.com
 * @author mitchellsundt@gmail.com
 * 
 */
public class ChoiceSubmissionType extends SubmissionFieldBase<List<String>> {

	boolean isChanged = false;
	List<String> values = new ArrayList<String>();
	
	List<SelectChoice> choices = new ArrayList<SelectChoice>();
	
	private final String parentKey;
	private final EntityKey topLevelTableKey;
	
	public ChoiceSubmissionType(FormElementModel element, String parentKey, EntityKey topLevelTableKey) {
		super(element);
		this.parentKey = parentKey;
		this.topLevelTableKey = topLevelTableKey;
	}

	@Override
	public void formatValue(ElementFormatter elemFormatter, Row row, String ordinalValue, CallingContext cc)
			throws ODKDatastoreException {
		elemFormatter.formatChoices(values, element.getGroupQualifiedElementName()+ ordinalValue, row);
	}

	@Override
	public List<String> getValue() {
		return values;
	}

	@Override
	public void getValueFromEntity(CallingContext cc) throws ODKDatastoreException {
		
		SelectChoice sel = (SelectChoice) element.getFormDataModel().getBackingObjectPrototype();
		Query q = cc.getDatastore().createQuery(element.getFormDataModel().getBackingObjectPrototype(), cc.getCurrentUser());
		q.addFilter(sel.parentAuri, FilterOperation.EQUAL, parentKey);
		q.addSort(sel.ordinalNumber, Direction.ASCENDING);

		List<? extends CommonFieldsBase> choiceHits = q.executeQuery(0);
		choices.clear();
		values.clear();
		for ( CommonFieldsBase cb : choiceHits ) {
			SelectChoice choice = (SelectChoice) cb;
			choices.add(choice);
			values.add(choice.getValue());
		}
		isChanged = false;
	}

	@Override
	public void setValueFromString(String concatenatedValues) throws ODKConversionException, ODKDatastoreException {
		isChanged = true;
		values.clear();
		String[] splits = concatenatedValues.split(" ");
		for ( String v : splits ) {
			if ( v != null ) values.add(v);
		}
	}

	@Override
	public void recursivelyAddEntityKeys(List<EntityKey> keyList, CallingContext cc) {
		for ( SelectChoice s : choices ) {
			keyList.add( new EntityKey( s, s.getUri()));
		}
	}
	
	@Override
	public void persist(CallingContext cc) throws ODKEntityPersistException {
		
		if ( isChanged ) {
			Datastore ds = cc.getDatastore();
			User user = cc.getCurrentUser();
			// clear the old underlying data records...
			List<EntityKey> keys = new ArrayList<EntityKey>();
			for ( SelectChoice c: choices ) {
				keys.add(new EntityKey(c, c.getUri()));
			}

			try {
				ds.deleteEntities(keys, user);
			} catch (ODKDatastoreException e) {
				throw new ODKEntityPersistException("Unable to delete old choices", e);
			}
			choices.clear();
		
			int i = 1;
			for ( String v : values ) {
				SelectChoice c = (SelectChoice) ds.createEntityUsingRelation(element.getFormDataModel().getBackingObjectPrototype(), user);
				c.setTopLevelAuri(topLevelTableKey.getKey());
				c.setParentAuri(parentKey);
				c.setOrdinalNumber(Long.valueOf(i++));
				c.setValue(v);
				choices.add(c);
			}
			ds.putEntities(choices, user);
			isChanged = false;
		}
	}

	public SubmissionValue resolveSubmissionKeyBeginningAt(int i,
			List<SubmissionKeyPart> parts) {
		// TODO: indexing into the list would require creating a 
		// virtual StringSubmissionType for each choice value.
		// for now, don't go to the trouble of that...
		// NOTE: a virtual StringSubmissionType would allow the
		// value to be of arbitrary length.
		return this;
	}
}