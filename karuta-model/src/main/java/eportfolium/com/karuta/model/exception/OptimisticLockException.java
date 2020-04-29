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

import eportfolium.com.karuta.util.ClassUtil;
import eportfolium.com.karuta.util.MessageUtil;

@SuppressWarnings("serial")
//@ApplicationException(rollback = true)
public class OptimisticLockException extends BusinessException {
	private String entityLabelMessageId;
	private Serializable id;
	private String entityName;
	private Exception exception;

	/**
	 * This exception is thrown by an IPersistenceExceptionInterpreter when an attempt to update an entity has failed
	 * because the entity has been updated by an intervening transaction since we read its state.
	 * 
	 * @param entityName the name of the entity being updated. It will be stripped down to its unqualified name (eg.
	 *        "jumpstart.Department" would be stripped down to "Department") to be used as a message key when generating
	 *        a message in getMessage().
	 * @param id the id of the entity.
	 * @param exception the root cause exception, eg. a Hibernate StaleObjectException.
	 */
	public OptimisticLockException(String entityName, Serializable id, Exception exception) {

		// Don't convert the message ids to messages yet because we're in the server's locale, not the user's.

		super();

		if (entityName == null) {
			throw new IllegalArgumentException("entityName is null");
		}
		if (id == null) {
			throw new IllegalArgumentException("id is null");
		}

		this.entityLabelMessageId = ClassUtil.extractUnqualifiedName(entityName);
		this.id = id;
		this.entityName = entityName;
		this.exception = exception;
	}

	@Override
	public String getMessage() {

		// We deferred converting the message ids to messages until now, when we are more likely to be in the user's
		// locale.

		String originalExceptionlMessage = exception == null ? "" : exception.getLocalizedMessage();
		Object[] msgArgs = new Object[] { MessageUtil.toText(entityLabelMessageId), id, originalExceptionlMessage };

		String msg = MessageUtil.toText("OptimisticLockException", msgArgs);
		return msg;
	}

	public String getEntityName() {
		return entityName;
	}

	public String getEntityLabelMessageId() {
		return entityLabelMessageId;
	}

	public Exception getException() {
		return exception;
	}

	public Serializable getId() {
		return id;
	}
}
