package org.hibernate.rx.service;

import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

import io.vertx.mutiny.sqlclient.Row;
import org.hibernate.rx.RxSession;

import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.Tuple;

/**
 * Abstracts over reactive connection pools.
 *
 * @see org.hibernate.rx.impl.PoolConnection
 */
// FIXME: We might need to replace RowSet and Tuple classes
public interface RxConnection {
	CompletionStage<Void> inTransaction(
			Consumer<RxSession> consumer,
			RxSession delegate);

	CompletionStage<Integer> update(String sql);

	CompletionStage<Integer> update(String sql, Tuple parameters);

	CompletionStage<RowSet<Row>> preparedQuery(String query);

	CompletionStage<Optional<Integer>> updateReturning(String sql, Tuple parameters);

	CompletionStage<RowSet<Row>> preparedQuery(String sql, Tuple parameters);

	void close();

}

