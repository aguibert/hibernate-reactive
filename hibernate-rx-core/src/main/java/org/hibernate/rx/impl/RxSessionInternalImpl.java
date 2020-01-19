package org.hibernate.rx.impl;

import org.hibernate.*;
import org.hibernate.collection.spi.PersistentCollection;
import org.hibernate.engine.spi.ExceptionConverter;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerGroup;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.*;
import org.hibernate.graph.GraphSemantic;
import org.hibernate.graph.RootGraph;
import org.hibernate.graph.spi.RootGraphImplementor;
import org.hibernate.internal.SessionCreationOptions;
import org.hibernate.internal.SessionFactoryImpl;
import org.hibernate.internal.SessionImpl;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.proxy.LazyInitializer;
import org.hibernate.rx.RxSession;
import org.hibernate.rx.RxSessionInternal;
import org.hibernate.rx.engine.spi.RxActionQueue;
import org.hibernate.rx.event.spi.RxDeleteEventListener;
import org.hibernate.rx.event.spi.RxFlushEventListener;
import org.hibernate.rx.event.spi.RxLoadEventListener;
import org.hibernate.rx.event.spi.RxPersistEventListener;
import org.hibernate.rx.util.impl.RxUtil;

import javax.persistence.EntityNotFoundException;
import javax.persistence.LockModeType;
import java.io.Serializable;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

public class RxSessionInternalImpl extends SessionImpl implements RxSessionInternal, EventSource {

	private transient RxActionQueue rxActionQueue = new RxActionQueue( this );

	public RxSessionInternalImpl(SessionFactoryImpl delegate, SessionCreationOptions options) {
		super( delegate, options );
	}

	@Override
	public RxActionQueue getRxActionQueue() {
		return rxActionQueue;
	}

	@Override
	public RxSession reactive() {
		return new RxSessionImpl( this );
	}

	@Override
	public Object immediateLoad(String entityName, Serializable id) throws HibernateException {
		throw new LazyInitializationException("reactive sessions do not support transparent lazy fetching"
				+ " - use RxSession.fetch() (entity '" + entityName + "' with id '" + id + "' was not loaded)");
	}

	@Override
	public <T> CompletionStage<Optional<T>> rxFetch(T association) {
		if ( association instanceof HibernateProxy ) {
			LazyInitializer initializer = ((HibernateProxy) association).getHibernateLazyInitializer();
			//TODO: is this correct?
			// SessionImpl doesn't use IdentifierLoadAccessImpl for initializing proxies
			return new RxIdentifierLoadAccessImpl<T>( initializer.getEntityName() )
					.fetch( initializer.getIdentifier() )
					.thenApply(Optional::get)
					.thenApply( result -> {
						initializer.setSession( this );
						return Optional.ofNullable(result);
					} );
		}
		if ( association instanceof PersistentCollection ) {
			//TODO: handle PersistentCollection (raise InitializeCollectionEvent)
			throw new UnsupportedOperationException("fetch() is not yet implemented for collections");
		}
		return RxUtil.completedFuture( Optional.ofNullable(association) );
	}

	@Override
	public CompletionStage<Void> rxPersist(Object entity) {
		return schedulePersist( entity );
	}

	// Should be similar to firePersist
	private CompletionStage<Void> schedulePersist(Object entity) {
		CompletionStage<Void> ret = RxUtil.nullFuture();
		for ( PersistEventListener listener : listeners( EventType.PERSIST ) ) {
			PersistEvent event = new PersistEvent( null, entity, this );
			CompletionStage<Void> stage = ((RxPersistEventListener) listener).rxOnPersist(event);
			ret = ret.thenCompose(v -> stage);
		}
		return ret;
	}

	@Override
	public CompletionStage<Void> rxPersistOnFlush(Object entity) {
		return schedulePersistOnFlush( entity );
	}

	// Should be similar to firePersist
	private CompletionStage<Void> schedulePersistOnFlush(Object entity) {
		CompletionStage<Void> ret = RxUtil.nullFuture();
		for ( PersistEventListener listener : listeners( EventType.PERSIST_ONFLUSH ) ) {
			PersistEvent event = new PersistEvent( null, entity, this );
			CompletionStage<Void> stage = ((RxPersistEventListener) listener).rxOnPersist(event);
			ret = ret.thenCompose(v -> stage);
		}
		return ret;
	}

	@Override
	public CompletionStage<Void> rxRemove(Object entity) {
		return fireRemove( entity );
	}

	// Should be similar to fireRemove
	private CompletionStage<Void> fireRemove(Object entity) {
		CompletionStage<Void> ret = RxUtil.nullFuture();
		for ( DeleteEventListener listener : listeners( EventType.DELETE ) ) {
			DeleteEvent event = new DeleteEvent( null, entity, this );

			CompletionStage<Void> delete = ((RxDeleteEventListener) listener).rxOnDelete(event);
			ret = ret.thenCompose(v -> delete);
		}
		return ret;
	}

	@Override
	public CompletionStage<Void> rxFlush() {
//		checkOpen();
		return doFlush();
	}

	private CompletionStage<Void> doFlush() {
//		checkTransactionNeeded();
//		checkTransactionSynchStatus();

//			if ( persistenceContext.getCascadeLevel() > 0 ) {
//				throw new HibernateException( "Flush during cascade is dangerous" );
//			}

		CompletionStage<Void> ret = RxUtil.nullFuture();
		FlushEvent flushEvent = new FlushEvent( this );
		for ( FlushEventListener listener : listeners( EventType.FLUSH ) ) {
			CompletionStage<Void> flush = ((RxFlushEventListener) listener).rxOnFlush(flushEvent);
			ret = ret.thenCompose( v -> flush );
		}

//			delayedAfterCompletion();
		return ret.exceptionally( x -> {
			if ( x instanceof RuntimeException ) {
				throw exceptionConverter().convert( (RuntimeException) x );
			}
			else {
				return RxUtil.rethrow( x );
			}
		} );
	}

	@Override
	public <T> CompletionStage<Optional<T>> rxFind(
			Class<T> entityClass,
			Object primaryKey,
			LockModeType lockModeType,
			Map<String, Object> properties) {
//		checkOpen();

		LockOptions lockOptions = null;

		getLoadQueryInfluencers().getEffectiveEntityGraph().applyConfiguredGraph( properties );

		final RxIdentifierLoadAccessImpl<T> loadAccess = rxById( entityClass );
//			loadAccess.with( determineAppropriateLocalCacheMode( properties ) );

//			if ( lockModeType != null ) {
//				if ( !LockModeType.NONE.equals( lockModeType) ) {
//					checkTransactionNeededForUpdateOperation();
//				}
//				lockOptions = buildLockOptions( lockModeType, properties );
//				loadAccess.with( lockOptions );
//			}

		return loadAccess.load( (Serializable) primaryKey )
				.handle( (v, x) -> {
					if ( x instanceof EntityNotFoundException) {
						// DefaultLoadEventListener.returnNarrowedProxy may throw ENFE (see HHH-7861 for details),
						// which find() should not throw. Find() should return null if the entity was not found.
						//			if ( log.isDebugEnabled() ) {
						//				String entityName = entityClass != null ? entityClass.getName(): null;
						//				String identifierValue = primaryKey != null ? primaryKey.toString() : null ;
						//				log.ignoringEntityNotFound( entityName, identifierValue );
						//			}
						return Optional.<T>empty();
					}
					if ( x instanceof ObjectDeletedException) {
						//the spec is silent about people doing remove() find() on the same PC
						return Optional.<T>empty();
					}
					if ( x instanceof ObjectNotFoundException) {
						//should not happen on the entity itself with get
						throw new IllegalArgumentException( x.getMessage(), x );
					}
					if ( x instanceof MappingException
							|| x instanceof TypeMismatchException
							|| x instanceof ClassCastException ) {
						throw exceptionConverter().convert( new IllegalArgumentException( x.getMessage(), x ) );
					}
					if ( x instanceof JDBCException ) {
//			if ( accessTransaction().getRollbackOnly() ) {
//				// assume this is the similar to the WildFly / IronJacamar "feature" described under HHH-12472
//				return null;
//			}
//			else {
						throw exceptionConverter().convert( (JDBCException) x, lockOptions );
//			}
					}
					if ( x instanceof RuntimeException ) {
						throw exceptionConverter().convert( (RuntimeException) x, lockOptions );
					}
					return v;
				} )
				.whenComplete( (v, x) -> getLoadQueryInfluencers().getEffectiveEntityGraph().clear() );
	}

	<T> RxIdentifierLoadAccessImpl<T> rxById(Class<T> entityClass) {
		return new RxIdentifierLoadAccessImpl( entityClass );
	}

	private ExceptionConverter exceptionConverter() {
		return unwrap( EventSource.class ).getExceptionConverter();
	}

	private <T> Iterable<T> listeners(EventType<T> type) {
		return eventListenerGroup( type ).listeners();
	}

	private <T> EventListenerGroup<T> eventListenerGroup(EventType<T> type) {
		return getFactory().unwrap( SessionFactoryImplementor.class )
				.getServiceRegistry().getService( EventListenerRegistry.class )
				.getEventListenerGroup( type );
	}

	private EntityPersister locateEntityPersister(Class<?> entityClass) {
		return getFactory().getMetamodel().locateEntityPersister( entityClass );
	}

	private EntityPersister locateEntityPersister(String entityName) {
		return getFactory().getMetamodel().locateEntityPersister( entityName );
	}

	private CompletionStage<Void> fireLoad(LoadEvent event, LoadEventListener.LoadType loadType) {
//		checkOpenOrWaitingForAutoClose();
		return fireLoadNoChecks( event, loadType );
//		delayedAfterCompletion();
	}

	//Performance note:
	// This version of #fireLoad is meant to be invoked by internal methods only,
	// so to skip the session open, transaction synch, etc.. checks,
	// which have been proven to be not particularly cheap:
	// it seems they prevent these hot methods from being inlined.
	private CompletionStage<Void> fireLoadNoChecks(LoadEvent event, LoadEventListener.LoadType loadType) {
//		pulseTransactionCoordinator();
		CompletionStage<Void> ret = RxUtil.nullFuture();
		for ( LoadEventListener listener : listeners( EventType.LOAD ) ) {
			CompletionStage<Void> load = ((RxLoadEventListener) listener).rxOnLoad(event, loadType);
			ret = ret.thenCompose( v -> load );
		}
		return ret;
	}

	/**
	 * Check if there is a Hibernate or JTA transaction in progress and,
	 * if there is not, flush if necessary, make sure the connection has
	 * been committed (if it is not in autocommit mode) and run the after
	 * completion processing
	 *
	 * @param success Was the operation a success
	 */
	public void afterOperation(boolean success) {
//		if ( !isTransactionInProgress() ) {
//			getJdbcCoordinator().afterTransaction();
//		}
	}

	private class RxIdentifierLoadAccessImpl<T> {

		private final EntityPersister entityPersister;

		private LockOptions lockOptions;
		private CacheMode cacheMode;

		//Note that entity graphs aren't supported at all
		//because we're not using the EntityLoader from
		//the plan package, so this stuff is useless
		private RootGraphImplementor<T> rootGraph;
		private GraphSemantic graphSemantic;

		public RxIdentifierLoadAccessImpl(EntityPersister entityPersister) {
			this.entityPersister = entityPersister;
		}

		public RxIdentifierLoadAccessImpl(String entityName) {
			this( locateEntityPersister( entityName ) );
		}

		public RxIdentifierLoadAccessImpl(Class<T> entityClass) {
			this( locateEntityPersister( entityClass ) );
		}

		public final RxIdentifierLoadAccessImpl<T> with(LockOptions lockOptions) {
			this.lockOptions = lockOptions;
			return this;
		}

		public RxIdentifierLoadAccessImpl<T> with(CacheMode cacheMode) {
			this.cacheMode = cacheMode;
			return this;
		}

		public RxIdentifierLoadAccessImpl<T> with(RootGraph<T> graph, GraphSemantic semantic) {
			rootGraph = (RootGraphImplementor<T>) graph;
			graphSemantic = semantic;
			return this;
		}

		public final CompletionStage<Optional<T>> getReference(Serializable id) {
			return perform( () -> doGetReference( id ) );
		}

		protected CompletionStage<Optional<T>> perform(Supplier<CompletionStage<Optional<T>>> executor) {
			if ( graphSemantic != null ) {
				if ( rootGraph == null ) {
					throw new IllegalArgumentException( "Graph semantic specified, but no RootGraph was supplied" );
				}
			}
			CacheMode sessionCacheMode = getCacheMode();
			boolean cacheModeChanged = false;
			if ( cacheMode != null ) {
				// naive check for now...
				// todo : account for "conceptually equal"
				if ( cacheMode != sessionCacheMode ) {
					setCacheMode( cacheMode );
					cacheModeChanged = true;
				}
			}

			if ( graphSemantic != null ) {
				getLoadQueryInfluencers().getEffectiveEntityGraph().applyGraph( rootGraph, graphSemantic );
			}

			boolean finalCacheModeChanged = cacheModeChanged;
			return executor.get()
					.whenComplete( (v, x) -> {
						if ( graphSemantic != null ) {
							getLoadQueryInfluencers().getEffectiveEntityGraph().clear();
						}
						if ( finalCacheModeChanged ) {
							// change it back
							setCacheMode( sessionCacheMode );
						}
					} );
		}

		protected CompletionStage<Optional<T>> doGetReference(Serializable id) {
			if ( lockOptions != null ) {
				LoadEvent event = new LoadEvent(id, entityPersister.getEntityName(), lockOptions, RxSessionInternalImpl.this, getReadOnlyFromLoadQueryInfluencers());
				return fireLoad( event, LoadEventListener.LOAD ).thenApply( v -> (Optional<T>) event.getResult() );
			}

			LoadEvent event = new LoadEvent(id, entityPersister.getEntityName(), false, RxSessionInternalImpl.this, getReadOnlyFromLoadQueryInfluencers());
			return fireLoad( event, LoadEventListener.LOAD )
					.thenApply( v -> {
						if ( event.getResult() == null ) {
							getFactory().getEntityNotFoundDelegate().handleEntityNotFound(
									entityPersister.getEntityName(),
									id
							);
						}
						return (Optional<T>) event.getResult();
					} ).whenComplete( (v, x) -> afterOperation( x != null ) );
		}

		public final CompletionStage<Optional<T>> load(Serializable id) {
			return perform( () -> doLoad( id, LoadEventListener.GET) );
		}

		public final CompletionStage<Optional<T>> fetch(Serializable id) {
			return perform( () -> doLoad( id, LoadEventListener.IMMEDIATE_LOAD) );
		}

		private Boolean getReadOnlyFromLoadQueryInfluencers() {
			return getLoadQueryInfluencers().getReadOnly();
		}

		protected final CompletionStage<Optional<T>> doLoad(Serializable id, LoadEventListener.LoadType loadType) {
			if ( lockOptions != null ) {
				LoadEvent event = new LoadEvent(id, entityPersister.getEntityName(), lockOptions, RxSessionInternalImpl.this, getReadOnlyFromLoadQueryInfluencers());
				return fireLoad( event, loadType ).thenApply( v -> (Optional<T>) event.getResult() );
			}

			LoadEvent event = new LoadEvent(id, entityPersister.getEntityName(), false, RxSessionInternalImpl.this, getReadOnlyFromLoadQueryInfluencers());
			return fireLoad( event, loadType )
					.handle( (v, t) -> {
						afterOperation( t != null );
						if ( t != null
								// if session cache contains proxy for non-existing object
								&& !( t instanceof ObjectNotFoundException ) ) {
							RxUtil.rethrow( t );
						}
						return (Optional<T>) event.getResult();
					} );
		}
	}

	@Override
	public <T> T unwrap(Class<T> clazz) {
		if ( RxSessionInternal.class.isAssignableFrom( clazz ) ) {
			return clazz.cast(this);
		}
		if ( RxSession.class.isAssignableFrom( clazz ) ) {
			return clazz.cast( reactive() );
		}
		return super.unwrap( clazz );
	}
}

