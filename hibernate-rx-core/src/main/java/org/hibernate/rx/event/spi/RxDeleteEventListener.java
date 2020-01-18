package org.hibernate.rx.event.spi;


import org.hibernate.HibernateException;
import org.hibernate.event.spi.DeleteEvent;

import java.io.Serializable;
import java.util.Set;
import java.util.concurrent.CompletionStage;

/**
 * Defines the contract for handling of deletion events generated from a session.
 *
 * @author Steve Ebersole
 */
public interface RxDeleteEventListener extends Serializable {

	/**
	 * Handle the given delete event.
	 *
	 * @param event The delete event to be handled.
	 *
	 * @throws HibernateException
	 */
	CompletionStage<Void> rxOnDelete(DeleteEvent event) throws HibernateException;

	CompletionStage<Void> rxOnDelete(DeleteEvent event, Set transientEntities) throws HibernateException;
}
