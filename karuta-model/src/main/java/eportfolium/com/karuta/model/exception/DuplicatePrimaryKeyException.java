/* =======================================================
	Copyright 2020 - ePortfolium - Licensed under the
	Educational Community License, Version 2.0 (the "License"); you may
	not use this file except in compliance with the License. You may
	obtain a copy of the License at

	http://www.osedu.org/licenses/ECL-2.0

	Unless required by applicable law or agreed to in writing,
	software distributed under the License is distributed on an "AS IS"
	BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
	or implied. See the License for the specific language governing
	permissions and limitations under the License.
   ======================================================= */

package eportfolium.com.karuta.model.exception;

import java.io.Serializable;

import eportfolium.com.karuta.util.MessageUtil;

@SuppressWarnings("serial")
//@ApplicationException(rollback = true)
public class DuplicatePrimaryKeyException extends BusinessException {
	static public final int INFORMATIONLEVEL_ENTITY_ID = 1;
	static public final int INFORMATIONLEVEL_ID = 2;
	static public final int INFORMATIONLEVEL_NONE = 3;

	private int informationLevel = INFORMATIONLEVEL_ENTITY_ID;
	private String entityLabelMessageId;
	private Serializable id;

	/**
	 * This exception is thrown by an IPersistenceExceptionInterpreter when an attempt to create an entity has failed because
	 * it already exists.  This is not possible when the entity definition specifies auto-generated ids.
	 * 
	 * @param entityLabelMessageId	the key of a message that describes the entity.
	 * @param id	the id of the entity.
	 */
	public DuplicatePrimaryKeyException(String entityLabelMessageId, Serializable id) {

		// Don't convert the message ids to messages yet because we're in the
		// server's locale, not the user's.

		super();
		this.informationLevel = INFORMATIONLEVEL_ENTITY_ID;
		this.entityLabelMessageId = entityLabelMessageId;
		this.id = id;
	}

	/**
	 * This exception is thrown by an IPersistenceExceptionInterpreter when an attempt to create an entity has failed because
	 * it already exists.  This is not possible when the entity definition specifies auto-generated ids.
	 * 
	 * @param id	the id of the entity.
	 */
	public DuplicatePrimaryKeyException(Serializable id) {

		// Don't convert the message ids to messages yet because we're in the
		// server's locale, not the user's.

		super();
		this.informationLevel = INFORMATIONLEVEL_ID;
		this.id = id;
	}

	/**
	 * This exception is thrown by an IPersistenceExceptionInterpreter when an attempt to create an entity has failed because
	 * it already exists.  This is not possible when the entity definition specifies auto-generated ids.
	 */
	public DuplicatePrimaryKeyException() {

		// Don't convert the message ids to messages yet because we're in the
		// server's locale, not the user's.

		super();
		this.informationLevel = INFORMATIONLEVEL_NONE;
	}

	@Override
	public String getMessage() {
		String msg;
		Object[] msgArgs;

		// We deferred converting the message ids to messages until now, when we
		// are more likely to be in the user's locale.

		if (informationLevel == INFORMATIONLEVEL_ENTITY_ID) {
			msgArgs = new Object[] { MessageUtil.toText(entityLabelMessageId), id };
			msg = MessageUtil.toText("DuplicatePrimaryKeyException", msgArgs);
		}
		else if (informationLevel == INFORMATIONLEVEL_ID) {
			msgArgs = new Object[] { id };
			msg = MessageUtil.toText("DuplicatePrimaryKeyException_2", msgArgs);
		}
		else if (informationLevel == INFORMATIONLEVEL_NONE) {
			msgArgs = new Object[] {};
			msg = MessageUtil.toText("DuplicatePrimaryKeyException_3", msgArgs);
		}
		else {
			throw new IllegalStateException("informationLevel = " + informationLevel);
		}

		return msg;
	}

	public String getEntityLabelMessageId() {
		return entityLabelMessageId;
	}

	public Serializable getId() {
		return id;
	}

	public int getInformationLevel() {
		return informationLevel;
	}
}
